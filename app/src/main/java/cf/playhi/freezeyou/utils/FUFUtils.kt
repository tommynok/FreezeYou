package cf.playhi.freezeyou.utils

import android.annotation.TargetApi
import android.app.Activity
import android.content.ComponentName
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.VisibleForTesting
import cf.playhi.freezeyou.DeviceAdminReceiver
import cf.playhi.freezeyou.MainApplication
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.fuf.FUFSinglePackage
import cf.playhi.freezeyou.fuf.FreezeYouFUFSinglePackage
import cf.playhi.freezeyou.service.FUFService
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.lesserToast
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys
import cf.playhi.freezeyou.ui.AskRunActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import rikka.sui.Sui
import rikka.shizuku.ShizukuProvider
import java.util.*

/**
 * Central coordinator and dispatcher for application freeze and unfreeze (FUF) operations.
 *
 * <p><b>Dispatch Architecture &amp; Execution Pathways:</b>
 * <ul>
 *   <li><b>In-Process Direct Execution ([processSingleActionInProcess], [processBatchAction]):</b>
 *       Used when operations are initiated from active UI components (e.g. package list item clicks,
 *       shortcut activities). Executing directly in the application process avoids the costly overhead
 *       of spinning up the {@code :backgroundService} process, re-fetching Shizuku Binder tokens, and
 *       issuing foreground notifications. Reduces single-app freeze latency from ~800ms to &lt;50ms.</li>
 *   <li><b>Out-of-Process Background Service ([FUFService]):</b>
 *       Dispatched via [startBatchService] when operations are invoked headlessly (widgets, automated
 *       tasks, lock screen triggers, or legacy Root batch modes) where no foreground activity is alive
 *       to manage the coroutine lifecycle.</li>
 *   <li><b>Root Batch Optimization ([oneKeyActionRoot], [oneKeyActionRootAsync]):</b>
 *       Pipes all batch unfreeze/freeze instructions into as few {@code su} shell sessions as it
 *       takes ([PrivilegedShellUtils.executeBatchPrivilegedCommand]), avoiding per-package su process
 *       invocation while still reading back one exit code and one block of output per package.</li>
 * </ul>
 *
 * <p><b>Safety Protocols &amp; State Guards:</b><br>
 * Evaluates three primary safety boundaries before modifying package state:
 * <ol>
 *   <li>Self-preservation: Disallows freezing FreezeYou itself regardless of package name/applicationId.</li>
 *   <li>Foreground Protection: Prevents freezing currently active foreground tasks when enabled by user.</li>
 *   <li>Notification Protection: Checks [ProcessSharedState] to prevent suppressing apps with active status notifications.</li>
 * </ol>
 */
object FUFUtils {

    /**
     * Handles application launch decisions after unfreezing.
     *
     * <p>If {@code runImmediately} is active (or enabled in global settings), directly launches
     * the target component or package entry point via [checkAndStartApp]. Otherwise, presents the
     * interactive [AskRunActivity] dialog allowing the user to confirm launch or cancel.
     */
    @JvmStatic
    fun askRun(
        context: Context,
        pkgName: String,
        target: String?,
        tasks: String?,
        runImmediately: Boolean,
        activity: Activity?,
        finish: Boolean
    ) {
        if (runImmediately || DefaultMultiProcessMMKVStorageBooleanKeys.openImmediately.getValue()) {
            checkAndStartApp(context, pkgName, target, tasks, activity, finish)
        } else {
            if (!isOnlyUnfreezeTarget(target)) {
                context.startActivity(
                    Intent(context, AskRunActivity::class.java)
                        .putExtra("pkgName", pkgName)
                        .putExtra("target", target)
                        .putExtra("tasks", tasks)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /**
     * What a shortcut stores when the chosen action is "unfreeze, and do not launch anything".
     *
     * Upstream identified that choice by its own translated label, so the value written into a
     * shortcut depended on the device language at the moment it was created: switch the phone to
     * another language, and the shortcut stopped being recognised and was taken for the name of
     * an activity to start. The '@' keeps this apart from every real class name, which cannot
     * contain one, and it never changes with the language or the wording.
     */
    const val ONLY_UNFREEZE_TARGET = "@onlyUnfreeze"

    @JvmStatic
    fun isOnlyUnfreezeTarget(target: String?): Boolean {
        return target == ONLY_UNFREEZE_TARGET
    }

    /**
     * Turns what the target field shows into what gets stored. The field is human-readable and
     * editable, so the two standing choices appear there in the user's language; they are
     * translated to their stored form here, at the one boundary where a target is saved or
     * simulated. Anything else is an activity class name and is kept as typed.
     */
    @JvmStatic
    fun normalizeSelectedTarget(context: Context, target: String?): String? {
        return normalizeSelectedTarget(
            target,
            context.getString(R.string.launch),
            context.getString(R.string.onlyUnfreeze)
        )
    }

    /**
     * The decision itself, with the two labels passed in rather than looked up. Context.getString
     * is final, so a test cannot stand in for it; taking the strings as arguments keeps this
     * reachable without one.
     */
    @JvmStatic
    fun normalizeSelectedTarget(
        target: String?, launchLabel: String, onlyUnfreezeLabel: String
    ): String? {
        return when (target) {
            null -> null
            // "Launch" means the application's own entry point, which is expressed as no target.
            launchLabel -> null
            onlyUnfreezeLabel -> ONLY_UNFREEZE_TARGET
            else -> target
        }
    }

    @JvmStatic
    fun checkAndDoActivityFinish(activity: Activity?, finish: Boolean) {
        if (activity != null && finish) {
            activity.finish()
        }
    }

    /**
     * Executes a freeze or unfreeze operation for a single package and performs all necessary follow-up bookkeeping.
     *
     * <p>On success:
     * <ul>
     *   <li>Broadcasts {@code cf.playhi.freezeyou.action.packageStatusChanged} targeting the local package.</li>
     *   <li>If unfreezing: triggers {@code onUFApplications} automation tasks, creates quick notification,
     *       and optionally delegates to [askRun].</li>
     *   <li>If freezing: triggers {@code onFApplications} tasks and deletes any persistent quick notifications.</li>
     * </ul>
     *
     * @return true if the operation succeeded and status changed, false otherwise
     */
    @JvmStatic
    @JvmOverloads
    fun processAction(
        context: Context, pkgName: String, apiMode: Int,
        enable: Boolean, showUnnecessaryToast: Boolean,
        askRun: Boolean = false, target: String? = null, tasks: String? = null,
        runImmediately: Boolean = false, activity: Activity? = null, finish: Boolean = false,
        skipQuickNotification: Boolean = false
    ): Boolean {
        val result = checkAndExecuteAction(
            context, pkgName,
            apiMode,
            if (enable) FUFSinglePackage.ACTION_MODE_UNFREEZE else FUFSinglePackage.ACTION_MODE_FREEZE
        )
        if (preProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
                context, result, showUnnecessaryToast
            )
        ) {
            sendStatusChangedBroadcast(context, pkgName)
            if (enable) {
                TasksUtils.onUFApplications(context, pkgName)
                if (!skipQuickNotification) {
                    checkAndCreateFUFQuickNotification(context, pkgName)
                }
                if (askRun) {
                    askRun(context, pkgName, target, tasks, runImmediately, activity, finish)
                }
            } else {
                TasksUtils.onFApplications(context, pkgName)
                if (!skipQuickNotification) {
                    NotificationUtils.deleteNotification(context, pkgName)
                }
            }
            return true
        }
        return false
    }

    @JvmStatic
    fun processRootAction(
        pkgName: String, target: String?, tasks: String?, context: Context,
        enable: Boolean, askRun: Boolean, runImmediately: Boolean, activity: Activity?,
        finish: Boolean, showUnnecessaryToast: Boolean
    ): Boolean {
        return processAction(
            context,
            pkgName,
            FUFSinglePackage.API_FREEZEYOU_ROOT_DISABLE_ENABLE,
            enable,
            showUnnecessaryToast,
            askRun,
            target,
            tasks,
            runImmediately,
            activity,
            finish
        )
    }

    @JvmStatic
    @TargetApi(21)
    fun processMRootAction(
        context: Context, pkgName: String, target: String?, tasks: String?, hidden: Boolean,
        askRun: Boolean, runImmediately: Boolean, activity: Activity?,
        finish: Boolean, showUnnecessaryToast: Boolean
    ): Boolean {
        return processAction(
            context,
            pkgName,
            FUFSinglePackage.API_FREEZEYOU_MROOT_DPM,
            !hidden,
            showUnnecessaryToast,
            askRun,
            target,
            tasks,
            runImmediately,
            activity,
            finish
        )
    }

    /**
     * Synchronously validates safety prerequisites and dispatches the freeze/unfreeze action.
     *
     * <p>Checks:
     * <ol>
     *   <li>Ensures {@code pkgName} does not match FreezeYou's runtime package name.</li>
     *   <li>If freezing, checks if the target package has active notifications and avoids freezing if configured.</li>
     *   <li>If freezing, checks if the target package is currently in the foreground and avoids freezing if configured.</li>
     *   <li>Commits execution synchronously using {@link FUFSinglePackage#commit} via {@code runBlocking}.</li>
     * </ol>
     */
    @JvmStatic
    fun checkAndExecuteAction(
        context: Context,
        pkgName: String,
        apiMode: Int,
        actionMode: Int
    ): Int {
        var returnValue = 999
        var currentPackage: String? = " "
        if (DefaultMultiProcessMMKVStorageBooleanKeys.avoidFreezeForegroundApplications.getValue()) {
            currentPackage = MainApplication.currentPackage
        }
        if (currentPackage == null) currentPackage = " "
        // Compare against the id this build actually runs as: a fork installed under a different
        // applicationId would otherwise not recognise itself, and could freeze itself.
        if (context.packageName != pkgName) {
            if (actionMode == FUFSinglePackage.ACTION_MODE_FREEZE &&
                isAvoidFreezeNotifyingApplicationsEnabledAndAppStillNotifying(pkgName)
            ) {
                checkAndShowAppStillNotifyingToast(context, pkgName)
            } else if (actionMode == FUFSinglePackage.ACTION_MODE_FREEZE && currentPackage == pkgName) {
                checkAndShowAppIsForegroundApplicationToast(context, pkgName)
            } else {
                runBlocking {
                    returnValue = FUFSinglePackage(context, pkgName, actionMode, apiMode).commit()
                }
            }
        }
        return returnValue
    }

    /**
     * Launches an unfrozen package or specific target activity.
     *
     * <p>If a specific activity target is provided, launches it with {@code FLAG_ACTIVITY_NEW_TASK}.
     * If launching fails with {@link SecurityException} (e.g. unexported activity) or
     * {@link ActivityNotFoundException}, delegates to [ElevatedLaunchUtils.startActivityElevatedAsync]
     * to launch with Root/Shizuku shell privileges.
     */
    @JvmStatic
    fun checkAndStartApp(
        context: Context,
        pkgName: String,
        target: String?,
        tasks: String?,
        activity: Activity?,
        finish: Boolean
    ) {
        if (target != null) {
            if (!isOnlyUnfreezeTarget(target)) {
                try {
                    val component = ComponentName(pkgName, target)
                    val intent = Intent()
                    intent.component = component
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    intent.action = Intent.ACTION_MAIN
                    context.startActivity(intent)
                } catch (e: SecurityException) {
                    e.printStackTrace()
                    // Refused because the target is not exported. Root, or the shell UID Shizuku
                    // runs as, may still be allowed to start it.
                    ElevatedLaunchUtils.startActivityElevatedAsync(context, pkgName, target)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                    // The target vanished (an application update removed or renamed the
                    // activity): crashing the process over a stale shortcut target is
                    // worse than reporting it. The elevated path can still start some.
                    ElevatedLaunchUtils.startActivityElevatedAsync(context, pkgName, target)
                }
            }
        } else if (context.packageManager.getLaunchIntentForPackage(pkgName) != null) {
            val intent = Intent(
                context.packageManager.getLaunchIntentForPackage(pkgName)
            )
            context.startActivity(intent)
        } else {
            ToastUtils.showToast(
                context,
                R.string.unrootedOrCannotFindTheLaunchIntent
            )
        }
        if (tasks != null) {
            TasksUtils.runTask(tasks, context, null)
        }
        checkAndDoActivityFinish(activity, finish)
    }

    /**
     * What a batch of privileged commands did: the packages left in the state that was asked for,
     * and the packages that were not.
     *
     * The packages held back by the user's own guards — an application still notifying, the one in
     * the foreground — are in neither list. Nothing was asked of the shell for them, and the reason
     * was told to the user one application at a time.
     */
    class RootBatchSummary(val succeeded: List<String>, val failed: List<String>)

    /**
     * A root batch, from the decision to the answer, kept as one value because in the asynchronous
     * path the deciding, the shell and the reporting happen on three different threads.
     */
    private class RootBatch(
        /** Packages the shell was sent a command for, parallel to [commands]. */
        val batched: List<String>,
        val commands: List<String>,
        /** Packages already in the state asked for: nothing to run, so nothing that can fail. */
        val alreadyInDesiredState: List<String>,
        /** Packages whose enabled setting the package manager would not answer for. */
        val unreadable: List<String>,
        val stillNotifying: List<String>,
        val foreground: List<String>,
        /** null when the shell had nothing to do. */
        val shell: PrivilegedShellUtils.BatchResult?
    )

    /**
     * Executes a batch freeze or unfreeze through as few privileged shell sessions as it can.
     *
     * <p><b>Performance Optimization:</b><br>
     * An individual {@code su} invocation costs a process and a privilege escalation — several
     * hundred milliseconds — while {@code pm disable} itself is a fraction of that. The whole batch
     * is fed to one shell by [PrivilegedShellUtils.executeBatchPrivilegedCommand], which still
     * reports every command on its own, so a batch that was refused for one application is known as
     * that instead of being reported as a success. Bookkeeping (freeze counters, notification
     * removal, automation triggers) is then done once, for the applications that changed.
     *
     * <p><b>Threads:</b> blocks for the shell, so call it from a thread that may block, and that has
     * a Looper — the toast and the triggered tasks at the end want one. [oneKeyActionRootAsync] is
     * the same operation for a caller on the UI thread.
     *
     * @param context execution context
     * @param freeze true to freeze, false to unfreeze
     * @param pkgNameList array of target package names
     * @param disableModeTrueOrHideModeFalse true for Mode 2 (pm disable/enable), false for Mode 3 (pm hide/unhide)
     */
    @JvmStatic
    @JvmOverloads
    fun oneKeyActionRoot(
        context: Context, freeze: Boolean, pkgNameList: Array<String>?,
        disableModeTrueOrHideModeFalse: Boolean = true
    ): RootBatchSummary {
        val appContext = context.applicationContext
        return reportRootBatch(
            appContext,
            runRootBatch(appContext, pkgNameList, freeze, disableModeTrueOrHideModeFalse),
            freeze
        )
    }

    /**
     * [oneKeyActionRoot] off the calling thread.
     *
     * A batch of two dozen root commands takes a second or two, and until now that wait happened on
     * the activity's own thread whenever the batch was started from a legacy entry point — the app
     * looked frozen to the user while the shell was still working through the list.
     *
     * Database updates, notifications, and task collection remain on the background thread to keep
     * the UI fluid; only toasts, task execution, and [onComplete] are dispatched on the main thread.
     */
    @JvmStatic
    @JvmOverloads
    fun oneKeyActionRootAsync(
        context: Context, freeze: Boolean, pkgNameList: Array<String>?,
        disableModeTrueOrHideModeFalse: Boolean = true,
        onComplete: Runnable? = null
    ) {
        // Same reason as in oneKeyAction: an empty list must not end in the "executed" toast.
        if (pkgNameList.isNullOrEmpty()) {
            onComplete?.run()
            return
        }
        val appContext = context.applicationContext
        inProcessFUFScope.launch {
            try {
                val batch = runRootBatch(
                    appContext, pkgNameList, freeze, disableModeTrueOrHideModeFalse
                )
                val outcomes = resolveRootBatchOutcomes(batch)
                val shell = batch.shell
                var bookkeepingException: Exception? = null
                val triggeredTasks = try {
                    if (shell == null || shell.sessionStarted) {
                        executeBackgroundBookkeeping(appContext, outcomes.actuallyChanged, freeze)
                    } else {
                        ArrayList()
                    }
                } catch (e: Exception) {
                    bookkeepingException = e
                    ArrayList()
                } finally {
                    sendStatusChangedBroadcast(appContext)
                }
                withContext(Dispatchers.Main) {
                    showRootBatchUI(appContext, batch, outcomes.succeeded, outcomes.failed, triggeredTasks)
                    if (bookkeepingException != null) {
                        bookkeepingException.printStackTrace()
                        ToastUtils.showToast(
                            appContext,
                            appContext.getString(R.string.exception) + bookkeepingException.message
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    ToastUtils.showToast(appContext, appContext.getString(R.string.exception) + e.message)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    onComplete?.run()
                }
            }
        }
    }

    /**
     * Java convenience overload to provide a completion callback with default disable/enable mode.
     */
    @JvmStatic
    fun oneKeyActionRootAsync(
        context: Context, freeze: Boolean, pkgNameList: Array<String>?,
        onComplete: Runnable?
    ) {
        oneKeyActionRootAsync(context, freeze, pkgNameList, true, onComplete)
    }

    /**
     * Decides what the shell has to do for this batch and asks for it.
     *
     * The decisions are the ones the loop used to make in the middle of the command stream: the
     * user's guards, and whether the application is already in the state asked for. The second is
     * worth making before anything is sent, because {@code pm} answers for an already disabled
     * application in whichever way that release happens to. It is deliberately not made on the
     * unfreeze half of Mode 3: hiding an application leaves its enabled setting alone, so the check
     * would find a hidden application enabled and skip exactly the unfreeze it was asked to do.
     */
    private fun runRootBatch(
        context: Context, pkgNameList: Array<String>?, freeze: Boolean,
        disableModeTrueOrHideModeFalse: Boolean
    ): RootBatch {
        val batched = ArrayList<String>()
        val commands = ArrayList<String>()
        val alreadyInDesiredState = ArrayList<String>()
        val unreadable = ArrayList<String>()
        val stillNotifying = ArrayList<String>()
        val foreground = ArrayList<String>()
        // The list arrives from Java callers that may hand over null, and an empty one has nothing to
        // walk: taking it as one non-null local keeps the decisions below free of both cases.
        // batchTargetsWithoutSelf drops this build's own package and repeated entries, so the
        // shell is asked about each package at most once.
        val targets =
            if (pkgNameList == null) emptyList() else batchTargetsWithoutSelf(context.packageName, pkgNameList)
        var currentPackage: String? = " "
        if (DefaultMultiProcessMMKVStorageBooleanKeys.avoidFreezeForegroundApplications.getValue()) {
            currentPackage = MainApplication.currentPackage
        }
        if (currentPackage == null) currentPackage = " "
        val verb = when {
            freeze && disableModeTrueOrHideModeFalse -> "disable"
            freeze -> "hide"
            disableModeTrueOrHideModeFalse -> "enable"
            else -> "unhide"
        }
        for (pkgName in targets) {
            if (freeze) {
                if (isAvoidFreezeNotifyingApplicationsEnabledAndAppStillNotifying(pkgName)) {
                    stillNotifying += pkgName
                    continue
                }
                if (currentPackage == pkgName) {
                    foreground += pkgName
                    continue
                }
            }
            if (freeze || disableModeTrueOrHideModeFalse) {
                val state = enabledSettingOrNull(context, pkgName)
                if (state == null) {
                    unreadable += pkgName
                    continue
                }
                // Freeze wants the frozen states, unfreeze wants to leave them: the same question
                // either way, asked once.
                if (freeze == isFrozenEnabledSetting(state)) {
                    alreadyInDesiredState += pkgName
                    continue
                }
            }
            batched += pkgName
            // Quoted because a package name is editable text in a list and in a backup file, and an
            // unquoted one of them is a command of whoever wrote it.
            commands += "pm $verb ${PrivilegedShellUtils.shellQuote(pkgName)}"
        }
        val shell = if (commands.isEmpty()) null else PrivilegedShellUtils
            .executeBatchPrivilegedCommand(
                commands, context, PrivilegedShellUtils.Backend.ROOT
            )
        return RootBatch(
            batched, commands, alreadyInDesiredState, unreadable, stillNotifying, foreground, shell
        )
    }

    private class RootBatchOutcomes(
        val succeeded: List<String>,
        val failed: List<String>,
        val actuallyChanged: List<String>
    )

    private fun resolveRootBatchOutcomes(batch: RootBatch): RootBatchOutcomes {
        val shell = batch.shell
        val succeeded = ArrayList(batch.alreadyInDesiredState)
        val failed = ArrayList<String>(batch.unreadable)
        val actuallyChanged = ArrayList<String>()

        if (shell != null && !shell.sessionStarted) {
            failed += batch.batched
        } else if (shell != null) {
            batch.batched.forEachIndexed { index, pkgName ->
                if (shell.outcomes[index].isSuccessful()) {
                    succeeded += pkgName
                    actuallyChanged += pkgName
                } else {
                    failed += pkgName
                }
            }
        }
        return RootBatchOutcomes(succeeded, failed, actuallyChanged)
    }

    /**
     * Performs database persistence, notification cancellation, and trigger collection on the background
     * dispatcher to avoid stalling UI threads during large batches.
     */
    private fun executeBackgroundBookkeeping(
        context: Context,
        actuallyChanged: List<String>,
        freeze: Boolean
    ): ArrayList<String> {
        if (actuallyChanged.isEmpty()) return ArrayList()
        if (freeze) {
            DataStatisticsUtils.addFreezeTimes(context, actuallyChanged)
            NotificationUtils.deleteNotifications(context, actuallyChanged)
        } else {
            DataStatisticsUtils.addUFreezeTimes(context, actuallyChanged)
        }
        val triggered = TasksUtils.collectTriggeredTasks(
            context, actuallyChanged, if (freeze) "onFApplications" else "onUFApplications"
        )
        if (!freeze && DefaultMultiProcessMMKVStorageBooleanKeys.createQuickFUFNotiAfterUnfrozen.getValue()) {
            // One notification per application, the way the system stacks them into a bundle of its
            // own accord. The single card for the lot is opt-in, and worth having only when there is
            // more than one application to put on it.
            if (DefaultMultiProcessMMKVStorageBooleanKeys.combineBatchQuickNotification.getValue()
                && actuallyChanged.size > 1
            ) {
                NotificationUtils.createBatchQuickNotification(context, actuallyChanged)
            } else {
                actuallyChanged.forEach { checkAndCreateFUFQuickNotification(context, it) }
            }
        }
        return triggered ?: ArrayList()
    }

    /**
     * Dispatches user-facing toasts and task execution on the main Looper thread.
     */
    private fun showRootBatchUI(
        context: Context,
        batch: RootBatch,
        succeeded: List<String>,
        failed: List<String>,
        triggeredTasks: ArrayList<String>
    ) {
        batch.stillNotifying.forEach { checkAndShowAppStillNotifyingToast(context, it) }
        batch.foreground.forEach { checkAndShowAppIsForegroundApplicationToast(context, it) }

        val shell = batch.shell
        if (shell != null && !shell.sessionStarted) {
            ToastUtils.showToast(context, R.string.mayUnrootedOrOtherEx)
            val message = shell.error?.lowercase(Locale.getDefault()) ?: ""
            if (message.contains("permission denied") || message.contains("not found")) {
                ToastUtils.showToast(context, R.string.mayUnrooted)
            }
            return
        }

        if (triggeredTasks.isNotEmpty()) {
            TasksUtils.runCollectedTasks(context, triggeredTasks)
        }
        if (failed.isNotEmpty()) {
            showFailedApplicationsToast(context, failed)
        } else if (!lesserToast.getValue()) {
            ToastUtils.showToast(context, R.string.executed)
        }
    }

    /**
     * Tells the user, the list and the scheduled tasks what the batch did.
     *
     * The bookkeeping follows the applications that reached the state asked for rather than the list
     * that was handed in: a freeze counted for an application the shell refused, or that a guard of
     * the user's own held back, reported a freeze nobody had done.
     */
    private fun reportRootBatch(
        context: Context, batch: RootBatch, freeze: Boolean
    ): RootBatchSummary {
        val outcomes = resolveRootBatchOutcomes(batch)
        val shell = batch.shell
        var bookkeepingException: Exception? = null
        val triggeredTasks = try {
            if (shell == null || shell.sessionStarted) {
                executeBackgroundBookkeeping(context, outcomes.actuallyChanged, freeze)
            } else {
                ArrayList()
            }
        } catch (e: Exception) {
            bookkeepingException = e
            ArrayList()
        } finally {
            sendStatusChangedBroadcast(context)
        }
        showRootBatchUI(context, batch, outcomes.succeeded, outcomes.failed, triggeredTasks)
        if (bookkeepingException != null) {
            bookkeepingException.printStackTrace()
            ToastUtils.showToast(
                context,
                context.getString(R.string.exception) + bookkeepingException.message
            )
        }
        return RootBatchSummary(outcomes.succeeded, outcomes.failed)
    }

    /**
     * @return the enabled setting, or null when the package manager will not answer for the package
     * at all — uninstalled, or in a state the caller is not allowed to read.
     */
    private fun enabledSettingOrNull(context: Context, pkgName: String): Int? =
        try {
            context.packageManager.getApplicationEnabledSetting(pkgName)
        } catch (e: Exception) {
            null
        }

    private fun isFrozenEnabledSetting(state: Int): Boolean =
        state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED

    @JvmStatic
    @TargetApi(21)
    @Deprecated(
        "DEPRECATED", ReplaceWith(
            "oneKeyAction(context, freeze, pkgNameList, FUFSinglePackage.API_FREEZEYOU_MROOT_DPM)",
            "cf.playhi.freezeyou.utils.FUFUtils.oneKeyAction",
            "cf.playhi.freezeyou.fuf.FUFSinglePackage"
        )
    )
    fun oneKeyActionMRoot(context: Context, freeze: Boolean, pkgNameList: Array<String>?) {
        oneKeyAction(context, freeze, pkgNameList, FUFSinglePackage.API_FREEZEYOU_MROOT_DPM)
    }

    /**
     * Dispatches a batch freeze/unfreeze action according to [apiMode].
     *
     * <p>Routes Root modes (2, 3) to [oneKeyActionRoot], which feeds the whole batch to one su
     * session and reports each package back separately. Routes all other modes (DPM, Shizuku, System
     * App) through iterative execution while collecting failures into a capped summary toast.
     *
     * <p>Blocks for the shell: callers that own a screen want [oneKeyActionRootAsync] instead, and
     * the list already goes that way through [processBatchAction].
     */
    @JvmStatic
    fun oneKeyAction(context: Context, freeze: Boolean, pkgNameList: Array<String>?, apiMode: Int) {
        // Nothing to act on: without this the empty list walks through the loop below, finds no
        // failures and reports success - the "executed" toast for a freeze that never happened.
        // An empty array reaches here from the batch service and from a list that is empty or
        // holds only separators.
        if (pkgNameList.isNullOrEmpty()) return
        when (apiMode) {
            FUFSinglePackage.API_FREEZEYOU_ROOT_DISABLE_ENABLE -> oneKeyActionRoot(
                context,
                freeze,
                pkgNameList,
                true
            )
            FUFSinglePackage.API_FREEZEYOU_ROOT_UNHIDE_HIDE -> oneKeyActionRoot(
                context,
                freeze,
                pkgNameList,
                false
            )
            else -> if (pkgNameList != null) {
                // FreezeYou never acts on itself: a self entry (a restored backup, a hand-edited
                // OneKEY list) used to slip through the precedence of the old condition, collect
                // the -11 rejection in processAction and land in the failure toast. runRootBatch
                // has always skipped self silently; this is the same rule for the other modes.
                val targets = batchTargetsWithoutSelf(context.packageName, pkgNameList)
                // A list of nothing but self is an empty batch: it must not report success.
                if (targets.isEmpty()) return
                val failedPkgNames = mutableListOf<String>()
                val succeededPkgNames = mutableListOf<String>()
                val isBatch = pkgNameList.size > 1
                val combineBatchNoti = !freeze && DefaultMultiProcessMMKVStorageBooleanKeys.combineBatchQuickNotification.getValue()
                val skipNoti = isBatch || combineBatchNoti
                for (aPkgName in targets) {
                    try {
                        // An app the DPM already froze is skipped instead of committed again.
                        @Suppress("DEPRECATION")
                        if ((apiMode == FUFSinglePackage.API_FREEZEYOU_LEGACY_AUTO ||
                                    apiMode == FUFSinglePackage.API_FREEZEYOU_MROOT_DPM) &&
                            freeze && checkMRootFrozen(context, aPkgName)
                        ) {
                            continue
                        }
                        if (!processAction(
                                context, aPkgName, apiMode, !freeze, false,
                                skipQuickNotification = skipNoti
                            )
                        ) {
                            failedPkgNames.add(aPkgName)
                        } else {
                            succeededPkgNames.add(aPkgName)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        failedPkgNames.add(aPkgName)
                    }
                }
                sendStatusChangedBroadcast(context)
                if (failedPkgNames.isNotEmpty()) {
                    showFailedApplicationsToast(context, failedPkgNames)
                } else if (!lesserToast.getValue()) {
                    ToastUtils.showToast(context, R.string.executed)
                }
                if (freeze) {
                    if (isBatch && succeededPkgNames.isNotEmpty()) {
                        NotificationUtils.deleteNotifications(context, succeededPkgNames)
                    }
                } else if (DefaultMultiProcessMMKVStorageBooleanKeys.createQuickFUFNotiAfterUnfrozen.getValue()) {
                    if (combineBatchNoti && succeededPkgNames.size > 1) {
                        NotificationUtils.createBatchQuickNotification(context, succeededPkgNames)
                    } else if (combineBatchNoti && succeededPkgNames.size == 1) {
                        checkAndCreateFUFQuickNotification(context, succeededPkgNames[0])
                    } else if (isBatch) {
                        succeededPkgNames.forEach { checkAndCreateFUFQuickNotification(context, it) }
                    }
                }
            }
        }
    }

    /**
     * Android's system Toast has no built-in scrolling/scaling for long content — extra
     * toasts just queue up (or get silently dropped for background apps on Android 11+),
     * so a batch failure is summarized into a single, length-capped toast instead of one
     * toast per failed package.
     */
    private const val MAX_FAILED_APPS_SHOWN_IN_TOAST = 5

    private fun showFailedApplicationsToast(context: Context, failedPkgNames: List<String>) {
        val shownNames = failedPkgNames.take(MAX_FAILED_APPS_SHOWN_IN_TOAST).joinToString(", ") {
            ApplicationLabelUtils.getApplicationLabel(context, null, null, it) ?: it
        }
        val remaining = failedPkgNames.size - MAX_FAILED_APPS_SHOWN_IN_TOAST
        val message = context.getString(
            R.string.nApplicationsFailedColonList, failedPkgNames.size, shownNames
        ) + if (remaining > 0) context.getString(R.string.andNMoreApplications, remaining) else ""
        ToastUtils.showToast(context, message)
    }

    /**
     * @param pkgName the one package whose state changed, so the list can refresh that row
     * alone; null means "unknown / several", and the list re-checks everything.
     */
    @JvmStatic
    @JvmOverloads
    fun sendStatusChangedBroadcast(context: Context, pkgName: String? = null) {
        val intent = Intent()
        intent.action = "cf.playhi.freezeyou.action.packageStatusChanged"
        intent.setPackage(context.packageName)
        if (pkgName != null) intent.putExtra("pkgName", pkgName)
        context.sendBroadcast(intent)
    }

    /**
     * Packages with an FUF operation already running. Two taps on the same row, a shortcut fired
     * twice, or a shortcut and a list row racing each other used to start two operations for one
     * application: both read the old state, both committed, the statistics and the trigger tasks
     * counted two changes, and a shortcut could flip the state back and forth. The second request
     * is now dropped instead of queued — it carries no different intent, and running it after the
     * first would only repeat the work.
     *
     * A monitor rather than a java.util.concurrent collection: it guards a few set operations and
     * has to work on every supported API level.
     */
    private val inFlightLock = Any()
    private val inFlightPackages = HashSet<String>()

    /** @return true when this caller acquired the package and must release it afterwards. */
    @JvmStatic
    fun tryAcquirePackage(pkgName: String): Boolean = synchronized(inFlightLock) {
        inFlightPackages.add(pkgName)
    }

    @JvmStatic
    fun releasePackage(pkgName: String) = synchronized(inFlightLock) {
        inFlightPackages.remove(pkgName)
    }

    @JvmStatic
    fun isPackageInFlight(pkgName: String): Boolean = synchronized(inFlightLock) {
        inFlightPackages.contains(pkgName)
    }

    @VisibleForTesting
    @JvmStatic
    fun clearInFlightPackagesForTests() = synchronized(inFlightLock) {
        inFlightPackages.clear()
    }

    /**
     * Single freeze/unfreeze without the FUFService detour. The service lives in its own
     * `:backgroundService` process, so a tap from the list used to mean: fork a process, put up a
     * foreground notification, fetch the Shizuku binder into that process over IPC, and only then
     * make the one binder call the whole thing is about. In the main process the binder is
     * already here and the proxy stays warm between taps — the call is milliseconds, as it is
     * meant to be. This is the same path the Freeze activity already uses.
     *
     * Batch operations, shortcuts, widgets and scheduled tasks keep the service: there is no
     * foreground activity to hold the work there.
     */
    @JvmStatic
    fun processSingleActionInProcess(
        context: Context,
        pkgName: String,
        target: String?,
        tasks: String?,
        freeze: Boolean,
        askRun: Boolean,
        runImmediately: Boolean
    ) {
        // Application context: the activity may be gone by the time the toast is shown.
        val appContext = context.applicationContext
        if (!tryAcquirePackage(pkgName)) return
        inProcessFUFScope.launch {
            try {
                val startedAt = System.currentTimeMillis()
                val fufSinglePackage = FreezeYouFUFSinglePackage(
                    appContext,
                    pkgName,
                    if (freeze) FUFSinglePackage.ACTION_MODE_FREEZE else FUFSinglePackage.ACTION_MODE_UNFREEZE,
                    needAskRun = askRun,
                    runImmediately = runImmediately,
                    tasks = tasks,
                    target = target
                )
                val result = fufSinglePackage.commit()
                if (DebugModeUtils.isDebugModeEnabled()) {
                    Log.e(
                        "DebugModeLogcat",
                        "fuf $pkgName freeze=$freeze result=$result " +
                                "took=${System.currentTimeMillis() - startedAt}ms"
                    )
                }
                withContext(Dispatchers.Main) {
                    // Starting the app / the ask-run dialog after an unfreeze belongs on the UI thread.
                    val finalResult = if (freeze) result
                    else fufSinglePackage.checkAndStartTaskAndTargetAndActivity(result)
                    ToastUtils.showToast(appContext, getFUFRelatedToastString(appContext, finalResult))
                }
            } finally {
                releasePackage(pkgName)
            }
        }
    }

    /**
     * Freezing a selection of applications, without the FUFService detour.
     *
     * The service version paid, per application, for: a `runBlocking` round trip, two SQLite
     * databases opened and a table scanned, and a status broadcast that woke the list — 20
     * applications meant 40 database opens and 21 broadcasts, all on the service process's main
     * thread. The binder calls the operation actually consists of are a rounding error next to
     * that, which is why a batch felt far slower here than in apps doing the same thing.
     *
     * Now the applications are handled one after another on a background thread, the bookkeeping
     * is done once for the whole batch, and the list is told once at the end.
     *
     * The root modes come here too, through [oneKeyActionRootAsync]: one su session for the batch
     * instead of one process per application, driven on a background thread. The deprecated
     * automatic mode is dispatched through FUFService, which partitions packages between DPM/PO
     * and batched root execution.
     *
     * Applications the user's own guards refuse — still notifying, or in the foreground — are
     * reported one by one with the specific toast the single-package path uses instead of joining
     * the failure list, and the batch only says it executed when nothing was refused.
     */
    @JvmStatic
    fun processBatchAction(context: Context, packages: Array<String>, freeze: Boolean) {
        // An empty batch has nothing to do and must not report success; see oneKeyAction.
        if (packages.isEmpty()) return
        val apiMode = try {
            DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode.getValue(context)!!.toInt()
        } catch (e: Exception) {
            e.printStackTrace()
            startBatchService(context, packages, freeze)
            return
        }

        @Suppress("DEPRECATION")
        if (apiMode == FUFSinglePackage.API_FREEZEYOU_LEGACY_AUTO) {
            startBatchService(context, packages, freeze)
            return
        }
        if (apiMode == FUFSinglePackage.API_FREEZEYOU_ROOT_DISABLE_ENABLE ||
            apiMode == FUFSinglePackage.API_FREEZEYOU_ROOT_UNHIDE_HIDE
        ) {
            oneKeyActionRootAsync(
                context, freeze, packages,
                apiMode == FUFSinglePackage.API_FREEZEYOU_ROOT_DISABLE_ENABLE
            )
            return
        }

        val appContext = context.applicationContext
        // The same rule as oneKeyAction and runRootBatch: self never reaches the action, so it
        // can never reach the failed list and the toast that names it.
        val targets = batchTargetsWithoutSelf(appContext.packageName, packages)
        if (targets.isEmpty()) return
        // A package with an operation already running is left to that operation and the batch
        // takes the rest; when nothing is left, the request would only repeat work.
        val acquiredPackages = mutableListOf<String>()
        for (pkg in targets) {
            if (tryAcquirePackage(pkg)) {
                acquiredPackages.add(pkg)
            }
        }
        if (acquiredPackages.isEmpty()) {
            // The list has already painted the requested state onto these rows; since this
            // request will not change anything, tell it to read the real state back.
            sendStatusChangedBroadcast(appContext)
            return
        }
        val actionMode =
            if (freeze) FUFSinglePackage.ACTION_MODE_FREEZE else FUFSinglePackage.ACTION_MODE_UNFREEZE

        inProcessFUFScope.launch {
            try {
                val startedAt = System.currentTimeMillis()
                val succeeded = mutableListOf<String>()
                val stillNotifying = mutableListOf<String>()
                val foreground = mutableListOf<String>()
                val failed = mutableListOf<String>()

                for (pkgName in acquiredPackages) {
                    categorizeBatchResult(
                        executeActionQuietly(appContext, pkgName, apiMode, actionMode),
                        pkgName, succeeded, stillNotifying, foreground, failed
                    )
                }

                // Split on purpose: the actions and the bookkeeping that follows them are different
                // costs, and reading one number for both hid that the bookkeeping was the slower half.
                val actionsDoneAt = System.currentTimeMillis()

                if (succeeded.isNotEmpty()) {
                    if (freeze) {
                        DataStatisticsUtils.addFreezeTimes(appContext, succeeded)
                        NotificationUtils.deleteNotifications(appContext, succeeded)
                    } else {
                        DataStatisticsUtils.addUFreezeTimes(appContext, succeeded)
                        // The quick notifications are deliberately not here — see after the toast.
                    }
                    sendStatusChangedBroadcast(appContext)
                    val triggered = TasksUtils.collectTriggeredTasks(
                        appContext, succeeded,
                        if (freeze) "onFApplications" else "onUFApplications"
                    )
                    if (triggered.isNotEmpty()) {
                        withContext(Dispatchers.Main) { TasksUtils.runCollectedTasks(appContext, triggered) }
                    }
                } else if (shouldRefreshListAfterBatch(failed, stillNotifying, foreground)) {
                    sendStatusChangedBroadcast(appContext)
                }

                val bookkeepingDoneAt = System.currentTimeMillis()

                withContext(Dispatchers.Main) {
                    stillNotifying.forEach { checkAndShowAppStillNotifyingToast(appContext, it) }
                    foreground.forEach { checkAndShowAppIsForegroundApplicationToast(appContext, it) }
                    if (failed.isNotEmpty()) {
                        showFailedApplicationsToast(appContext, failed)
                    } else if (stillNotifying.isEmpty() && foreground.isEmpty()
                        && !lesserToast.getValue()
                    ) {
                        ToastUtils.showToast(appContext, R.string.executed)
                    }
                }

                // Last, and after the list has been told to refresh and the toast has been shown,
                // because this is the slowest part of an unfreeze and nothing waits on its result.
                // Each application gets a notification of its own, with its own icon: that is what the
                // system stacks into a bundle, and the cost of it — a package manager lookup and an
                // icon decoded — is the price of the stock behaviour. The single card for the batch is
                // opt-in and cheaper, because it names the applications without decoding their icons.
                if (!freeze && DefaultMultiProcessMMKVStorageBooleanKeys.createQuickFUFNotiAfterUnfrozen.getValue()) {
                    if (DefaultMultiProcessMMKVStorageBooleanKeys.combineBatchQuickNotification.getValue()
                        && succeeded.size > 1
                    ) {
                        NotificationUtils.createBatchQuickNotification(appContext, succeeded)
                    } else {
                        succeeded.forEach { checkAndCreateFUFQuickNotification(appContext, it) }
                    }
                }

                if (DebugModeUtils.isDebugModeEnabled()) {
                    Log.e(
                        "DebugModeLogcat",
                        "fuf batch of ${packages.size} freeze=$freeze ok=${succeeded.size} " +
                                "failed=${failed.size} " +
                                "stillNotifying=${stillNotifying.size} " +
                                "foreground=${foreground.size} " +
                                "actions=${actionsDoneAt - startedAt}ms " +
                                "bookkeeping=${bookkeepingDoneAt - actionsDoneAt}ms " +
                                "reportedAfter=${bookkeepingDoneAt - startedAt}ms " +
                                "notifications=${System.currentTimeMillis() - bookkeepingDoneAt}ms"
                    )
                }
            } finally {
                acquiredPackages.forEach { releasePackage(it) }
            }
        }
    }

    private fun startBatchService(context: Context, packages: Array<String>, freeze: Boolean) {
        ServiceUtils.startService(
            context, Intent(context, FUFService::class.java)
                .putExtra("single", false)
                .putExtra("packages", packages)
                .putExtra("freeze", freeze)
        )
    }

    /**
     * The batch target list with this build's own package taken out and repeated entries collapsed
     * — one rule for every in-process batch path. Compared against the id this build runs as, never
     * a hardcoded name: a fork ships under its own applicationId and has to recognise itself.
     * Deduplication keeps the order of first appearance, so the batch still walks the list as the
     * user wrote it. Kept pure so a JVM test can hold the rule; the loops that use it sit behind
     * MMKV and toasts.
     */
    @JvmStatic
    fun batchTargetsWithoutSelf(
        selfPackageName: String, packages: Array<String>
    ): List<String> = packages.filterNot { it == selfPackageName }.distinct()

    /**
     * Puts one in-process batch result into the list it belongs to. The two guard codes are not
     * failures: the application was left alone on purpose, and reporting it as a failure hides the
     * reason the single-package path names. Kept out of the loop so a JVM test can hold the split
     * without an Android context.
     */
    @VisibleForTesting
    internal fun categorizeBatchResult(
        result: Int,
        pkgName: String,
        succeeded: MutableList<String>,
        stillNotifying: MutableList<String>,
        foreground: MutableList<String>,
        failed: MutableList<String>
    ) {
        when (result) {
            FUFSinglePackage.ERROR_NO_ERROR_SUCCESS,
            FUFSinglePackage.ERROR_NO_ERROR_CAUGHT_UNKNOWN_RESULT -> succeeded += pkgName
            FUFSinglePackage.ERROR_USER_SET_NOT_ALLOWED_TO_FREEZE_NOTIFYING_APPLICATION ->
                stillNotifying += pkgName
            FUFSinglePackage.ERROR_USER_SET_NOT_ALLOWED_TO_FREEZE_FOREGROUND_APPLICATION ->
                foreground += pkgName
            else -> failed += pkgName
        }
    }

    /**
     * Whether the list has to read the batch rows back. The success branch reports its own change;
     * a guard refusal changes nothing, yet the rows were painted with the state the request asked
     * for when it started, so it must ask the list to refresh as well. Kept pure so a JVM test can
     * hold the rule without an Android context.
     */
    @VisibleForTesting
    internal fun shouldRefreshListAfterBatch(
        failed: List<String>,
        stillNotifying: List<String>,
        foreground: List<String>
    ): Boolean = failed.isNotEmpty() || stillNotifying.isNotEmpty() || foreground.isNotEmpty()

    /**
     * The same guards [FreezeYouFUFSinglePackage] applies, without its per-application follow-up
     * work — in a batch that work is done once at the end instead.
     */
    private suspend fun executeActionQuietly(
        context: Context, pkgName: String, apiMode: Int, actionMode: Int
    ): Int {
        if (context.packageName == pkgName) {
            return FUFSinglePackage.ERROR_OPERATION_ON_FREEZEYOU_IS_NOT_ALLOWED
        }
        if (actionMode == FUFSinglePackage.ACTION_MODE_FREEZE) {
            if (isAvoidFreezeNotifyingApplicationsEnabledAndAppStillNotifying(pkgName)) {
                return FUFSinglePackage.ERROR_USER_SET_NOT_ALLOWED_TO_FREEZE_NOTIFYING_APPLICATION
            }
            if (DefaultMultiProcessMMKVStorageBooleanKeys.avoidFreezeForegroundApplications.getValue()
                && MainApplication.currentPackage == pkgName
            ) {
                return FUFSinglePackage.ERROR_USER_SET_NOT_ALLOWED_TO_FREEZE_FOREGROUND_APPLICATION
            }
        }
        return FUFSinglePackage(context, pkgName, actionMode, apiMode).commit()
    }

    private val inProcessFUFScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @TargetApi(21)
    private fun isAppStillNotifying(pkgName: String?): Boolean {
        // Read from the shared store rather than the listener service's own field: that service
        // lives in :backgroundService, and this check runs wherever the freeze was asked for.
        return ProcessSharedState.isNotifying(pkgName)
    }

    @JvmStatic
    fun isAvoidFreezeNotifyingApplicationsEnabledAndAppStillNotifying(pkgName: String?): Boolean {
        return if (Build.VERSION.SDK_INT >= 21) {
            DefaultMultiProcessMMKVStorageBooleanKeys.avoidFreezeNotifyingApplications.getValue()
                    && isAppStillNotifying(pkgName)
        } else {
            false
        }
    }

    @JvmStatic
    fun checkAndShowAppStillNotifyingToast(context: Context, pkgName: String?) {
        val label = ApplicationLabelUtils.getApplicationLabel(
            context,
            null,
            null,
            pkgName
        )
        if (context.getString(R.string.uninstalled) != label) ToastUtils.showToast(
            context, String.format(
                context.getString(R.string.appHasNotifi),
                label
            )
        )
    }

    @JvmStatic
    fun checkAndShowAppIsForegroundApplicationToast(context: Context, pkgName: String?) {
        val label = ApplicationLabelUtils.getApplicationLabel(
            context,
            null,
            null,
            pkgName
        )
        if (context.getString(R.string.uninstalled) != label) ToastUtils.showToast(
            context, String.format(
                context.getString(R.string.isForegroundApplication),
                label
            )
        )
    }

    @JvmStatic
    fun processUnfreezeAction(
        context: Context?,
        pkgName: String?,
        target: String?,
        tasks: String?,
        askRun: Boolean,
        runImmediately: Boolean,
        activity: Activity?,
        finish: Boolean
    ) {
        ServiceUtils.startService(
            context, Intent(context, FUFService::class.java)
                .putExtra("askRun", askRun)
                .putExtra("pkgName", pkgName)
                .putExtra("freeze", false)
                .putExtra("target", target) // Target Activity
                .putExtra("tasks", tasks) // Additional tasks
                .putExtra("single", true)
                .putExtra("runImmediately", runImmediately)
        )
        checkAndDoActivityFinish(activity, finish)
    }

    @JvmStatic
    fun processFreezeAction(
        context: Context?,
        pkgName: String?,
        target: String?,
        tasks: String?,
        askRun: Boolean,
        activity: Activity?,
        finish: Boolean
    ) {
        ServiceUtils.startService(
            context, Intent(context, FUFService::class.java)
                .putExtra("askRun", askRun)
                .putExtra("pkgName", pkgName)
                .putExtra("target", target) // Target Activity
                .putExtra("tasks", tasks) // Additional tasks
                .putExtra("freeze", true)
                .putExtra("single", true)
        )
        checkAndDoActivityFinish(activity, finish)
    }

    @JvmStatic
    fun checkMRootFrozen(context: Context?, pkgName: String): Boolean {
        return try {
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && (DevicePolicyManagerUtils.isDeviceOwner(
                context
            ) || DevicePolicyManagerUtils.isProfileOwner(context))
                    && DevicePolicyManagerUtils.getDevicePolicyManager(context)
                .isApplicationHidden(DeviceAdminReceiver.getComponentName(context), pkgName))
        } catch (e: Exception) {
            false
        }
    }

    @JvmStatic
    fun checkHiddenFrozen(context: Context, pkgName: String, pm: PackageManager?): Boolean {
        val packageManager = pm ?: context.packageManager ?: return false
        return try {
            packageManager.getApplicationInfo(pkgName, 0)
            false
        } catch (e: PackageManager.NameNotFoundException) {
            try {
                @Suppress("DEPRECATION")
                val appInfo = packageManager.getApplicationInfo(
                    pkgName,
                    PackageManager.GET_UNINSTALLED_PACKAGES
                )
                (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0
            } catch (ignored: Exception) {
                false
            }
        } catch (ignored: Exception) {
            false
        }
    }

    @JvmStatic
    fun checkRootFrozen(
        context: Context,
        pkgName: String,
        packageManager: PackageManager?
    ): Boolean {
        return try {
            packageManager?.getApplicationEnabledSetting(pkgName)
                ?: context.packageManager.getApplicationEnabledSetting(
                    pkgName
                )
        } catch (e: Exception) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }.let {
            it != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
                    it != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
    }

    /**
     * @param packageName Package name
     * @return true if frozen
     */
    @JvmStatic
    fun realGetFrozenStatus(context: Context, packageName: String): Boolean {
        return (checkMRootFrozen(context, packageName)
                || checkHiddenFrozen(context, packageName, context.packageManager)
                || checkRootFrozen(context, packageName, context.packageManager))
    }

    /**
     * @param packageName Package name
     * @return true if frozen
     */
    @JvmStatic
    fun realGetFrozenStatus(context: Context, packageName: String, pm: PackageManager?): Boolean {
        return (checkMRootFrozen(context, packageName)
                || checkHiddenFrozen(context, packageName, pm)
                || checkRootFrozen(context, packageName, pm))
    }

    @JvmStatic
    fun checkFrozenStatusAndStartApp(
        context: Context,
        pkgName: String,
        target: String?,
        tasks: String?
    ) {
        if (realGetFrozenStatus(context, pkgName, null)) {
            processUnfreezeAction(
                context, pkgName, target, tasks,
                askRun = true,
                runImmediately = true,
                activity = null,
                finish = false
            )
        } else {
            checkAndStartApp(context, pkgName, target, tasks, null, false)
        }
    }

    @JvmStatic
    fun checkRootPermission(): Boolean {
        return RootUtils.checkRootPermission()
    }

    @JvmStatic
    fun checkAndCreateFUFQuickNotification(context: Context?, pkgName: String?) {
        if (DefaultMultiProcessMMKVStorageBooleanKeys.createQuickFUFNotiAfterUnfrozen.getValue()) {
            NotificationUtils.createFUFQuickNotification(
                context, pkgName, R.drawable.ic_notification,
                ApplicationIconUtils.getBitmapFromDrawable(
                    ApplicationIconUtils.getApplicationIcon(
                        context!!,
                        pkgName!!,
                        ApplicationInfoUtils.getApplicationInfoFromPkgName(pkgName, context),
                        false
                    )
                )
            )
        }
    }

    fun isSystemApp(context: Context): Boolean {
        return context.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == ApplicationInfo.FLAG_SYSTEM
    }

    fun preProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
        context: Context, resultCode: Int, showUnnecessaryToast: Boolean
    ): Boolean {
        return when (resultCode) {
            FUFSinglePackage.ERROR_NO_ERROR_CAUGHT_UNKNOWN_RESULT -> {
                if (showUnnecessaryToast) showPreProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
                    context, context.getString(R.string.unknownResult_probablySuccess)
                )
                true
            }
            FUFSinglePackage.ERROR_NO_ERROR_SUCCESS -> {
                if (showUnnecessaryToast) showPreProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
                    context, context.getString(R.string.success)
                )
                true
            }
            FUFSinglePackage.ERROR_SINGLE_PACKAGE_NAME_IS_BLANK -> {
                showPreProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
                    context, context.getString(R.string.packageNameIsEmpty)
                )
                false
            }
            FUFSinglePackage.ERROR_DEVICE_ANDROID_VERSION_TOO_LOW -> {
                showPreProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
                    context, context.getString(R.string.sysVerLow)
                )
                false
            }
            FUFSinglePackage.ERROR_NO_ROOT_PERMISSION -> {
                showPreProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
                    context, context.getString(R.string.noRootPermission)
                )
                false
            }
            FUFSinglePackage.ERROR_DPM_EXECUTE_FAILED_FROM_SYSTEM,
            FUFSinglePackage.ERROR_PROFILE_OWNER_EXECUTE_FAILED_FROM_SYSTEM -> {
                showPreProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
                    context, context.getString(R.string.executeFailedFromSystem)
                )
                false
            }
            FUFSinglePackage.ERROR_NOT_DEVICE_POLICY_MANAGER -> {
                showPreProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
                    context, context.getString(R.string.isNotDevicePolicyManager)
                )
                false
            }
            FUFSinglePackage.ERROR_NOT_PROFILE_OWNER -> {
                showPreProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
                    context, context.getString(R.string.isNotProfileOwner)
                )
                false
            }
            FUFSinglePackage.ERROR_NO_SUCH_API_MODE -> {
                showPreProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
                    context, context.getString(R.string.noSuchApiMode)
                )
                false
            }
            FUFSinglePackage.ERROR_NOT_SYSTEM_APP -> {
                showPreProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
                    context, context.getString(R.string.isNotSystemApp)
                )
                false
            }
            FUFSinglePackage.ERROR_INSUFFICIENT_PERMISSION -> {
                showPreProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
                    context, context.getString(R.string.insufficientPermission)
                )
                false
            }
            FUFSinglePackage.ERROR_STATE_DID_NOT_CHANGE -> {
                showPreProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
                    context, context.getString(R.string.stateDidNotChange)
                )
                false
            }
            FUFSinglePackage.ERROR_OTHER -> {
                showPreProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
                    context, context.getString(R.string.unknownError)
                )
                false
            }
            else -> {
                showPreProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
                    context, context.getString(R.string.unknownError)
                )
                false
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun getFUFRelatedToastString(
        context: Context,
        resultCode: Int,
        showUnnecessaryToast: Boolean? = null
    ): String? {
        return when (resultCode) {
            FUFSinglePackage.ERROR_NO_ERROR_CAUGHT_UNKNOWN_RESULT -> {
                if (showUnnecessaryToast == false || lesserToast.getValue())
                    null
                else
                    getPreProcessedFUFResultToastString(
                        context, context.getString(R.string.unknownResult_probablySuccess)
                    )
            }
            FUFSinglePackage.ERROR_NO_ERROR_SUCCESS -> {
                if (showUnnecessaryToast == false || lesserToast.getValue())
                    null
                else
                    getPreProcessedFUFResultToastString(
                        context, context.getString(R.string.success)
                    )
            }
            FUFSinglePackage.ERROR_SINGLE_PACKAGE_NAME_IS_BLANK ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.packageNameIsEmpty)
                )
            FUFSinglePackage.ERROR_DEVICE_ANDROID_VERSION_TOO_LOW ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.sysVerLow)
                )
            FUFSinglePackage.ERROR_NO_ROOT_PERMISSION ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.noRootPermission)
                )
            FUFSinglePackage.ERROR_DPM_EXECUTE_FAILED_FROM_SYSTEM,
            FUFSinglePackage.ERROR_PROFILE_OWNER_EXECUTE_FAILED_FROM_SYSTEM ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.executeFailedFromSystem)
                )
            FUFSinglePackage.ERROR_NOT_DEVICE_POLICY_MANAGER ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.isNotDevicePolicyManager)
                )
            FUFSinglePackage.ERROR_NOT_PROFILE_OWNER ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.isNotProfileOwner)
                )
            FUFSinglePackage.ERROR_NO_SUCH_API_MODE ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.noSuchApiMode)
                )
            FUFSinglePackage.ERROR_NOT_SYSTEM_APP ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.isNotSystemApp)
                )
            FUFSinglePackage.ERROR_OPERATION_ON_FREEZEYOU_IS_NOT_ALLOWED ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.operationOnFreezeYouIsNotAllowed)
                )
            FUFSinglePackage.ERROR_USER_SET_NOT_ALLOWED_TO_FREEZE_FOREGROUND_APPLICATION ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.theAppIsCurrentlyTheForegroundApp)
                )
            FUFSinglePackage.ERROR_USER_SET_NOT_ALLOWED_TO_FREEZE_NOTIFYING_APPLICATION ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.theAppHasNotificationInTheNotificationBar)
                )
            FUFSinglePackage.ERROR_INSUFFICIENT_PERMISSION,
            FUFSinglePackage.ERROR_NO_SUFFICIENT_PERMISSION_TO_START_THIS_ACTIVITY ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.insufficientPermission)
                )
            FUFSinglePackage.ERROR_CANNOT_FIND_THE_LAUNCH_INTENT_OR_UNFREEZE_FAILED ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.unrootedOrCannotFindTheLaunchIntent)
                )
            FUFSinglePackage.ERROR_STATE_DID_NOT_CHANGE ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.stateDidNotChange)
                )
            FUFSinglePackage.ERROR_OTHER ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.unknownError)
                )
            else ->
                getPreProcessedFUFResultToastString(
                    context, context.getString(R.string.unknownError)
                )
        }
    }

    private fun getPreProcessedFUFResultToastString(context: Context, message: String): String {
        return String.format(context.getString(R.string.executionResult_colon_message), message)
    }

    private fun showPreProcessFUFResultAndShowToastAndReturnIfResultBelongsSuccess(
        context: Context,
        message: String
    ) {
        ToastUtils.showShortToast(
            context,
            String.format(context.getString(R.string.executionResult_colon_message), message)
        )
    }

    fun checkAndEnableShizukuMultiProcessSupport(context: Context) {
        if ("10" == DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode.getValue()
            || "9" == DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode.getValue()
            || "8" == DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode.getValue()
        ) {
            ShizukuProvider.enableMultiProcessSupport(false)
            if (!Sui.isSui()) {
                Sui.init(context.packageName)
            }
            ShizukuProvider.requestBinderForNonProviderProcess(context)
        }
    }
}
