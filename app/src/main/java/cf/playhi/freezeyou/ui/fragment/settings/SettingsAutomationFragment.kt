package cf.playhi.freezeyou.ui.fragment.settings

import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.AlertDialogUtils.buildAlertDialog
import cf.playhi.freezeyou.utils.TasksUtils.deleteAllScheduledTasks

@Keep
class SettingsAutomationFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.spr_automation, rootKey)

        findPreference<Preference?>("deleteAllScheduledTasks")?.setOnPreferenceClickListener {
            buildAlertDialog(
                requireActivity(),
                R.drawable.ic_warning,
                R.string.askIfDel,
                R.string.caution
            )
                .setPositiveButton(R.string.yes) { _, _ ->
                    deleteAllScheduledTasks(requireContext())
                }
                .setNegativeButton(R.string.no, null)
                .show()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.automation)
    }

}