package cf.playhi.freezeyou.ui

import android.os.Bundle
import android.widget.Button
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.ui.fragment.FirstTimeSetupFragment
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme

class FirstTimeSetupActivity : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.first_time_setup_main)
        val actionBar = supportActionBar
        actionBar?.hide()
        init()
    }

    private fun init() {
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.first_time_setup_main_frameLayout, FirstTimeSetupFragment())
            .commit()
        val firstTimeSetupMainNextButton =
            findViewById<Button>(R.id.first_time_setup_main_next_button)
        firstTimeSetupMainNextButton.setOnClickListener { finish() }
    }
}
