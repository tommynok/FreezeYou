package cf.playhi.freezeyou.ui.fragment

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.view.View
import android.widget.ListView
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.MoreUtils.requestOpenWebSite
import cf.playhi.freezeyou.utils.SettingsUtils.checkPreferenceData

class FirstTimeSetupFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.first_time_setup_pr)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val listView = view.findViewById<ListView>(android.R.id.list)
        if (listView != null) {
            listView.isVerticalScrollBarEnabled = false
            listView.overScrollMode = View.OVER_SCROLL_NEVER
        }
    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(requireActivity())
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(requireActivity())
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        checkPreferenceData(
            requireActivity().applicationContext,
            requireActivity(), sharedPreferences, key
        )
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        val key = preference.key
        if (key != null) {
            when (key) {
                "howToUse" -> requestOpenWebSite(
                    requireActivity(),
                    String.format(
                        "https://www.zidon.net/%1\$s/guide/how-to-use.html",
                        getString(R.string.correspondingAndAvailableWebsiteUrlLanguageCode)
                    )
                )
                else -> {}
            }
        }
        return super.onPreferenceTreeClick(preference)
    }
}
