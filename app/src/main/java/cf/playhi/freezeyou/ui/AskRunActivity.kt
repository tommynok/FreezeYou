package cf.playhi.freezeyou.ui

import android.os.Bundle
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon
import cf.playhi.freezeyou.utils.ApplicationInfoUtils
import cf.playhi.freezeyou.utils.FUFUtils.checkAndStartApp
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import cf.playhi.freezeyou.utils.AlertDialogUtils.buildAlertDialog

class AskRunActivity : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this, true)
        super.onCreate(savedInstanceState)
        val pkgName = intent.getStringExtra("pkgName") ?: ""
        val target = intent.getStringExtra("target")
        val tasks = intent.getStringExtra("tasks")
        buildAlertDialog(
            this,
            getApplicationIcon(
                this,
                pkgName,
                ApplicationInfoUtils.getApplicationInfoFromPkgName(pkgName, this),
                true
            ),
            resources.getString(R.string.unfreezedAndAskLaunch),
            resources.getString(R.string.notice)
        )
            .setNegativeButton(R.string.no) { _, _ -> finish() }
            .setPositiveButton(R.string.yes) { _, _ ->
                checkAndStartApp(
                    this@AskRunActivity,
                    pkgName,
                    target,
                    tasks,
                    null,
                    false
                )
                finish()
            }
            .setOnCancelListener { finish() }
            .create()
            .show()
    }

    override fun onPause() {
        super.onPause()
        finish()
    }
}
