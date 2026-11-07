package cf.playhi.freezeyou.ui.fragment.settings

import android.os.Build
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.ListPreference
import androidx.preference.Preference
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.shortcutClickAction
import cf.playhi.freezeyou.utils.SettingsUtils

@Keep
class SettingsFufFragment : CardPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.spr_fuf, rootKey)

        // The choice does not exist from Oreo on, where a background service cannot simply keep
        // running. The whole screen this setting used to live on was hidden there for the same
        // reason; now that it sits among the freeze settings, it is the row that goes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            preferenceScreen?.removePreferenceRecursively("useForegroundService")
        }

        setupShortcutPreferences()
    }

    private fun setupShortcutPreferences() {
        val shortcutActionPref = findPreference<ListPreference>("shortcutClickAction") ?: return
        val openImmediatelyPref = findPreference<Preference>("openImmediatelyAfterUnfreezeUseShortcutAutoFUF")
        val needConfirmPref = findPreference<Preference>("needConfirmWhenFreezeUseShortcutAutoFUF")

        // The same answer the shortcut itself acts on, so this screen cannot show one thing while
        // the tap does another. Storing it here is what settles the legacy booleans the row used to
        // read: from now on the single choice is the only copy that moves.
        val currentAction = SettingsUtils.resolveShortcutClickAction()
        shortcutClickAction.setValue(value = currentAction)
        shortcutActionPref.value = currentAction

        fun updateDependents(actionValue: String) {
            val isToggle = actionValue == "toggle"
            openImmediatelyPref?.isVisible = isToggle
            needConfirmPref?.isVisible = isToggle
        }

        updateDependents(currentAction)

        shortcutActionPref.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                if (newValue is String) {
                    shortcutClickAction.setValue(value = newValue)
                    updateDependents(newValue)
                }
                true
            }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.freezeAUF)
    }

}
