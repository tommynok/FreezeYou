package cf.playhi.freezeyou.ui.fragment.settings

import android.os.Bundle
import androidx.annotation.Keep
import cf.playhi.freezeyou.ui.fragment.settings.CardPreferenceFragment
import cf.playhi.freezeyou.R

@Keep
class SettingsMainScreenFragment : CardPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.spr_main_screen, rootKey)
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.mainScreenSettings)
    }

}
