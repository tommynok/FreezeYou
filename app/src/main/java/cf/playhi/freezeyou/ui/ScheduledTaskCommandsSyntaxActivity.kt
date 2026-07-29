package cf.playhi.freezeyou.ui

import android.os.Bundle
import android.webkit.WebView
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.ThemeUtils.processActionBar
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme

class ScheduledTaskCommandsSyntaxActivity : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scheduled_task_commands_syntax_activity)
        processActionBar(supportActionBar)

        findViewById<WebView>(R.id.stcsa_webView).loadUrl(
            "file:///android_asset/help/scheduled_task_commands_" +
                    getString(R.string.offlineHelpAssetLanguageCode) + ".html"
        )
    }
}
