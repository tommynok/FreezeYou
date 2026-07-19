package cf.playhi.freezeyou.ui

import android.os.Bundle
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.AlertDialogUtils
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils
import cf.playhi.freezeyou.utils.ThemeUtils.processAddTranslucent
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import cf.playhi.freezeyou.utils.VersionUtils.checkUpdate

class UninstallActivity : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        processAddTranslucent(this)
        super.onCreate(savedInstanceState)
        AlertDialogUtils.buildAlertDialog(
            this, R.mipmap.ic_launcher_new_round,
            R.string.removeNoRootCaution, R.string.plsConfirm
        )
            .setPositiveButton(R.string.yes) { _, _ ->
                if (DevicePolicyManagerUtils.isDeviceOwner(applicationContext)) {
                    try {
                        DevicePolicyManagerUtils
                            .getDevicePolicyManager(
                                applicationContext
                            )
                            .clearDeviceOwnerApp("cf.playhi.freezeyou")
                        showToast(applicationContext, R.string.success)
                    } catch (e: Exception) {
                        showToast(applicationContext, R.string.failed)
                    }
                } else {
                    showToast(applicationContext, R.string.noRootNotActivated)
                }
                finish()
            }
            .setNegativeButton(R.string.no) { _, _ -> finish() }
            .setNeutralButton(R.string.update) { _, _ ->
                checkUpdate(this)
                finish()
            }
            .setOnCancelListener { finish() }
            .create()
            .show()
    }
}
