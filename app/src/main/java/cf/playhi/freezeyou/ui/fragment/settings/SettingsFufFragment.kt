package cf.playhi.freezeyou.ui.fragment.settings

import android.os.Build
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.PreferenceFragmentCompat
import cf.playhi.freezeyou.R

@Keep
class SettingsFufFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.spr_fuf, rootKey)

        // The choice does not exist from Oreo on, where a background service cannot simply keep
        // running. The whole screen this setting used to live on was hidden there for the same
        // reason; now that it sits among the freeze settings, it is the row that goes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            preferenceScreen?.removePreferenceRecursively("useForegroundService")
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.freezeAUF)
    }

}
