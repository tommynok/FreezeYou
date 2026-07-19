package cf.playhi.freezeyou.ui.fragment

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.MoreUtils

class STAATriggerFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.stma_add_trigger_pr)
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val key = preference.key
        if (key != null) {
            when (key) {
                "stma_add_help" -> MoreUtils.requestOpenWebSite(
                    requireActivity(),
                    String.format(
                        "https://www.zidon.net/%1\$s/guide/schedules.html",
                        getString(R.string.correspondingAndAvailableWebsiteUrlLanguageCode)
                    )
                )
                else -> {}
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
}
