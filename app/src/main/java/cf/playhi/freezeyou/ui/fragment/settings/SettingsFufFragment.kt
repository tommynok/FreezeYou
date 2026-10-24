package cf.playhi.freezeyou.ui.fragment.settings

import android.os.Build
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.ListPreference
import androidx.preference.Preference
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.openAndUFImmediately
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.shortcutAutoFUF
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.shortcutClickAction

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

        // Sync initial state from MMKV: migrate from legacy booleans if unset, and persist resolved choice
        val savedAction = shortcutClickAction.getValue()
        val currentAction = if (!savedAction.isNullOrEmpty()) {
            savedAction
        } else {
            val resolved = when {
                shortcutAutoFUF.getValue() -> "toggle"
                openAndUFImmediately.getValue() -> "open_and_uf"
                else -> "dialog"
            }
            shortcutClickAction.setValue(value = resolved)
            resolved
        }
        shortcutActionPref.value = currentAction

        fun updateDependents(actionValue: String) {
            val isToggle = actionValue == "toggle"
            openImmediatelyPref?.isVisible = isToggle
            needConfirmPref?.isVisible = isToggle
        }

        updateDependents(currentAction)

        shortcutActionPref.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _, newValue ->
                val strValue = newValue as? String ?: "dialog"
                shortcutClickAction.setValue(value = strValue)
                when (strValue) {
                    "toggle" -> {
                        shortcutAutoFUF.setValue(value = true)
                        openAndUFImmediately.setValue(value = false)
                    }
                    "open_and_uf" -> {
                        shortcutAutoFUF.setValue(value = false)
                        openAndUFImmediately.setValue(value = true)
                    }
                    else -> { // "dialog"
                        shortcutAutoFUF.setValue(value = false)
                        openAndUFImmediately.setValue(value = false)
                    }
                }
                updateDependents(strValue)
                true
            }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.freezeAUF)
    }

}
