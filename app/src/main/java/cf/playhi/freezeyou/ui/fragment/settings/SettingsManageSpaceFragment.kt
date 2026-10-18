package cf.playhi.freezeyou.ui.fragment.settings

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.annotation.Keep
import androidx.preference.Preference
import cf.playhi.freezeyou.ui.fragment.settings.CardPreferenceFragment
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.AlertDialogUtils.buildAlertDialog
import cf.playhi.freezeyou.utils.DataStatisticsUtils.purgeUninstalledPackageTimes
import cf.playhi.freezeyou.utils.DataStatisticsUtils.resetTimes
import cf.playhi.freezeyou.utils.FileUtils.clearIconCache
import cf.playhi.freezeyou.utils.FileUtils.deleteAllFiles
import cf.playhi.freezeyou.utils.OneKeyListUtils.removeUninstalledFromOneKeyList
import cf.playhi.freezeyou.utils.OneKeyListUtils.removeUninstalledFromUserDefinedCategories
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import java.io.File

@Keep
class SettingsManageSpaceFragment : CardPreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.spr_manage_space, rootKey)

        findPreference<Preference?>("clearUninstalledPkgsInAllLists")?.setOnPreferenceClickListener {
            clearUninstalledPkgsInAllLists()
            true
        }
        findPreference<Preference?>("clearIconCache")?.setOnPreferenceClickListener {
            showToast(
                activity,
                if (clearIconCache(activity)) R.string.success else R.string.failed
            )
            true
        }
        findPreference<Preference?>("clearNameCache")?.setOnPreferenceClickListener {
            requireContext().getSharedPreferences("NameOfPackages", Context.MODE_PRIVATE)
                .edit().clear().apply()
            showToast(activity, R.string.success)
            true
        }
        findPreference<Preference?>("clearAllCache")?.setOnPreferenceClickListener {
            try {
                requireContext().getSharedPreferences("NameOfPackages", Context.MODE_PRIVATE)
                    .edit().clear().apply()
                deleteAllFiles(requireContext().cacheDir, false)
                deleteAllFiles(requireContext().externalCacheDir, false)
                deleteAllFiles(
                    File(requireContext().filesDir.toString() + "/icon"),
                    false
                )
                showToast(activity, R.string.success)
            } catch (e: Exception) {
                e.printStackTrace()
                showToast(activity, R.string.failed)
            }
            true
        }
        findPreference<Preference?>("resetFreezeTimes")?.setOnPreferenceClickListener {
            askIfResetTimes("ApplicationsFreezeTimes")
            true
        }
        findPreference<Preference?>("resetUFTimes")?.setOnPreferenceClickListener {
            askIfResetTimes("ApplicationsUFreezeTimes")
            true
        }
        findPreference<Preference?>("resetUseTimes")?.setOnPreferenceClickListener {
            askIfResetTimes("ApplicationsUseTimes")
            true
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.manageSpace)
    }

    /**
     * One press cleans every list that keeps package names: the three stored OneKEY lists, the
     * custom categories in the database beside them, and the freeze and use counters. Each holds
     * its own copy of the same names, so a per-list button left the uninstalled app in all the
     * others.
     */
    private fun clearUninstalledPkgsInAllLists() {
        var success = removeUninstalledFromUserDefinedCategories(requireContext())
        for (listKeyName in listOf(
            getString(R.string.sAutoFreezeApplicationList),
            getString(R.string.sOneKeyUFApplicationList),
            getString(R.string.sFreezeOnceQuit)
        )) {
            success = removeUninstalledFromOneKeyList(requireContext(), listKeyName) && success
        }
        success = purgeUninstalledPackageTimes(requireContext()) && success
        showToast(activity, if (success) R.string.success else R.string.failed)
    }

    private fun askIfResetTimes(dbName: String) {
        val activity: Activity? = activity
        if (activity != null) {
            buildAlertDialog(
                activity, R.drawable.ic_warning, R.string.askIfDel, R.string.caution
            )
                .setPositiveButton(R.string.yes) { _, _ ->
                    resetTimes(activity, dbName)
                }
                .setNegativeButton(R.string.no, null)
                .show()
        }
    }

}