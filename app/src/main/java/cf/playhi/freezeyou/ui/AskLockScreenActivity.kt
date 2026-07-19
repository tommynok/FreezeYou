package cf.playhi.freezeyou.ui

import android.os.Bundle
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.AlertDialogUtils
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils.doLockScreen
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme

class AskLockScreenActivity : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this, true)
        super.onCreate(savedInstanceState)
        AlertDialogUtils.buildAlertDialog(
            this,
            R.mipmap.ic_launcher_new_round,
            R.string.askIfLockScreen,
            R.string.notice
        )
            .setPositiveButton(R.string.yes) { _, _ ->
                doLockScreen(this@AskLockScreenActivity)
                finish()
            }
            .setNegativeButton(R.string.no) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create().show()
    }
}
