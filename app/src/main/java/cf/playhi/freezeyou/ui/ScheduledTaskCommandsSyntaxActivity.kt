package cf.playhi.freezeyou.ui

import android.os.Bundle
import android.widget.TextView
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

        findViewById<TextView>(R.id.stcsa_content_textView).text =
            getString(R.string.scheduledTaskCommandsSyntaxContent)
    }
}
