package cf.playhi.freezeyou.ui.fragment;

import android.content.Intent;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.ui.ScheduledTaskCommandsSyntaxActivity;

public class STAAFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.stma_add_pr);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();
        if (key != null) {
            switch (key) {
                case "stma_add_help":
                    // The online guide repeated the syntax page shipped in assets; the
                    // offline copy answers the same question without leaving the app.
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
