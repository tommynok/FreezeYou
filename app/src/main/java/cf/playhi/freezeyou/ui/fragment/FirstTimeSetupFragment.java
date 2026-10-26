package cf.playhi.freezeyou.ui.fragment;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import cf.playhi.freezeyou.R;

import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.allowFollowSystemAutoSwitchDarkMode;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.themeOfAutoSwitchDarkMode;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.uiStyleSelection;
import static cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageStringKeys.mainActivityPattern;
import static cf.playhi.freezeyou.utils.MoreUtils.requestOpenWebSite;
import static cf.playhi.freezeyou.utils.SettingsUtils.checkPreferenceData;
import static cf.playhi.freezeyou.utils.ThemeUtils.recreateAllActivities;

public class FirstTimeSetupFragment extends PreferenceFragmentCompat implements
        SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.first_time_setup_pr);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ListView listView = view.findViewById(android.R.id.list);
        if (listView != null) {
            listView.setVerticalScrollBarEnabled(false);
            listView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(getActivity()).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(getActivity()).unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        checkPreferenceData(
                getActivity().getApplicationContext(),
                getActivity(), sharedPreferences, key
        );
        if (uiStyleSelection.name().equals(key)
                || themeOfAutoSwitchDarkMode.name().equals(key)
                || allowFollowSystemAutoSwitchDarkMode.name().equals(key)
                || mainActivityPattern.name().equals(key)) {
            // Writing the choice to storage is not enough: this screen and Main behind it keep the
            // old theme until recreated. The dark-mode keys are not on this screen but they decide
            // the same theme, so a restore-in-progress change applies here too.
            if (getActivity() != null) {
                recreateAllActivities(getActivity());
            }
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();
        if (key != null) {
            switch (key) {
                case "howToUse":
                    requestOpenWebSite(getActivity(),
                            String.format("https://www.zidon.net/%1$s/guide/how-to-use.html",
                                    getString(R.string.correspondingAndAvailableWebsiteUrlLanguageCode)));
                    break;
                default:
                    break;
            }
        }
        return super.onPreferenceTreeClick(preference);
    }

}
