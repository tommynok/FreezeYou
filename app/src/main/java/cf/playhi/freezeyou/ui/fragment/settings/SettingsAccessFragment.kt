package cf.playhi.freezeyou.ui.fragment.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.Keep
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.enableAuthentication
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.firstIconEnabled
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.secondIconEnabled
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.thirdIconEnabled
import cf.playhi.freezeyou.ui.AppLockActivity
import cf.playhi.freezeyou.utils.AuthenticationUtils.isBiometricPromptPartAvailable
import cf.playhi.freezeyou.utils.SettingsUtils.changeIconEntryComponentState

/**
 * The lock and the ways into the application, which used to be a screen holding one checkbox and
 * a screen holding the launcher icons. This fragment is the two of them put together.
 */
@Keep
class SettingsAccessFragment : PreferenceFragmentCompat() {

    private var enableAuthenticationActivityResultLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activity: Activity? = activity
        if (activity != null) {
            enableAuthenticationActivityResultLauncher = registerForActivityResult(
                StartActivityForResult()
            ) { result: ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    enableAuthentication.setValue(value = true)
                    PreferenceManager.getDefaultSharedPreferences(activity)
                        .edit()
                        .putBoolean(enableAuthentication.name, true)
                        .apply()
                    findPreference<CheckBoxPreference>(enableAuthentication.name)?.isChecked = true
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.spr_access, rootKey)

        // Turning the lock on is only allowed once it has been proved it can be opened again.
        val enableAuthenticationPreference: Preference? = findPreference(enableAuthentication.name)
        enableAuthenticationPreference?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val activity: Activity? = activity
                if (activity != null) {
                    if (true == newValue) {
                        if (isBiometricPromptPartAvailable(activity)) {
                            if (enableAuthenticationActivityResultLauncher != null) {
                                enableAuthenticationActivityResultLauncher
                                    ?.launch(
                                        Intent(activity, AppLockActivity::class.java)
                                            .putExtra(
                                                "ignoreCurrentUnlockStatus",
                                                true
                                            )
                                    )
                            }
                        }
                        return@OnPreferenceChangeListener false
                    } else {
                        return@OnPreferenceChangeListener true
                    }
                }
                true
            }

        bindIconEntry(firstIconEnabled.name, "cf.playhi.freezeyou.FirstIcon")
        bindIconEntry(secondIconEnabled.name, "cf.playhi.freezeyou.SecondIcon")
        bindIconEntry(thirdIconEnabled.name, "cf.playhi.freezeyou.ThirdIcon")
    }

    /** Each launcher icon is an activity-alias, shown or hidden by enabling its component. */
    private fun bindIconEntry(key: String, component: String) {
        findPreference<CheckBoxPreference>(key)?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue is Boolean) {
                changeIconEntryComponentState(requireContext(), newValue, component)
            }
            true
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.accessAndSecurity)
    }

}
