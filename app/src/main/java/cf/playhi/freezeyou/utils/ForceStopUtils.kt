package cf.playhi.freezeyou.utils

import android.content.Context
import cf.playhi.freezeyou.MainApplication
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.avoidFreezeForegroundApplications
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.lesserToast
import cf.playhi.freezeyou.utils.ProcessUtils.destroyProcess
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import java.io.DataOutputStream

object ForceStopUtils {
    @JvmStatic
    fun forceStop(
        context: Context, pkgNameList: Array<String>?
    ) {
        if (pkgNameList != null) {
            var currentPackage = " "
            if (avoidFreezeForegroundApplications.getValue(null)) {
                currentPackage = MainApplication.currentPackage ?: " "
            }
            var process: Process? = null
            var outputStream: DataOutputStream? = null
            try {
                process = Runtime.getRuntime().exec("su")
                outputStream = DataOutputStream(process.outputStream)
                for (aPkgNameList in pkgNameList) {
                    if ("cf.playhi.freezeyou" != aPkgNameList) {
                        if (FUFUtils.isAvoidFreezeNotifyingApplicationsEnabledAndAppStillNotifying(
                                aPkgNameList
                            )
                        ) {
                            FUFUtils.checkAndShowAppStillNotifyingToast(context, aPkgNameList)
                        } else if (currentPackage == aPkgNameList) {
                            FUFUtils.checkAndShowAppIsForegroundApplicationToast(
                                context,
                                aPkgNameList
                            )
                        } else {
                            try {
                                outputStream.writeBytes("am force-stop $aPkgNameList\n")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                outputStream.writeBytes("exit\n")
                outputStream.flush()
                val exitValue = process.waitFor()
                if (exitValue == 0) {
                    if (!lesserToast.getValue(null)) {
                        showToast(context, R.string.executed)
                    }
                } else {
                    showToast(context, R.string.mayUnrootedOrOtherEx)
                }
                destroyProcess(outputStream, process)
            } catch (e: Exception) {
                e.printStackTrace()
                showToast(context, context.getString(R.string.exception) + e.message)
                if (e.message?.lowercase()?.contains("permission denied") == true || e.message?.lowercase()
                        ?.contains("not found") == true
                ) {
                    showToast(context, R.string.mayUnrooted)
                }
                destroyProcess(outputStream, process)
            }
        }
    }
}
