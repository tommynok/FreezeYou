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
import androidx.preference.PreferenceManager
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilder
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.enableAppLock
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.enableAuthentication
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.firstIconEnabled
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.secondIconEnabled
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.thirdIconEnabled
import cf.playhi.freezeyou.ui.AppLockActivity
import cf.playhi.freezeyou.utils.AccessibilityUtils.isAccessibilitySettingsOn
import cf.playhi.freezeyou.utils.AppCodeDialogs
import cf.playhi.freezeyou.utils.AppCodeUtils
import cf.playhi.freezeyou.utils.AuthenticationUtils.isAuthenticatorAvailable
import cf.playhi.freezeyou.utils.AuthenticationUtils.isBiometricPromptPartAvailable
import cf.playhi.freezeyou.utils.SettingsUtils.changeIconEntryComponentState
import cf.playhi.freezeyou.utils.Support.showAppLockAccessibilityWarningIfOff
import cf.playhi.freezeyou.utils.ToastUtils

/**
 * The lock and the ways into the application, which used to be a screen holding one checkbox and
 * a screen holding the launcher icons. This fragment is the two of them put together.
 */
@Keep
class SettingsAccessFragment : CardPreferenceFragment() {

    private var enableAuthenticationActivityResultLauncher: ActivityResultLauncher<Intent>? = null
    private var enableAppLockActivityResultLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activity: Activity? = activity
        if (activity != null) {
            enableAuthenticationActivityResultLauncher = registerForActivityResult(
                StartActivityForResult()
            ) { result: ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    setAuthenticationEnabled(true)
                }
            }
            enableAppLockActivityResultLauncher = registerForActivityResult(
                StartActivityForResult()
            ) { result: ActivityResult ->
                if (result.resultCode == Activity.RESULT_OK) {
                    enableAppLock.setValue(value = true)
                    PreferenceManager.getDefaultSharedPreferences(activity)
                        .edit()
                        .putBoolean(enableAppLock.name, true)
                        .apply()
                    findPreference<CheckBoxPreference>(enableAppLock.name)?.isChecked = true
                }
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.spr_access, rootKey)

        // The hidden checkbox feeds the XML dependencies of the lock rows; MMKV is what the rest
        // of the application reads, so the row state starts from there.
        findPreference<CheckBoxPreference>(enableAuthentication.name)?.isChecked =
            enableAuthentication.getValue()

        findPreference<Preference>("authenticationMethod")?.setOnPreferenceClickListener {
            showProtectionMethodDialog()
            true
        }

        // The app lock answers for other applications, so it asks the questions that row has:
        // whether a protection method is active, whether anything is there to intercept with,
        // and whether the device can be made to ask.
        findPreference<Preference>(enableAppLock.name)?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                val activity: Activity? = activity
                if (activity != null && true == newValue) {
                    if (!enableAuthentication.getValue()) {
                        ToastUtils.showToast(activity, R.string.chooseProtectionMethodFirst)
                    } else if (!isAccessibilitySettingsOn(activity)) {
                        showAppLockAccessibilityWarningIfOff(activity)
                    } else if (isAuthenticatorAvailable(activity)) {
                        enableAppLockActivityResultLauncher?.launch(
                            Intent(activity, AppLockActivity::class.java)
                                .putExtra("ignoreCurrentUnlockStatus", true)
                        )
                    } else {
                        ToastUtils.showToast(activity, R.string.authenticationFailed)
                    }
                    false
                } else {
                    true
                }
            }

        // An older installation could keep the App Lock on with no protection method chosen; the
        // upgrade must not leave it intercepting unprotected, nor the checkbox claiming it does.
        if (!enableAuthentication.getValue() && enableAppLock.getValue()) {
            setAppLockEnabled(false)
        }

        refreshProtectionSummary()

        bindIconEntry(firstIconEnabled.name, "cf.playhi.freezeyou.FirstIcon")
        bindIconEntry(secondIconEnabled.name, "cf.playhi.freezeyou.SecondIcon")
        bindIconEntry(thirdIconEnabled.name, "cf.playhi.freezeyou.ThirdIcon")
    }

    /**
     * One question instead of two switches: what protects the application. The answer is derived
     * from the keys the rest of the application already reads — protection off means None, a set
     * code means the code (it is what the lock screen asks first), otherwise the device lock.
     */
    private fun showProtectionMethodDialog() {
        val activity = activity ?: return
        FreezeYouAlertDialogBuilder(activity)
            .setTitle(R.string.authenticationMethod)
            .setItems(
                arrayOf(
                    getString(R.string.authenticationMethodNone),
                    getString(R.string.authenticationMethodDeviceLock),
                    getString(R.string.appCode)
                )
            ) { _, which ->
                when (which) {
                    0 -> setAuthenticationEnabled(false)
                    1 -> selectDeviceLock()
                    2 -> selectAppCode()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun selectDeviceLock() {
        val activity = activity ?: return
        // A set code would still be asked first, so it must be removed before the switch.
        if (AppCodeUtils.hasCode()) {
            ToastUtils.showToast(activity, R.string.removeAppCodeFirst)
            return
        }
        if (!isBiometricPromptPartAvailable(activity)) {
            ToastUtils.showToast(activity, R.string.deviceLockNotSet)
            return
        }
        // Turning the lock on is only allowed once it has been proved it can be opened again.
        enableAuthenticationActivityResultLauncher?.launch(
            Intent(activity, AppLockActivity::class.java)
                .putExtra("ignoreCurrentUnlockStatus", true)
        )
    }

    private fun selectAppCode() {
        val activity = activity ?: return
        AppCodeDialogs.showManageDialog(activity) {
            if (AppCodeUtils.hasCode()) {
                setAuthenticationEnabled(true)
            } else if (!isBiometricPromptPartAvailable(activity)) {
                // The code was the only thing to ask with; without it and without a device lock
                // the protection and the App Lock go off together.
                setAuthenticationEnabled(false)
            } else {
                refreshProtectionSummary()
            }
        }
    }

    private fun setAuthenticationEnabled(enabled: Boolean) {
        val context = context ?: return
        enableAuthentication.setValue(enabled)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(enableAuthentication.name, enabled)
            .apply()
        findPreference<CheckBoxPreference>(enableAuthentication.name)?.isChecked = enabled
        if (!enabled) {
            setAppLockEnabled(false)
        }
        refreshProtectionSummary()
    }

    private fun setAppLockEnabled(enabled: Boolean) {
        val context = context ?: return
        enableAppLock.setValue(enabled)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(enableAppLock.name, enabled)
            .apply()
        findPreference<CheckBoxPreference>(enableAppLock.name)?.isChecked = enabled
    }

    private fun refreshProtectionSummary() {
        val summary = when {
            !enableAuthentication.getValue() -> R.string.authenticationMethodNone
            AppCodeUtils.hasCode() -> R.string.appCode
            else -> R.string.authenticationMethodDeviceLock
        }
        findPreference<Preference>("authenticationMethod")?.setSummary(summary)
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
        refreshProtectionSummary()
    }

}
