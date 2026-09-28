package cf.playhi.freezeyou.ui.fragment;

import android.content.Intent;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.ui.ScheduledTaskCommandsSyntaxActivity;

public class STAATriggerFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.stma_add_trigger_pr);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();
        if (key != null) {
            switch (key) {
                case "stma_add_help":
                    // Same as the time tab: the assets copy replaces the online guide.
                    startActivity(
                            new Intent(requireContext(), ScheduledTaskCommandsSyntaxActivity.class));
                    break;
                default:
                    break;
            }
        }
        return super.onPreferenceTreeClick(preference);
    }

}
