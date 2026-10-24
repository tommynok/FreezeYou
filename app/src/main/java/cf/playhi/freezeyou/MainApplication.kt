package cf.playhi.freezeyou

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.NonNull
import androidx.preference.PreferenceManager
import cf.playhi.freezeyou.service.ScreenLockOneKeyFreezeService
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.*
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.languagePref
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode
import cf.playhi.freezeyou.storage.legacy.LegacyPreferenceSource
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage
import cf.playhi.freezeyou.utils.FUFUtils.checkAndEnableShizukuMultiProcessSupport
import cf.playhi.freezeyou.utils.OneKeyListUtils
import cf.playhi.freezeyou.utils.PackageListUtils
import cf.playhi.freezeyou.utils.ProcessSharedState
import cf.playhi.freezeyou.utils.ShortcutTokenUtils
import cf.playhi.freezeyou.utils.ServiceUtils
import com.getkeepsafe.relinker.ReLinker
import com.google.android.material.color.DynamicColors
import com.tencent.mmkv.MMKV
import android.app.Activity
import android.os.Bundle
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.lang.ref.WeakReference
import java.nio.channels.FileLock


class MainApplication : Application(), Application.ActivityLifecycleCallbacks {

    // Live activities, tracked so a theme/language switch can recreate them all at once
    // instead of waiting for a process restart.
    private val liveActivities = ArrayList<WeakReference<Activity>>()

    fun liveActivities(): List<Activity> {
        val list = ArrayList<Activity>()
        liveActivities.removeAll { it.get() == null || it.get()!!.isFinishing }
        for (reference in liveActivities) {
            reference.get()?.let { list.add(it) }
        }
        return list
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        liveActivities.add(WeakReference(activity))
    }

    override fun onActivityDestroyed(activity: Activity) {
        liveActivities.removeAll { it.get() === activity || it.get() == null }
    }

    override fun onActivityStarted(activity: Activity) {}

    override fun onActivityResumed(activity: Activity) {}

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/content/pm/",
                    "Landroid/content/IIntentSender",
                    "Landroid/content/IntentSender;"
                )
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
        CrashHandler().init(this)

        // Initialize MMKV,
        // use [ReLinker](https://github.com/KeepSafe/ReLinker)
        // to avoid errors like `java.lang.UnsatisfiedLinkError`.
        MMKV.initialize(this) { libName: String? ->
            ReLinker.loadLibrary(
                this@MainApplication,
                libName
            )
        }
        try {
            runMigrationsWithIpcLock()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Dynamic (wallpaper) colors are intentionally OFF: the in-app theme switch and the
        // activity setTheme flow wiped the overlay in both directions we tried, and the
        // static Material3 palettes read better on this app's screens. Revisit only with a
        // ContextThemeWrapper-based design, not applyStyle/applyToActivities hacks.
        if (false && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            com.google.android.material.color.DynamicColors.applyToActivitiesIfAvailable(this)
        }
        checkAndEnableShizukuMultiProcessSupport(this)
        ShortcutTokenUtils.initDynamicShortcuts(this)
        try {
            // The process can be started by a provider/binder call from another app: on
            // Android 12+ starting a foreground service from that background state throws
            // ForegroundServiceStartNotAllowedException. The service is started by its own
            // triggers anyway; this call only covers the case where the app was launched
            // from the launcher with the screen-lock automation enabled.
            checkAndStartScreenLockOneKeyFreezeService()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun runMigrationsWithIpcLock() {
        val lockFile = File(filesDir, "migrations_ipc.lock")
        val channel = RandomAccessFile(lockFile, "rw").channel
        var lock: FileLock? = null
        try {
            lock = channel.lock()
            // Read once for the whole chain. Legacy SharedPreferences fallback is migrated to MMKV.
            val legacy = LegacyPreferenceSource(this)
            checkAndMigrateAppIconDataPreference()
            checkAndMigrateSharedPreferencesSettingsToMMKV()
            checkAndMigrateEnableAuthenticationPreferenceDataToMMKV(legacy)
            checkAndMigrateMultiProcessPreferenceDataToMMKV(legacy)
            checkAndMigrateOneKeyListsToMMKV()
            checkAndMigrateOneKeyConfig()
            checkAndMigrateKeySAFUFAndNCWFUSAFUFAndOIAUUSAFUF()
            initDatabases()
        } finally {
            try {
                lock?.release()
            } catch (ignored: Exception) {
            }
            try {
                channel.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun migrateOneKeyConfig() {
        val absoluteFilesPath = filesDir.absolutePath
        val sharedPrefsPath = (absoluteFilesPath.substring(0, absoluteFilesPath.length - 5)
                + "shared_prefs" + File.separator)
        migrateOneKeyData(
            File(sharedPrefsPath + "AutoFreezeApplicationList.xml"),
            "AutoFreezeApplicationList",
            getString(R.string.sAutoFreezeApplicationList)
        )
        migrateOneKeyData(
            File(sharedPrefsPath + "OneKeyUFApplicationList.xml"),
            "OneKeyUFApplicationList",
            getString(R.string.sOneKeyUFApplicationList)
        )
        migrateOneKeyData(
            File(sharedPrefsPath + "FreezeOnceQuit.xml"),
            "FreezeOnceQuit",
            getString(R.string.sFreezeOnceQuit)
        )
    }

    private fun parseLegacyOneKeyPackages(pkgNameS: String?): List<String> {
        if (pkgNameS.isNullOrEmpty()) return emptyList()
        val result = mutableListOf<String>()
        if (pkgNameS.contains("||") || pkgNameS.contains("|")) {
            val parts = pkgNameS.split("\\|\\|".toRegex())
            for (part in parts) {
                val cleaned = part.replace("\\|".toRegex(), "").trim()
                if (cleaned.isNotEmpty()) {
                    result.add(cleaned)
                }
            }
        } else {
            val parsed = PackageListUtils.parse(pkgNameS)
            for (p in parsed) {
                if (p.isNotEmpty()) {
                    result.add(p)
                }
            }
        }
        return result
    }

    private fun migrateOneKeyData(
        oldFile: File,
        old_shared_prefs_name: String,
        new_key_name: String
    ) {
        if (oldFile.exists() && oldFile.isFile) {
            val pkgNameS = applicationContext.getSharedPreferences(
                old_shared_prefs_name, MODE_PRIVATE
            ).getString("pkgName", "")
            val packages = parseLegacyOneKeyPackages(pkgNameS)
            var success = true
            if (packages.isNotEmpty()) {
                success = OneKeyListUtils.addToOneKeyList(this, new_key_name, packages)
            }
            if (success) {
                oldFile.delete()
            }
        }
    }

    /**
     * Copy the three OneKEY lists into the multi-process store.
     *
     * An empty list is left alone: there is nothing to carry, and writing "" would make the next
     * reader's default meaningless.
     */
    @Throws(IOException::class)
    private fun checkAndMigrateOneKeyListsToMMKV() {
        val migrateLock = File(
            filesDir.absolutePath + File.separator
                    + "migrateOneKeyListsToMMKV.lock"
        )
        if (!migrateLock.exists()) {
            // Resolved outside the store: DefaultMultiProcessMMKVStorage has its own getString(),
            // and these three names are resource IDs, not keys.
            val listKeys = listOf(
                getString(R.string.sAutoFreezeApplicationList),
                getString(R.string.sOneKeyUFApplicationList),
                getString(R.string.sFreezeOnceQuit)
            )
            var allSuccess = true
            listKeys.forEach { key ->
                val pkgNames = legacyOneKeyListFromPreferences(key)
                val packages = parseLegacyOneKeyPackages(pkgNames)
                if (packages.isNotEmpty()) {
                    if (!OneKeyListUtils.addToOneKeyList(this, key, packages)) {
                        allSuccess = false
                    }
                }
            }
            if (allSuccess) {
                migrateLock.createNewFile()
            }
        }
    }

    /**
     * A OneKEY list as it was kept before Tray: a preferences file named after the key, holding the
     * whole list under `pkgName`.
     *
     * Importing those three files was all ImportTrayPreferences ever did, and an install that never
     * ran a build with that import still keeps its lists there. The key name and the file name are
     * the same string, which is why the lookup needs nothing else.
     */
    private fun legacyOneKeyListFromPreferences(key: String): String? =
        getSharedPreferences(key, MODE_PRIVATE).getString("pkgName", "")

    @Throws(IOException::class)
    private fun checkAndMigrateOneKeyConfig() {
        val checkFile = File(filesDir.absolutePath + File.separator + "20180808")
        if (!checkFile.exists()) {
            migrateOneKeyConfig()
            checkFile.createNewFile()
        }
    }

    private fun checkAndStartScreenLockOneKeyFreezeService() {
        if (onekeyFreezeWhenLockScreen.getValue()) {
            ServiceUtils.startService(
                this,
                Intent(this, ScreenLockOneKeyFreezeService::class.java)
            )
        }
    }

    @Throws(IOException::class)
    private fun checkAndMigrateAppIconDataPreference() {
        val appIconDataTransfer20181014 = File(
            filesDir.absolutePath
                    + File.separator + "appIconDataTransfer20181014.lock"
        )
        if (!appIconDataTransfer20181014.exists()) {
            val pm = packageManager
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val theCls = arrayOf(
                "cf.playhi.freezeyou.FirstIcon",
                "cf.playhi.freezeyou.SecondIcon",
                "cf.playhi.freezeyou.ThirdIcon"
            )
            val theAppIconPrefs =
                arrayOf("firstIconEnabled", "secondIconEnabled", "thirdIconEnabled")
            for (i in theCls.indices) {
                if (sharedPreferences.getBoolean(
                        theAppIconPrefs[i], theAppIconPrefs[2] == theAppIconPrefs[i]
                    )
                ) {
                    pm.setComponentEnabledSetting(
                        ComponentName(this, theCls[i]),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } else {
                    pm.setComponentEnabledSetting(
                        ComponentName(this, theCls[i]),
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
            appIconDataTransfer20181014.createNewFile()
        }
    }

    @Throws(IOException::class)
    private fun checkAndMigrateEnableAuthenticationPreferenceDataToMMKV(
        legacy: LegacyPreferenceSource
    ) {
        val migrateLock = File(
            filesDir.absolutePath + File.separator
                    + "migrateEnableAuthenticationPreferenceDataToMMKV.lock"
        )
        if (!migrateLock.exists()) {
            DefaultMultiProcessMMKVStorage()
                .putBoolean(
                    enableAuthentication.name,
                    legacy.getBoolean(
                        enableAuthentication.name, enableAuthentication.defaultValue()
                    )
                )
                .sync()
            migrateLock.createNewFile()
        }
    }

    /**
     * Copy settings that are written to default SharedPreferences directly into multi-process MMKV.
     *
     * These settings never depended on Tray database contents, so they are migrated independently
     * of whether Tray can be read.
     */
    @Throws(IOException::class)
    private fun checkAndMigrateSharedPreferencesSettingsToMMKV() {
        val migrateLock = File(
            filesDir.absolutePath + File.separator
                    + "migrateSharedPreferencesSettingsToMMKV.lock"
        )
        if (!migrateLock.exists()) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            DefaultMultiProcessMMKVStorage().run {
                putBoolean(
                    tryDelApkAfterInstalled.name,
                    sharedPreferences.getBoolean(
                        tryDelApkAfterInstalled.name,
                        tryDelApkAfterInstalled.defaultValue()
                    )
                )
                putString(
                    languagePref.name,
                    sharedPreferences.getString(
                        languagePref.name,
                        languagePref.defaultValue()
                    ) ?: languagePref.defaultValue()
                )
                putString(
                    selectFUFMode.name,
                    sharedPreferences.getString(
                        selectFUFMode.name,
                        selectFUFMode.defaultValue()
                    ) ?: selectFUFMode.defaultValue()
                )
                sync()
            }
            migrateLock.createNewFile()
        }
    }

    @Throws(IOException::class)
    private fun checkAndMigrateMultiProcessPreferenceDataToMMKV(
        legacy: LegacyPreferenceSource
    ) {
        val migrateLock = File(
            filesDir.absolutePath + File.separator
                    + "migrateMultiProcessPreferenceDataToMMKV.lock"
        )
        if (!migrateLock.exists()) {
            DefaultMultiProcessMMKVStorage().run {
                DefaultMultiProcessMMKVStorageBooleanKeys.values().forEach { key ->
                    if (key.name != enableAuthentication.name && key != tryDelApkAfterInstalled) {
                        putBoolean(key.name, legacy.getBoolean(key.name, key.defaultValue()))
                    }
                }
                DefaultMultiProcessMMKVStorageStringKeys.values().forEach { key ->
                    if (key != languagePref && key != selectFUFMode) {
                        putString(key.name, legacy.getString(key.name, key.defaultValue()))
                    }
                }
                sync()
            }
            migrateLock.createNewFile()
        }
    }

    @Throws(IOException::class)
    private fun checkAndMigrateKeySAFUFAndNCWFUSAFUFAndOIAUUSAFUF() {
        val migrateLock = File(
            filesDir.absolutePath + File.separator
                    + "migrateKeySAFUFAndNCWFUSAFUFAndOIAUUSAFUF.lock"
        )
        if (!migrateLock.exists()) {
            DefaultMultiProcessMMKVStorage().run {
                val three = arrayOf(
                    shortcutAutoFUF,
                    needConfirmWhenFreezeUseShortcutAutoFUF,
                    openImmediatelyAfterUnfreezeUseShortcutAutoFUF
                )
                three.forEach { key ->
                    putBoolean(
                        key.name,
                        PreferenceManager.getDefaultSharedPreferences(this@MainApplication)
                            .getBoolean(key.name, key.defaultValue())
                    )
                }
                sync()
            }
            migrateLock.createNewFile()
        }
    }

    private fun initDatabases() {
        try {
            val db1 = openOrCreateDatabase("scheduledTriggerTasks", Context.MODE_PRIVATE, null)
            try {
                db1.execSQL("CREATE TABLE IF NOT EXISTS tasks(_id INTEGER PRIMARY KEY AUTOINCREMENT, tg VARCHAR, tgextra VARCHAR, enabled INTEGER(1), label VARCHAR, task VARCHAR, column1 VARCHAR, column2 VARCHAR)")
            } finally {
                db1.close()
            }
            val db2 = openOrCreateDatabase("scheduledTasks", Context.MODE_PRIVATE, null)
            try {
                db2.execSQL("CREATE TABLE IF NOT EXISTS tasks(_id INTEGER PRIMARY KEY AUTOINCREMENT, time VARCHAR, repeat VARCHAR, enabled INTEGER(1), label VARCHAR, task VARCHAR, column1 VARCHAR, column2 VARCHAR)")
            } finally {
                db2.close()
            }
            val db3 = openOrCreateDatabase("userDefinedCategories", Context.MODE_PRIVATE, null)
            try {
                db3.execSQL("CREATE TABLE IF NOT EXISTS categories(_id INTEGER PRIMARY KEY AUTOINCREMENT, label VARCHAR, packages VARCHAR)")
            } finally {
                db3.close()
            }
            for (dbName in arrayOf("ApplicationsUseTimes", "ApplicationsFreezeTimes", "ApplicationsUFreezeTimes")) {
                val db4 = openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
                try {
                    db4.execSQL("CREATE TABLE IF NOT EXISTS TimesList(_id INTEGER PRIMARY KEY AUTOINCREMENT, pkg VARCHAR, times INT)")
                } finally {
                    db4.close()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private var mCurrentPackage = " "

        @JvmStatic
        var waitingForLeavingToInstallApplicationIntent: Intent? = null
            /**
             * @return Intent, or null if no pending request
             */
            get() = field
            /**
             * @param intent Intent (nullable)
             */
            set(intent) {
                field = intent
            }

        @JvmStatic
        fun clearCurrentPackage() {
            mCurrentPackage = " "
            ProcessSharedState.clearForegroundPackage()
        }

        /**
         * Written by the accessibility service, which runs in `:backgroundService`, and read
         * wherever a freeze is about to happen — which is no longer the same process. It is kept
         * in the shared store for that reason; the field beside it only serves as a fallback for
         * the process that did the writing, in case the store has nothing yet.
         */
        @JvmStatic
        var currentPackage: String?
            @NonNull
            get() = ProcessSharedState.getForegroundPackage() ?: mCurrentPackage
            set(pkgName) {
                if (pkgName != null && pkgName != mCurrentPackage) {
                    mCurrentPackage = pkgName
                    ProcessSharedState.setForegroundPackage(pkgName)
                }
            }
    }
}
