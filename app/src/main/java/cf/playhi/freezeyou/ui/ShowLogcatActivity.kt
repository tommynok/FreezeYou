package cf.playhi.freezeyou.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.activity.viewModels
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.viewmodel.ShowLogcatViewModel
import cf.playhi.freezeyou.utils.LogSharingUtils
import cf.playhi.freezeyou.utils.ThemeUtils
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import cf.playhi.freezeyou.utils.ThemeUtils.processActionBar
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import cf.playhi.freezeyou.app.FreezeYouBaseActivity

class ShowLogcatActivity : FreezeYouBaseActivity() {

    private val viewModel: ShowLogcatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.showlogcat_activity)
        processActionBar(supportActionBar)

        val editText = findViewById<EditText>(R.id.sla_log_editText)

        editText.setText(R.string.loading___)

        viewModel.getLog().observe(this) { content ->
            editText.setText(content)
            editText.setSelection(editText.text.length)
        }

        viewModel.loadLog()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.showlogcat_menu, menu)
        val theme = ThemeUtils.getUiTheme(this)
        if ("white" == theme || "default" == theme) {
            menu.findItem(R.id.sla_menu_share)?.setIcon(R.drawable.ic_action_share_light)
        }
        return true
    }

    /**
     * Until this existed, getting a log off the device meant selecting all of it by hand in the
     * text field and hoping the system's own menu offered somewhere to put it. Sharing is the
     * whole reason this screen is opened.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.sla_menu_share -> {
                // What is on screen, not what the view model last loaded: the two are the same, and
                // this way what gets shared is what was looked at.
                val text = findViewById<EditText>(R.id.sla_log_editText).text?.toString()
                if (text.isNullOrBlank()) {
                    showToast(this, R.string.failed)
                } else {
                    LogSharingUtils.shareLog(
                        this,
                        getString(R.string.app_name) + " — " + getString(R.string.log),
                        text
                    )
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
