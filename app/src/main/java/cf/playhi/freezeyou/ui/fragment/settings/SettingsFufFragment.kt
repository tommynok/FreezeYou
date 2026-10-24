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

        // Sync initial state from MMKV: migrate from legacy booleans if not set
        var currentAction = shortcutClickAction.getValue()
        if (currentAction.isNullOrEmpty() || currentAction == "dialog") {
            currentAction = when {
                shortcutAutoFUF.getValue() -> "toggle"
                openAndUFImmediately.getValue() -> "open_and_uf"
                else -> "dialog"
            }
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
                shortcutClickAction.setValue(strValue)
                when (strValue) {
                    "toggle" -> {
                        shortcutAutoFUF.setValue(true)
                        openAndUFImmediately.setValue(false)
                    }
                    "open_and_uf" -> {
                        shortcutAutoFUF.setValue(false)
                        openAndUFImmediately.setValue(true)
                    }
                    else -> { // "dialog"
                        shortcutAutoFUF.setValue(false)
                        openAndUFImmediately.setValue(false)
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
