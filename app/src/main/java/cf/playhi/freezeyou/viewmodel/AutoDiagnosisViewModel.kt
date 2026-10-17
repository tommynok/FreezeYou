package cf.playhi.freezeyou.viewmodel

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.AccessibilityUtils.isAccessibilitySettingsOn
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils.isDeviceOwner
import cf.playhi.freezeyou.utils.RootUtils
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
                loadingProgress.postValue(-1)

                problemsList.value!!.clear()
                loadingProgress.postValue(5)

                checkSystemVersion()
                loadingProgress.postValue(15)

                checkAccessibilityService()
                loadingProgress.postValue(30)

                checkNotificationListenerPermission()
                loadingProgress.postValue(45)

                checkNotifyPermission()
                loadingProgress.postValue(55)

                checkBlueToothPermission()
                loadingProgress.postValue(65)

                checkIsDeviceOwner()
                loadingProgress.postValue(75)

                checkRootPermission()
                loadingProgress.postValue(85)

                checkIsPowerSaveMode()
                loadingProgress.postValue(92)

                checkIsIgnoringBatteryOptimizations()
                loadingProgress.postValue(97)

                checkIfNoProblemFound()
                loadingProgress.postValue(99)

                problemsList.value!!.sortWith { t0: Map<String, Any>, t1: Map<String, Any> ->
                    val i = (t0["level"] as Int).compareTo(
                        (t1["level"] as Int)
                    )
                    if (i == 0) (t0["id"] as String).compareTo((t1["id"] as String)) else i
                }

                loadingProgress.postValue(100)

                mutex.unlock("AutoDiagnosisViewModelRefreshingDiagnosisData")
            }
        }
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

    private fun checkSystemVersion() {
        if (Build.VERSION.SDK_INT < 21) {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.sysVerLow),
                    getApplication<Application>().getString(R.string.sysVerLowSummary),
                    "-50",
                    R.drawable.ic_warning
                )
            )
        } else {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.sysVerOk),
                    getApplication<Application>().getString(R.string.sysVerOkSummary),
                    "-1",
                    R.drawable.ic_done
                )
            )
        }
    }

    private fun checkAccessibilityService() {
        if (isAccessibilitySettingsOn(getApplication())) {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.accessibilityServiceEnabled),
                    getApplication<Application>().getString(R.string.accessibilityServiceEnabledSummary),
                    "1",
                    R.drawable.ic_done
                )
            )
        } else {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.ACBSNotEnabled),
                    getApplication<Application>().getString(R.string.accessibilityServiceDisabledSummary),
                    "1",
                    R.drawable.ic_warning
                )
            )
        }
    }

    private fun checkNotificationListenerPermission() {
        if (Build.VERSION.SDK_INT >= 21) {
            val s = Settings.Secure.getString(
                getApplication<Application>().contentResolver,
                "enabled_notification_listeners"
            )
            if (s == null
                || !s.contains("cf.playhi.freezeyou/cf.playhi.freezeyou.MyNotificationListenerService")
            ) {
                problemsList.value!!.add(
                    generateHashMap(
                        getApplication<Application>().getString(R.string.noNotificationListenerPermission),
                        getApplication<Application>().getString(R.string.notificationListenerPermissionNotGrantedSummary),
                        "2",
                        R.drawable.ic_warning
                    )
                )
            } else {
                problemsList.value!!.add(
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

    private fun checkNotifyPermission() {
        val notificationManager =
            getApplication<Application>().getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 24 && !notificationManager.areNotificationsEnabled()) {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.noNotifyPermission),
                    getApplication<Application>().getString(R.string.noNotifyPermissionSummary),
                    "6",
                    R.drawable.ic_warning
                )
            )
        } else {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.notifyPermissionGranted),
                    getApplication<Application>().getString(R.string.notifyPermissionGrantedSummary),
                    "6",
                    R.drawable.ic_done
                )
            )
        }
    }

    private fun checkIsDeviceOwner() {
        if (isDeviceOwner(getApplication())) {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.deviceOwnerActive),
                    getApplication<Application>().getString(R.string.deviceOwnerActiveSummary),
                    "-3",
                    R.drawable.ic_done
                )
            )
        } else {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.noMRootPermission),
                    getApplication<Application>().getString(R.string.deviceOwnerNotActiveSummary),
                    "-3",
                    R.drawable.ic_warning
                )
            )
        }
    }

    private fun checkRootPermission() {
        if (!RootUtils.checkRootPermission(true)) {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.noRootPermission),
                    getApplication<Application>().getString(R.string.rootPermissionNotGrantedSummary),
                    "-3",
                    R.drawable.ic_warning
                )
            )
        } else {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.rootPermissionGranted),
                    getApplication<Application>().getString(R.string.rootPermissionGrantedSummary),
                    "-3",
                    R.drawable.ic_done
                )
            )
        }
    }

    private fun checkIsIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= 23
            && !(getApplication<Application>().getSystemService(POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
        ) {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.noIgnoringBO),
                    getApplication<Application>().getString(R.string.batteryOptimizationNotIgnoredSummary),
                    "4",
                    R.drawable.ic_warning
                )
            )
        } else {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.batteryOptimizationIgnored),
                    getApplication<Application>().getString(R.string.batteryOptimizationIgnoredSummary),
                    "4",
                    R.drawable.ic_done
                )
            )
        }
    }

    private fun checkIsPowerSaveMode() {
        if (Build.VERSION.SDK_INT >= 21
            && (getApplication<Application>().getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode
        ) {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.inPowerSaveMode),
                    getApplication<Application>().getString(R.string.powerSaveModeOnSummary),
                    "5",
                    R.drawable.ic_warning
                )
            )
        } else {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.powerSaveModeOff),
                    getApplication<Application>().getString(R.string.powerSaveModeOffSummary),
                    "5",
                    R.drawable.ic_done
                )
            )
        }
    }

    private fun checkBlueToothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            && ActivityCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.BLUETOOTH_CONNECT
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.bluetoothPermissionIsNotGranted),
                    getApplication<Application>().getString(R.string.bluetoothPermissionNotGrantedSummary),
                    "7",
                    R.drawable.ic_warning
                )
            )
        } else {
            problemsList.value!!.add(
                generateHashMap(
                    getApplication<Application>().getString(R.string.bluetoothPermissionGranted),
                    getApplication<Application>().getString(R.string.bluetoothPermissionGrantedSummary),
                    "7",
                    R.drawable.ic_done
                )
            )
        }
    }


    private fun checkIfNoProblemFound() {
        if (problemsList.value!!.isEmpty()) {
            problemsList.value!!.add(
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
