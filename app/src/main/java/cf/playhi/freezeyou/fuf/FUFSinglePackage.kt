package cf.playhi.freezeyou.fuf

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.system.Os
import android.util.Log
import cf.playhi.freezeyou.DeviceAdminReceiver.getComponentName
import cf.playhi.freezeyou.utils.DebugModeUtils.isDebugModeEnabled
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils.*
import cf.playhi.freezeyou.utils.FUFUtils.checkMRootFrozen
import cf.playhi.freezeyou.utils.FUFUtils.checkRootFrozen
import cf.playhi.freezeyou.utils.FUFUtils.isSystemApp
import cf.playhi.freezeyou.utils.ProcessUtils.fAURoot
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.ShizukuProvider
import rikka.shizuku.SystemServiceHelper
import rikka.shizuku.Shizuku
import java.lang.reflect.InvocationTargetException

/**
 * Core engine for freezing (disabling/hiding) and unfreezing (enabling/unhiding) a single package.
 *
 * <p><b>Architecture &amp; System Modes Overview:</b><br>
 * Android provides several distinct mechanisms to suppress application execution, each with
 * different privilege requirements, persistence semantics, and launcher integration:
 * <ul>
 *   <li><b>Device Policy Manager (DPM) Modes (1, 7):</b> Uses {@code DevicePolicyManager.setApplicationHidden}.
 *       The package remains installed and retains all permissions and data, but its components and
 *       launcher icons are completely hidden from the user. Requires Device Owner (mode 1) or Profile
 *       Owner (mode 7).</li>
 *   <li><b>Root Shell Modes (2, 3):</b> Dispatches privileged shell commands ({@code pm disable}/{@code pm enable}
 *       or {@code pm hide}/{@code pm unhide}) via su/app_process. Mode 3 (hide) mirrors DPM hiding without
 *       device owner setup. Mode 2 toggles package component state.</li>
 *   <li><b>Privileged System App Modes (4, 5, 6):</b> Uses {@link PackageManager#setApplicationEnabledSetting}
 *       directly when FreezeYou is installed as a privileged system app ({@code /system/priv-app}).</li>
 *   <li><b>Shizuku / Sui Binder IPC Modes (8, 9, 10):</b> Uses Shizuku to invoke hidden
 *       {@code IPackageManager.setApplicationEnabledSetting} directly over Binder IPC without spawning
 *       slow shell processes. Handles Android 14+ UID 2000 permission constraints automatically.</li>
 * </ul>
 *
 * @property context Application or Activity context
 * @property singlePackageName Target package identifier
 * @property actionMode Operation target: [ACTION_MODE_FREEZE] (0) or [ACTION_MODE_UNFREEZE] (1)
 * @property apiMode Specific API implementation to execute (0 through 10)
 */
open class FUFSinglePackage(
    open val context: Context,
    open val singlePackageName: String,
    open val actionMode: Int,
    open val apiMode: Int
) {

    /**
     * Executes the requested freeze or unfreeze operation according to [apiMode].
     *
     * <p>Includes self-preservation validation preventing FreezeYou from freezing its own package
     * (matching dynamic {@code context.packageName} to accommodate custom application IDs).
     *
     * @return Result status code: [ERROR_NO_ERROR_SUCCESS] (0) on success, or a negative error code on failure
     */
    open suspend fun commit(): Int {
        if (singlePackageName.isBlank()) return ERROR_SINGLE_PACKAGE_NAME_IS_BLANK

        // The fork may be installed under a different applicationId, so compare against the id
        // this build actually runs as rather than the upstream package name.
        if (context.packageName == singlePackageName) {
            return ERROR_OPERATION_ON_FREEZEYOU_IS_NOT_ALLOWED
        }

        return when (apiMode) {
            @Suppress("DEPRECATION")
            API_FREEZEYOU_LEGACY_AUTO ->
                pureExecuteAPIAutoAction()
            API_FREEZEYOU_MROOT_DPM ->
                pureExecuteAPIDPMAction()
            API_FREEZEYOU_MROOT_PROFILE_OWNER ->
                pureExecuteAPIProfileOwnerAction()
            API_FREEZEYOU_ROOT_DISABLE_ENABLE ->
                pureExecuteAPIRootAction(false)
            API_FREEZEYOU_ROOT_UNHIDE_HIDE ->
                pureExecuteAPIRootAction(true)
            API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE ->
                pureExecuteAPISystemAppDisabledAction()
            API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED ->
                pureExecuteAPISystemAppDisabledUntilUsedAction()
            API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE_USER ->
                pureExecuteAPISystemAppDisabledUserAction()
            API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE,
            API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED,
            API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_USER ->
                pureExecuteAPIShizukuAction()
            else ->
                ERROR_NO_SUCH_API_MODE
        }
    }

    /**
     * Legacy automatic resolution mode (Mode 0).
     * Attempts Device Owner (DPM) if available on API 21+, otherwise falls back to Root.
     * When unfreezing, checks whether the package was hidden via DPM or disabled via Root.
     */
    private fun pureExecuteAPIAutoAction(): Int {
        return if (actionMode == ACTION_MODE_FREEZE) {
            if (Build.VERSION.SDK_INT >= 21 && isDeviceOwner(context)) {
                pureExecuteAPIDPMAction()
            } else {
                pureExecuteAPIRootAction()
            }
        } else {
            if (checkMRootFrozen(context, singlePackageName)) {
                pureExecuteAPIDPMAction()
            } else {
                pureExecuteAPIRootAction()
            }
        }
    }

    /**
     * Executes freeze/unfreeze via Device Policy Manager (Mode 1: Device Owner).
     *
     * <p>Uses {@link android.app.admin.DevicePolicyManager#setApplicationHidden}.
     * When hidden, the application is disabled from running and its launcher icons are hidden,
     * but its APK, user data, and runtime permissions remain completely preserved.
     *
     * <p>Optimized with early short-circuiting: if an unfreeze is requested and the package
     * is already unhidden, returns success immediately without issuing redundant Binder IPC.
     */
    private fun pureExecuteAPIDPMAction(): Int {

        if (Build.VERSION.SDK_INT < 21) return ERROR_DEVICE_ANDROID_VERSION_TOO_LOW
        if (!isDeviceOwner(context)) return ERROR_NOT_DEVICE_POLICY_MANAGER

        val hidden = actionMode == ACTION_MODE_FREEZE
        if (!hidden &&
            !getDevicePolicyManager(context)
                .isApplicationHidden(getComponentName(context), singlePackageName)
        ) {
            return ERROR_NO_ERROR_SUCCESS
        }

        return if (getDevicePolicyManager(context).setApplicationHidden(
                getComponentName(context),
                singlePackageName,
                hidden
            )
        ) {
            ERROR_NO_ERROR_SUCCESS
        } else {
            ERROR_DPM_EXECUTE_FAILED_FROM_SYSTEM
        }

    }

    /**
     * Resolves the appropriate root command variant (hide vs disable) based on [apiMode].
     */
    private fun pureExecuteAPIRootAction(): Int {
        return when (apiMode) {
            @Suppress("DEPRECATION")
            API_FREEZEYOU_LEGACY_AUTO, API_FREEZEYOU_ROOT_DISABLE_ENABLE ->
                pureExecuteAPIRootAction(false)
            API_FREEZEYOU_ROOT_UNHIDE_HIDE ->
                pureExecuteAPIRootAction(true)
            else ->
                ERROR_OTHER
        }
    }

    /**
     * Executes root freeze/unfreeze via {@link ProcessUtils#fAURoot}.
     *
     * @param hideMode If true, executes {@code pm hide} / {@code pm unhide} (Mode 3);
     *                 if false, executes {@code pm disable} / {@code pm enable} (Mode 2).
     */
    private fun pureExecuteAPIRootAction(hideMode: Boolean): Int {

        var returnValue = ERROR_OTHER
        val enable = actionMode == ACTION_MODE_UNFREEZE
        try {
            val exitValue = fAURoot(singlePackageName, enable, hideMode)
            returnValue = if (exitValue == 0) {
                ERROR_NO_ERROR_SUCCESS
            } else {
                ERROR_OTHER
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val eMsg = e.message
            if (eMsg != null &&
                (eMsg.contains("permission denied", true)
                        || eMsg.contains("not found", true))
            ) {
                returnValue = ERROR_NO_ROOT_PERMISSION
            }
        }
        return returnValue
    }

    /**
     * Privileged System App Mode 4: {@code COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED}.
     *
     * <p>Requires FreezeYou to reside in {@code /system/priv-app}. The target application
     * remains disabled until explicitly invoked by a user action or system intent.
     */
    private fun pureExecuteAPISystemAppDisabledUntilUsedAction(): Int {

        if (Build.VERSION.SDK_INT < 18) return ERROR_DEVICE_ANDROID_VERSION_TOO_LOW
        if (!isSystemApp(context)) return ERROR_NOT_SYSTEM_APP
        val freeze = actionMode == ACTION_MODE_FREEZE
        context.packageManager.setApplicationEnabledSetting(
            singlePackageName,
            if (freeze)
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
            else
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            0
        )
        return ERROR_NO_ERROR_CAUGHT_UNKNOWN_RESULT
    }

    /**
     * Privileged System App Mode 5: {@code COMPONENT_ENABLED_STATE_DISABLED_USER}.
     *
     * <p>Requires FreezeYou to reside in {@code /system/priv-app}. Marks the package as
     * disabled explicitly by user action, preventing third-party packages from auto-re-enabling it.
     */
    private fun pureExecuteAPISystemAppDisabledUserAction(): Int {

        if (!isSystemApp(context)) return ERROR_NOT_SYSTEM_APP
        val freeze = actionMode == ACTION_MODE_FREEZE
        context.packageManager.setApplicationEnabledSetting(
            singlePackageName,
            if (freeze)
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            else
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            0
        )
        return ERROR_NO_ERROR_CAUGHT_UNKNOWN_RESULT
    }

    /**
     * Privileged System App Mode 6: {@code COMPONENT_ENABLED_STATE_DISABLED}.
     *
     * <p>Requires FreezeYou to reside in {@code /system/priv-app}. Completely disables the package
     * at framework level regardless of user context.
     */
    private fun pureExecuteAPISystemAppDisabledAction(): Int {

        if (!isSystemApp(context)) return ERROR_NOT_SYSTEM_APP
        val freeze = actionMode == ACTION_MODE_FREEZE
        context.packageManager.setApplicationEnabledSetting(
            singlePackageName,
            if (freeze)
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            0
        )
        return ERROR_NO_ERROR_CAUGHT_UNKNOWN_RESULT
    }

    /**
     * Executes freeze/unfreeze via Device Policy Manager in Profile Owner mode (Mode 7).
     *
     * <p>Functions identically to Mode 1 (Device Owner) using {@code setApplicationHidden},
     * but scoped specifically to a Managed Profile (Work Profile).
     */
    private fun pureExecuteAPIProfileOwnerAction(): Int {

        if (Build.VERSION.SDK_INT < 21) return ERROR_DEVICE_ANDROID_VERSION_TOO_LOW
        if (!isProfileOwner(context)) return ERROR_NOT_PROFILE_OWNER

        val hidden = actionMode == ACTION_MODE_FREEZE
        if (!hidden &&
            !getDevicePolicyManager(context)
                .isApplicationHidden(getComponentName(context), singlePackageName)
        ) {
            return ERROR_NO_ERROR_SUCCESS
        }

        return if (getDevicePolicyManager(context).setApplicationHidden(
                getComponentName(context),
                singlePackageName,
                hidden
            )
        ) {
            ERROR_NO_ERROR_SUCCESS
        } else {
            ERROR_PROFILE_OWNER_EXECUTE_FAILED_FROM_SYSTEM
        }

    }

    /**
     * Executes freeze/unfreeze using direct Binder IPC via Shizuku / Sui (Modes 8, 9, 10).
     *
     * <p><b>Android 14 (API 34) Security Restrictions:</b><br>
     * In Android 14+, {@code PackageManagerService} introduced strict caller UID validation on
     * {@code setApplicationEnabledSetting}. Plain Shizuku runs as UID 2000 (shell). Shell UID
     * is permitted to toggle applications only between {@code ENABLED}, {@code DISABLED_USER},
     * and {@code DEFAULT}. Attempting to pass {@code DISABLED} or {@code DISABLED_UNTIL_USED}
     * throws a {@link SecurityException}.
     *
     * <p>FreezeYou inspects the Shizuku server UID via [resolveShizukuUid]:
     * <ul>
     *   <li>If UID is 0 (Sui / root-backed Shizuku): preserves exact requested states (Modes 8, 9, 10).</li>
     *   <li>If UID is 2000 (standard ADB Shizuku): automatically remaps all freeze modes to
     *       {@link PackageManager#COMPONENT_ENABLED_STATE_DISABLED_USER}, ensuring compatibility
     *       without SecurityExceptions.</li>
     * </ul>
     *
     * <p><b>Self-Healing &amp; Verification:</b><br>
     * Handles {@link android.os.DeadObjectException} by refreshing cached proxies when Shizuku restarts.
     * Verifies the effective state via [verifyFrozenStateSettled] to handle asynchronous disk commits
     * and OEM ROM quirks (MIUI/HyperOS).
     */
    private fun pureExecuteAPIShizukuAction(): Int {
        if (Build.VERSION.SDK_INT < 23) return ERROR_DEVICE_ANDROID_VERSION_TOO_LOW
        val debug = isDebugModeEnabled()
        val startedAt = System.currentTimeMillis()
        try {
            ensureBinderAlive()?.let { return it }
            val binderReadyAt = System.currentTimeMillis()

            val freeze = actionMode == ACTION_MODE_FREEZE
            // Android 14 PackageManagerService: shell uid (plain Shizuku) may only toggle a
            // whole package between ENABLED, DISABLED_USER and DEFAULT - DISABLED and
            // DISABLED_UNTIL_USED from shell are rejected with SecurityException. Root
            // Shizuku (Sui) has no such limit, so under root the exact upstream states per
            // mode are preserved (UNTIL_USED / USER / DISABLED); under shell everything
            // lands on DISABLED_USER, the only shell-allowed frozen state.
            val freezeState = if (resolveShizukuUid() == 0) {
                when (apiMode) {
                    API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED ->
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
                    API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_USER ->
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                    API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE ->
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                    else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                }
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            }
            val newState = if (freeze) {
                freezeState
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }

            try {
                invokeSetApplicationEnabledSetting(newState)
            } catch (e: InvocationTargetException) {
                if (e.cause !is android.os.DeadObjectException) throw e
                // The cached proxy pointed at a Shizuku service that has since restarted. The
                // binder itself is alive again (ensureBinderAlive said so), so one fresh proxy
                // is enough — no need to make the user tap twice.
                invalidateCachedPackageManagerProxy()
                invokeSetApplicationEnabledSetting(newState)
            }
            val calledAt = System.currentTimeMillis()
            // The binder call returns nothing, so "no exception" is not the same as "it took
            // effect" — some ROMs accept the call and ignore it. Read the state back instead of
            // reporting a success the user cannot see.
            val settled = verifyFrozenStateSettled(freeze)
            if (debug) {
                Log.e(
                    "DebugModeLogcat",
                    "shizuku $singlePackageName freeze=$freeze settled=$settled " +
                            "binder=${binderReadyAt - startedAt}ms " +
                            "call=${calledAt - binderReadyAt}ms " +
                            "verify=${System.currentTimeMillis() - calledAt}ms"
                )
            }
            return if (settled) ERROR_NO_ERROR_SUCCESS else ERROR_STATE_DID_NOT_CHANGE
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
            if (e.cause is SecurityException) {
                return ERROR_INSUFFICIENT_PERMISSION
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            return ERROR_INSUFFICIENT_PERMISSION
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ERROR_OTHER
    }

    private fun invokeSetApplicationEnabledSetting(newState: Int) {
        val (pm, method) = obtainCachedPackageManagerProxyAndMethod()
        method.invoke(
            pm,
            singlePackageName,
            newState,
            0,
            /**
             * .../private/android_filesystem_config.h
             * #define AID_USER 100000        /* \T\O\D\O: switch users over to AID_USER_OFFSET */
             * #define AID_USER_OFFSET 100000 /* offset for uid ranges for each user */
             */
            Os.getuid() / 100000,
            context.packageName
        )
    }

    /**
     * [checkRootFrozen] reads exactly the value the binder call above writes — the application
     * enabled setting — so it is a real confirmation, not a guess. PackageManager settings are
     * persisted asynchronously, so the first read can still show the old value; poll briefly
     * instead of waiting a fixed amount, which keeps the common case at one read.
     *
     * Deliberately not used for the root paths: `pm hide` is a different mechanism that this
     * check does not observe.
     */
    private fun verifyFrozenStateSettled(expectFrozen: Boolean): Boolean {
        var waited = 0
        while (true) {
            if (checkRootFrozen(context, singlePackageName, context.packageManager) == expectFrozen) {
                return true
            }
            if (waited >= STATE_SETTLE_TIMEOUT_MS) return false
            Thread.sleep(STATE_SETTLE_POLL_MS)
            waited += STATE_SETTLE_POLL_MS.toInt()
        }
    }

    /**
     * Avoids re-running the binder-ready poll loop for every single package in a batch:
     * once confirmed alive, subsequent calls within [BINDER_ALIVE_CACHE_MS] skip straight through.
     */
    private fun ensureBinderAlive(): Int? {
        val now = System.currentTimeMillis()
        if (Shizuku.pingBinder()) {
            // The binder is already here — the main process gets it from ShizukuProvider, other
            // processes keep it once received. Asking for it again is an IPC round trip through
            // the provider for nothing. Only re-check the permission now and then.
            if (now - lastBinderAliveCheckMs < BINDER_ALIVE_CACHE_MS) return null
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return ERROR_INSUFFICIENT_PERMISSION
            }
            lastBinderAliveCheckMs = now
            return null
        }

        ShizukuProvider.requestBinderForNonProviderProcess(context)

        var waited = 0
        while (!Shizuku.pingBinder() && waited < 3000) {
            Thread.sleep(50)
            waited += 50
        }
        if (!Shizuku.pingBinder()) {
            return ERROR_INSUFFICIENT_PERMISSION
        }

        // pingBinder only says the service is running. Asking here turns a per-package
        // SecurityException into one cheap check, and reports the actual reason.
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return ERROR_INSUFFICIENT_PERMISSION
        }

        invalidateCachedPackageManagerProxy()
        lastBinderAliveCheckMs = now
        return null
    }

    @SuppressLint("PrivateApi")
    private fun obtainCachedPackageManagerProxyAndMethod(): Pair<Any, java.lang.reflect.Method> {
        cachedPackageManagerProxy?.let { pm ->
            cachedSetApplicationEnabledSettingMethod?.let { method ->
                return pm to method
            }
        }

        val pm = Class.forName("android.content.pm.IPackageManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, ShizukuBinderWrapper(SystemServiceHelper.getSystemService("package")))
        val method = pm::class.java.getMethod(
            "setApplicationEnabledSetting",
            String::class.java, // packageName
            Int::class.java, // newState
            Int::class.java, // flags
            Int::class.java, // userId
            String::class.java // callingPackage
        )
        cachedPackageManagerProxy = pm
        cachedSetApplicationEnabledSettingMethod = method
        return pm to method
    }

    private fun invalidateCachedPackageManagerProxy() {
        cachedPackageManagerProxy = null
        cachedSetApplicationEnabledSettingMethod = null
        shizukuUidCache = null
    }

    /**
     * The uid the Shizuku server runs under: 0 for root (Sui), 2000 for plain shell.
     * Probed once through the server itself ("id -u") and cached for the lifetime of the
     * binder proxy; the shell probe is also the binder-liveness check in disguise.
     */
    private fun resolveShizukuUid(): Int {
        shizukuUidCache?.let { return it }
        val uid = try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", "id -u"), null, null)
            val out = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            out.toIntOrNull() ?: 2000
        } catch (e: Exception) {
            e.printStackTrace()
            2000
        }
        shizukuUidCache = uid
        return uid
    }

    companion object {
        private const val BINDER_ALIVE_CACHE_MS = 5000L

        @Volatile
        private var shizukuUidCache: Int? = null
        private const val STATE_SETTLE_POLL_MS = 50L
        private const val STATE_SETTLE_TIMEOUT_MS = 300

        @Volatile
        private var lastBinderAliveCheckMs: Long = 0L

        @Volatile
        private var cachedPackageManagerProxy: Any? = null

        @Volatile
        private var cachedSetApplicationEnabledSettingMethod: java.lang.reflect.Method? = null

        const val ACTION_MODE_FREEZE = 0
        const val ACTION_MODE_UNFREEZE = 1

        const val ERROR_NO_ERROR_CAUGHT_UNKNOWN_RESULT = 1
        const val ERROR_NO_ERROR_SUCCESS = 0
        const val ERROR_OTHER = -1
        const val ERROR_SINGLE_PACKAGE_NAME_IS_BLANK = -2
        const val ERROR_DEVICE_ANDROID_VERSION_TOO_LOW = -3
        const val ERROR_NO_ROOT_PERMISSION = -4
        const val ERROR_DPM_EXECUTE_FAILED_FROM_SYSTEM = -5
        const val ERROR_NOT_DEVICE_POLICY_MANAGER = -6
        const val ERROR_NO_SUCH_API_MODE = -7
        const val ERROR_NOT_SYSTEM_APP = -8
        const val ERROR_NOT_PROFILE_OWNER = -9
        const val ERROR_PROFILE_OWNER_EXECUTE_FAILED_FROM_SYSTEM = -10
        const val ERROR_OPERATION_ON_FREEZEYOU_IS_NOT_ALLOWED = -11
        const val ERROR_USER_SET_NOT_ALLOWED_TO_FREEZE_FOREGROUND_APPLICATION = -12
        const val ERROR_USER_SET_NOT_ALLOWED_TO_FREEZE_NOTIFYING_APPLICATION = -13
        const val ERROR_NO_SUFFICIENT_PERMISSION_TO_START_THIS_ACTIVITY = -14
        const val ERROR_CANNOT_FIND_THE_LAUNCH_INTENT_OR_UNFREEZE_FAILED = -15
        const val ERROR_INSUFFICIENT_PERMISSION = -16
        const val ERROR_STATE_DID_NOT_CHANGE = -17

        /**
         * Mode 0: FreezeYou legacy automatic resolution mode.
         *
         * <p>Inspects runtime environment: prioritizes Device Owner (DPM) if provisioned on API 21+,
         * falling back to Root shell commands otherwise.
         *
         * @deprecated Use explicit modes ([API_FREEZEYOU_MROOT_DPM], [API_FREEZEYOU_ROOT_DISABLE_ENABLE],
         * or [API_FREEZEYOU_ROOT_UNHIDE_HIDE]) to avoid unexpected privilege fallback behavior.
         */
        @Deprecated("Use API_FREEZEYOU_MROOT_DPM or API_FREEZEYOU_ROOT_DISABLE_ENABLE instead.")
        const val API_FREEZEYOU_LEGACY_AUTO = 0

        /**
         * Mode 1: Device Policy Manager (DPM) Device Owner mode.
         *
         * <p>Calls {@link android.app.admin.DevicePolicyManager#setApplicationHidden}.
         * Requires FreezeYou to be configured as device owner via {@code dpm set-device-owner}.
         * Hides the app and halts background processes while preserving all application data and permissions.
         */
        const val API_FREEZEYOU_MROOT_DPM = 1

        /**
         * Mode 2: Root Shell Disable mode.
         *
         * <p>Executes {@code pm disable} / {@code pm enable} via su/app_process.
         * Toggles component state to disabled. May trigger launcher icon reorganization on some devices.
         */
        const val API_FREEZEYOU_ROOT_DISABLE_ENABLE = 2

        /**
         * Mode 3: Root Shell Hide mode.
         *
         * <p>Executes {@code pm hide} / {@code pm unhide} via su/app_process.
         * Packages are hidden similarly to DPM mode without altering component enabled states.
         */
        const val API_FREEZEYOU_ROOT_UNHIDE_HIDE = 3

        /**
         * Mode 4: Privileged System App (DISABLE_UNTIL_USED) mode.
         *
         * <p>Uses {@link PackageManager#COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED}.
         * Requires FreezeYou to be installed in {@code /system/priv-app}. The app is suppressed until
         * explicitly triggered by user launch or system intent. Available on API 18+.
         */
        const val API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED = 4

        /**
         * Mode 5: Privileged System App (DISABLE_USER) mode.
         *
         * <p>Uses {@link PackageManager#COMPONENT_ENABLED_STATE_DISABLED_USER}.
         * Requires FreezeYou in {@code /system/priv-app}. Marks the application as explicitly disabled
         * by the user, preventing automatic re-activation by third-party intents.
         */
        const val API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE_USER = 5

        /**
         * Mode 6: Privileged System App (DISABLE) mode.
         *
         * <p>Uses {@link PackageManager#COMPONENT_ENABLED_STATE_DISABLED}.
         * Unconditionally disables the package at the framework package manager level.
         */
        const val API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE = 6

        /**
         * Mode 7: Device Policy Manager (DPM) Profile Owner mode.
         *
         * <p>Calls {@code setApplicationHidden} within a Managed Profile (Work Profile).
         * Enables non-root freezing inside enterprise work containers without requiring full device ownership.
         */
        const val API_FREEZEYOU_MROOT_PROFILE_OWNER = 7

        /**
         * Mode 8: Shizuku / Sui Binder IPC (DISABLE_UNTIL_USED) mode.
         *
         * <p>Direct Binder IPC invocation of {@code IPackageManager.setApplicationEnabledSetting}.
         * On Android 14+ with plain Shizuku (UID 2000), PMS blocks this state with SecurityException;
         * FreezeYou automatically remaps to [API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_USER].
         * Full state fidelity is preserved when running under root-backed Sui (UID 0).
         */
        const val API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED = 8

        /**
         * Mode 9: Shizuku / Sui Binder IPC (DISABLE_USER) mode.
         *
         * <p>Direct Binder IPC invocation setting {@code COMPONENT_ENABLED_STATE_DISABLED_USER}.
         * The standard, recommended non-root freezing mode: universally supported by Android shell UID (2000)
         * across all Android versions without PMS security restrictions.
         */
        const val API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_USER = 9

        /**
         * Mode 10: Shizuku / Sui Binder IPC (DISABLE) mode.
         *
         * <p>Direct Binder IPC invocation setting {@code COMPONENT_ENABLED_STATE_DISABLED}.
         * Remapped to [API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_USER] on Android 14+ plain Shizuku (UID 2000)
         * to satisfy PMS security checks; preserved natively under Sui (UID 0).
         */
        const val API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE = 10

    }
}