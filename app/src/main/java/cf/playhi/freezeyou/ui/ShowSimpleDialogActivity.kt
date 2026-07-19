package cf.playhi.freezeyou.ui

import android.os.Bundle
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.AlertDialogUtils
import cf.playhi.freezeyou.utils.ClipboardUtils
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import cf.playhi.freezeyou.utils.ToastUtils

class ShowSimpleDialogActivity : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this, true)
        super.onCreate(savedInstanceState)
        init()
    }

    private fun init() {
        val intent = intent
        if (intent == null) {
            finish()
            return
        }
        val title = intent.getStringExtra("title")
        val text = intent.getStringExtra("text") ?: ""
        AlertDialogUtils
            .buildAlertDialog(this, null, text, title)
            .setPositiveButton(R.string.okay) { _, _ -> finish() }
            .setNeutralButton(android.R.string.copy) { _, _ ->
                if (ClipboardUtils.copyToClipboard(this@ShowSimpleDialogActivity, text)) {
                    ToastUtils.showToast(this@ShowSimpleDialogActivity, R.string.success)
                } else {
                    ToastUtils.showToast(this@ShowSimpleDialogActivity, R.string.failed)
                }
                finish()
            }
            .setOnCancelListener { finish() }
            .create()
            .show()
    }

    override fun activityNeedCheckAppLock(): Boolean {
        return false
    }
}
