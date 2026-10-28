package cf.playhi.freezeyou.viewmodel

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_LEGACY_AUTO
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_MROOT_DPM
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_MROOT_PROFILE_OWNER
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_ROOT_DISABLE_ENABLE
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_ROOT_UNHIDE_HIDE
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_USER
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE_USER
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode
import cf.playhi.freezeyou.utils.AccessibilityUtils.isAccessibilitySettingsOn
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils.isDeviceOwner
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils.isProfileOwner
import cf.playhi.freezeyou.utils.FUFUtils
import cf.playhi.freezeyou.utils.RootUtils
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class AutoDiagnosisViewModel(application: Application) : AndroidViewModel(application) {

    private val mutex = Mutex()
    private val loadingProgress: MutableLiveData<Int> = MutableLiveData<Int>().apply { value = -1 }

    private val problemsList: MutableLiveData<MutableList<Map<String, Any>>> =
        MutableLiveData<MutableList<Map<String, Any>>>().apply { value = ArrayList() }

    fun getLoadingProgress(): LiveData<Int> {
        return loadingProgress
    }

    fun getProblemsList(): LiveData<MutableList<Map<String, Any>>> {
        return problemsList
    }

    fun refreshDiagnosisData() {
        viewModelScope.launch(Dispatchers.IO) {
            if (mutex.tryLock("AutoDiagnosisViewModelRefreshingDiagnosisData")) {
                try {
                    refreshDiagnosisDataLocked()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Without a handler, an exception here would kill the process via the
                    // coroutine's uncaught-exception path. Log it and finish the progress
                    // so the screen shows the last completed result instead of dying.
                    Log.e("FreezeYouDebug", "AutoDiagnosis refresh failed", e)
                    loadingProgress.postValue(100)
                } finally {
                    // A check that throws must not strand the mutex: the next refresh would
                    // silently no-op and the screen would stay empty until the activity restarts.
                    mutex.unlock("AutoDiagnosisViewModelRefreshingDiagnosisData")
                }
            }
        }
    }

    private fun refreshDiagnosisDataLocked() {
        val startedAt = SystemClock.elapsedRealtime()
        loadingProgress.postValue(-1)

        // The list is published once at the end; check methods must never touch
        // problemsList directly, or the UI thread can observe a half-updated list.
        val localList = ArrayList<Map<String, Any>>()
        val fufMode = selectFUFMode.getValue(getApplication())?.toIntOrNull()
            ?: API_FREEZEYOU_LEGACY_AUTO
        val dpmRequired = fufMode == API_FREEZEYOU_LEGACY_AUTO
                || fufMode == API_FREEZEYOU_MROOT_DPM
        val profileOwnerRequired = fufMode == API_FREEZEYOU_MROOT_PROFILE_OWNER
        val rootRequired = fufMode == API_FREEZEYOU_LEGACY_AUTO
                || fufMode == API_FREEZEYOU_ROOT_DISABLE_ENABLE
                || fufMode == API_FREEZEYOU_ROOT_UNHIDE_HIDE
        val shizukuRequired = fufMode == API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE
                || fufMode == API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED
                || fufMode == API_FREEZEYOU_SHIZUKU_SYSTEM_APP_ENABLE_DISABLE_USER
        val systemAppRequired = fufMode == API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE
                || fufMode == API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED
                || fufMode == API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE_USER
        loadingProgress.postValue(5)

        checkSystemVersion(localList)
        loadingProgress.postValue(15)

        checkAccessibilityService(localList)
        loadingProgress.postValue(30)

        checkNotificationListenerPermission(localList)
        loadingProgress.postValue(45)

        checkNotifyPermission(localList)
        loadingProgress.postValue(55)

        checkBlueToothPermission(localList)
        loadingProgress.postValue(65)

        checkIsDeviceOwner(localList, dpmRequired)
        loadingProgress.postValue(75)

        checkRootPermission(localList, rootRequired)
        loadingProgress.postValue(85)

        checkProfileOwner(localList, profileOwnerRequired)
        checkSystemAppMode(localList, systemAppRequired, fufMode)
        checkShizuku(localList, shizukuRequired)

        checkIsPowerSaveMode(localList)
        loadingProgress.postValue(92)

        checkIsIgnoringBatteryOptimizations(localList)
        loadingProgress.postValue(97)

        checkIfNoProblemFound(localList)
        loadingProgress.postValue(99)

        localList.sortWith { t0: Map<String, Any>, t1: Map<String, Any> ->
            val i = (t0["level"] as Int).compareTo(
                (t1["level"] as Int)
            )
            if (i == 0) (t0["id"] as String).compareTo((t1["id"] as String)) else i
        }

        problemsList.postValue(localList)

        loadingProgress.postValue(100)

        Log.d(
            "FreezeYouDebug",
            "AutoDiagnosis refresh finished in "
                    + (SystemClock.elapsedRealtime() - startedAt)
                    + " ms with " + localList.size + " rows"
        )
    }

    private fun generateHashMap(
        title: String,
        sTitle: String,
        id: String,
        statusId: Int
    ): HashMap<String, Any> {
        val hashMap = HashMap<String, Any>()
        hashMap["title"] = title
        hashMap["sTitle"] = sTitle
        hashMap["id"] = id
        hashMap["status"] = statusId
        hashMap["level"] = if (statusId == R.drawable.ic_warning) 0 else 1
        return hashMap
    }

    private fun checkSystemVersion(localList: MutableList<Map<String, Any>>) {
        if (Build.VERSION.SDK_INT < 21) {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.sysVerLow),
                    getApplication<Application>().getString(R.string.sysVerLowSummary),
                    "-50",
                    R.drawable.ic_warning
                )
            )
        } else {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.sysVerOk),
                    getApplication<Application>().getString(R.string.sysVerOkSummary),
                    "-1",
                    R.drawable.ic_done
                )
            )
        }
    }

    private fun checkAccessibilityService(localList: MutableList<Map<String, Any>>) {
        if (isAccessibilitySettingsOn(getApplication())) {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.accessibilityServiceEnabled),
                    getApplication<Application>().getString(R.string.accessibilityServiceEnabledSummary),
                    "1",
                    R.drawable.ic_done
                )
            )
        } else {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.ACBSNotEnabled),
                    getApplication<Application>().getString(R.string.accessibilityServiceDisabledSummary),
                    "1",
                    R.drawable.ic_warning
                )
            )
        }
    }

    private fun checkNotificationListenerPermission(localList: MutableList<Map<String, Any>>) {
        if (Build.VERSION.SDK_INT >= 21) {
            val s = Settings.Secure.getString(
                getApplication<Application>().contentResolver,
                "enabled_notification_listeners"
            )
            if (s == null
                || !s.contains("cf.playhi.freezeyou/cf.playhi.freezeyou.MyNotificationListenerService")
            ) {
                localList.add(
                    generateHashMap(
                        getApplication<Application>().getString(R.string.noNotificationListenerPermission),
                        getApplication<Application>().getString(R.string.notificationListenerPermissionNotGrantedSummary),
                        "2",
                        R.drawable.ic_warning
                    )
                )
            } else {
                localList.add(
                    generateHashMap(
                        getApplication<Application>().getString(R.string.notificationListenerPermissionGranted),
                        getApplication<Application>().getString(R.string.notificationListenerPermissionGrantedSummary),
                        "2",
                        R.drawable.ic_done
                    )
                )
            }
        }
    }

    private fun checkNotifyPermission(localList: MutableList<Map<String, Any>>) {
        val notificationManager =
            getApplication<Application>().getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 24 && !notificationManager.areNotificationsEnabled()) {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.noNotifyPermission),
                    getApplication<Application>().getString(R.string.noNotifyPermissionSummary),
                    "6",
                    R.drawable.ic_warning
                )
            )
        } else {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.notifyPermissionGranted),
                    getApplication<Application>().getString(R.string.notifyPermissionGrantedSummary),
                    "6",
                    R.drawable.ic_done
                )
            )
        }
    }

    private fun checkIsDeviceOwner(
        localList: MutableList<Map<String, Any>>, dpmRequired: Boolean
    ) {
        if (isDeviceOwner(getApplication())) {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.deviceOwnerActive),
                    getApplication<Application>().getString(R.string.deviceOwnerActiveSummary),
                    "-3",
                    R.drawable.ic_done
                )
            )
        } else if (dpmRequired) {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.noMRootPermission),
                    getApplication<Application>().getString(R.string.deviceOwnerNotActiveSummary),
                    "-3",
                    R.drawable.ic_warning
                )
            )
        }
    }

    private fun checkRootPermission(
        localList: MutableList<Map<String, Any>>, rootRequired: Boolean
    ) {
        val startedAt = SystemClock.elapsedRealtime()
        // Probing every su candidate can take tens of seconds on some devices, so the full
        // sweep runs only when the active FUF mode actually depends on root. For every other
        // mode this reads the cached result: one quick retest of a known binary, or an
        // instant negative answer inside the failure window.
        val granted = RootUtils.checkRootPermission(rootRequired)
        Log.d(
            "FreezeYouDebug",
            "AutoDiagnosis checkRootPermission took "
                    + (SystemClock.elapsedRealtime() - startedAt)
                    + " ms (required=" + rootRequired + ", granted=" + granted + ")"
        )
        if (!granted) {
            if (rootRequired) {
                localList.add(
                    generateHashMap(
                        getApplication<Application>().getString(R.string.noRootPermission),
                        getApplication<Application>().getString(R.string.rootPermissionNotGrantedSummary),
                        "-3",
                        R.drawable.ic_warning
                    )
                )
            }
        } else {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.rootPermissionGranted),
                    getApplication<Application>().getString(R.string.rootPermissionGrantedSummary),
                    "-3",
                    R.drawable.ic_done
                )
            )
        }
    }

    private fun checkProfileOwner(
        localList: MutableList<Map<String, Any>>, profileOwnerRequired: Boolean
    ) {
        if (isProfileOwner(getApplication())) {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.profileOwnerActive),
                    "",
                    "-3",
                    R.drawable.ic_done
                )
            )
        } else if (profileOwnerRequired) {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.isNotProfileOwner),
                    "",
                    "-3",
                    R.drawable.ic_warning
                )
            )
        }
    }

    private fun checkSystemAppMode(
        localList: MutableList<Map<String, Any>>, systemAppRequired: Boolean, fufMode: Int
    ) {
        if (!systemAppRequired) return
        val app = getApplication<Application>()
        if (fufMode == API_FREEZEYOU_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED
            && Build.VERSION.SDK_INT < 18
        ) {
            localList.add(
                generateHashMap(
                    app.getString(R.string.sysVerLow),
                    "",
                    "-9",
                    R.drawable.ic_warning
                )
            )
            return
        }
        val isSystemApp = FUFUtils.isSystemApp(app)
        val hasChangeComponentEnabledStatePermission =
            ActivityCompat.checkSelfPermission(
                app, Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE
            ) == PackageManager.PERMISSION_GRANTED
        when {
            isSystemApp && hasChangeComponentEnabledStatePermission ->
                localList.add(
                    generateHashMap(
                        app.getString(R.string.systemAppModeActive),
                        "",
                        "-9",
                        R.drawable.ic_done
                    )
                )
            !isSystemApp ->
                localList.add(
                    generateHashMap(
                        app.getString(R.string.isNotSystemApp),
                        "",
                        "-9",
                        R.drawable.ic_warning
                    )
                )
            else ->
                localList.add(
                    generateHashMap(
                        app.getString(R.string.insufficientPermission),
                        "",
                        "-9",
                        R.drawable.ic_warning
                    )
                )
        }
    }

    private fun checkShizuku(
        localList: MutableList<Map<String, Any>>, shizukuRequired: Boolean
    ) {
        if (!shizukuRequired) return
        val app = getApplication<Application>()
        try {
            val startedAt = SystemClock.elapsedRealtime()
            var binderAlive = Shizuku.pingBinder()
            if (!binderAlive) {
                // The execution path repairs a missing binder by requesting it
                // through ShizukuProvider; do the same before reporting Shizuku unavailable.
                ShizukuProvider.requestBinderForNonProviderProcess(app)
                binderAlive = Shizuku.pingBinder()
            }
            Log.d(
                "FreezeYouDebug",
                "AutoDiagnosis checkShizuku took "
                        + (SystemClock.elapsedRealtime() - startedAt)
                        + " ms (binderAlive=" + binderAlive + ")"
            )
            val authorized = binderAlive
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            val versionTooLow = binderAlive && Shizuku.isPreV11()
            when {
                versionTooLow ->
                    localList.add(
                        generateHashMap(
                            app.getString(R.string.shizukuVersionIsTooLow),
                            "",
                            "-8",
                            R.drawable.ic_warning
                        )
                    )
                authorized ->
                    localList.add(
                        generateHashMap(
                            app.getString(R.string.authorizationStatus_authorized),
                            "",
                            "-8",
                            R.drawable.ic_done
                        )
                    )
                binderAlive ->
                    localList.add(
                        generateHashMap(
                            app.getString(R.string.authorizationStatusNotAuthorized),
                            "",
                            "-8",
                            R.drawable.ic_warning
                        )
                    )
                else ->
                    localList.add(
                        generateHashMap(
                            app.getString(R.string.shizukuIsNotInstalledOrOtherExceptions),
                            "",
                            "-8",
                            R.drawable.ic_warning
                        )
                    )
            }
        } catch (e: Exception) {
            localList.add(
                generateHashMap(
                    app.getString(R.string.shizukuIsNotInstalledOrOtherExceptions),
                    "",
                    "-8",
                    R.drawable.ic_warning
                )
            )
        }
    }

    private fun checkIsIgnoringBatteryOptimizations(localList: MutableList<Map<String, Any>>) {
        if (Build.VERSION.SDK_INT >= 23
            && !(getApplication<Application>().getSystemService(POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
        ) {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.noIgnoringBO),
                    getApplication<Application>().getString(R.string.batteryOptimizationNotIgnoredSummary),
                    "4",
                    R.drawable.ic_warning
                )
            )
        } else {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.batteryOptimizationIgnored),
                    getApplication<Application>().getString(R.string.batteryOptimizationIgnoredSummary),
                    "4",
                    R.drawable.ic_done
                )
            )
        }
    }

    private fun checkIsPowerSaveMode(localList: MutableList<Map<String, Any>>) {
        if (Build.VERSION.SDK_INT >= 21
            && (getApplication<Application>().getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode
        ) {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.inPowerSaveMode),
                    getApplication<Application>().getString(R.string.powerSaveModeOnSummary),
                    "5",
                    R.drawable.ic_warning
                )
            )
        } else {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.powerSaveModeOff),
                    getApplication<Application>().getString(R.string.powerSaveModeOffSummary),
                    "5",
                    R.drawable.ic_done
                )
            )
        }
    }

    private fun checkBlueToothPermission(localList: MutableList<Map<String, Any>>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            && ActivityCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.BLUETOOTH_CONNECT
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.bluetoothPermissionIsNotGranted),
                    getApplication<Application>().getString(R.string.bluetoothPermissionNotGrantedSummary),
                    "7",
                    R.drawable.ic_warning
                )
            )
        } else {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.bluetoothPermissionGranted),
                    getApplication<Application>().getString(R.string.bluetoothPermissionGrantedSummary),
                    "7",
                    R.drawable.ic_done
                )
            )
        }
    }


    private fun checkIfNoProblemFound(localList: MutableList<Map<String, Any>>) {
        if (localList.isEmpty()) {
            localList.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.noProblemsFound),
                    getApplication<Application>().getString(R.string.everySeemsAllRight),
                    "-99",
                    R.drawable.ic_done
                )
            )
        }
    }

}
