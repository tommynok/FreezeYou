package cf.playhi.freezeyou.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.ThemeUtils

class CommandExecutorActivity : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.processSetTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.cea_main)
        ThemeUtils.processActionBar(supportActionBar)
        init()
    }

    private fun init() {
        val printEditText = findViewById<EditText>(R.id.cea_main_print_editText)
        val inputEditText = findViewById<EditText>(R.id.cea_main_input_editText)
        val submitButton = findViewById<Button>(R.id.cea_main_finish_button)
    }
}
