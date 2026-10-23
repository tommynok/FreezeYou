package cf.playhi.freezeyou.ui

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.allowFollowSystemAutoSwitchDarkMode
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.combineBatchQuickNotification
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.createQuickFUFNotiAfterUnfrozen
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.showInRecents
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.languagePref
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.themeOfAutoSwitchDarkMode
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageStringKeys.mainActivityPattern
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.uiStyleSelection
import cf.playhi.freezeyou.ui.fragment.settings.SettingsAutomationFragment
import cf.playhi.freezeyou.ui.fragment.settings.SettingsFragment
import cf.playhi.freezeyou.utils.NotificationUtils
import cf.playhi.freezeyou.utils.SettingsUtils.checkPreferenceData
import cf.playhi.freezeyou.utils.ThemeUtils.processActionBar
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import cf.playhi.freezeyou.utils.ThemeUtils.recreateAllActivities

class SettingsActivity : FreezeYouBaseActivity(),
    PreferenceFragmentCompat.OnPreferenceStartScreenCallback,
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    SharedPreferences.OnSharedPreferenceChangeListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        // Only on a first start. Changing the theme or the language calls recreate(), and the
        // fragments are restored with the activity — adding another root fragment on top of the
        // restored one left two preference lists drawn over each other.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
            // A sub-screen asked for by a notification (the screen-lock service points here so the
            // tap reaches the switch that controls it). The root is committed first and stays off
            // the back stack, so "back" from the sub-screen returns to the settings menu instead of
            // leaving the activity.
            //
            // The extra is matched against the few keys this app names itself and is never handed
            // to the fragment factory as a class name - the activity is exported. No
            // ARG_PREFERENCE_ROOT goes to the fragment either: res/xml/spr_automation.xml has no
            // group of that key, and asking for one would leave the screen looking for a subtree
            // that is not there. This is how res/xml/spr.xml opens the same screen from inside.
            val subScreenKey = intent?.getStringExtra(EXTRA_PREFERENCE_ROOT)
            val subScreen = when (subScreenKey) {
                PREFERENCE_ROOT_AUTOMATION -> SettingsAutomationFragment()
                else -> null
            }
            if (subScreen != null && subScreenKey != null) {
                supportFragmentManager.beginTransaction()
                    .replace(android.R.id.content, subScreen, subScreenKey)
                    .addToBackStack(subScreenKey)
                    .commit()
            }
        }
        processActionBar(supportActionBar)
    }

    override fun finish() {
        if (Build.VERSION.SDK_INT >= 21 && !showInRecents.getValue()) {
            finishAndRemoveTask()
        }
        super.finish()
    }

    override fun onPreferenceStartScreen(
        preferenceFragmentCompat: PreferenceFragmentCompat, preferenceScreen: PreferenceScreen
    ): Boolean {
        val ft = supportFragmentManager.beginTransaction()
        val fragment = SettingsFragment()
        val args = Bundle()
        args.putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, preferenceScreen.key)
        fragment.arguments = args
        ft.replace(android.R.id.content, fragment, preferenceScreen.key)
        ft.addToBackStack(preferenceScreen.key)
        ft.commit()
        return true
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val prefFragment = pref.fragment
        if (prefFragment != null) {
            val fragment = supportFragmentManager.fragmentFactory.instantiate(
                classLoader,
                prefFragment
            )
            fragment.arguments = args
            fragment.setTargetFragment(caller, 0)
            // Replace the existing Fragment with the new Fragment
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit()
            return true
        }
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            val fragmentManager = supportFragmentManager
            if (fragmentManager.backStackEntryCount == 0) {
                finish()
            } else {
                fragmentManager.popBackStack()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, s: String?) {
        checkPreferenceData(applicationContext, this, sharedPreferences, s)
        if (combineBatchQuickNotification.name == s || createQuickFUFNotiAfterUnfrozen.name == s) {
            NotificationUtils.reconcileQuickNotifications(applicationContext)
        }
        if (languagePref.name == s
            || uiStyleSelection.name == s
            || themeOfAutoSwitchDarkMode.name == s
            || allowFollowSystemAutoSwitchDarkMode.name == s
            || mainActivityPattern.name == s
        ) {
            // Recreate every live activity, not just this one: the rest of the back stack
            // used to keep the old theme/language until a full process restart.
            recreateAllActivities(this)
        }
    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        PreferenceManager.getDefaultSharedPreferences(applicationContext)
            .unregisterOnSharedPreferenceChangeListener(this)
    }

    companion object {
        /**
         * Key of the extra naming the sub-screen to open on top of the settings menu.
         */
        const val EXTRA_PREFERENCE_ROOT = "open_preference_root"

        /**
         * Key of the Automation screen in res/xml/spr.xml.
         */
        const val PREFERENCE_ROOT_AUTOMATION = "spr_automation"
    }
}
