package cf.playhi.freezeyou.ui.fragment.settings

import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.Preference
import cf.playhi.freezeyou.ui.fragment.settings.CardPreferenceFragment
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.AccessibilityUtils.openAccessibilitySettings

@Keep
class SettingsAdvanceFragment : CardPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.spr_advance, rootKey)

        findPreference<Preference?>("configureAccessibilityService")?.setOnPreferenceClickListener {
            openAccessibilitySettings(activity)
            true
        }

    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.advance)
    }

}