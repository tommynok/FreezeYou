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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            val currentActivity = activity
            val appContext = context?.applicationContext
            if (appContext != null) {
                // A long-used icon cache is a folder of many small files and the delete walks it
                // recursively, so it does not belong on the thread that paints this screen.
                CoroutineScope(Dispatchers.IO).launch {
                    val success = clearIconCache(appContext)
                    withContext(Dispatchers.Main) {
                        showToast(currentActivity, if (success) R.string.success else R.string.failed)
                    }
                }
            }
            true
        }
        findPreference<Preference?>("clearNameCache")?.setOnPreferenceClickListener {
            requireContext().getSharedPreferences("NameOfPackages", Context.MODE_PRIVATE)
                .edit().clear().apply()
            showToast(activity, R.string.success)
            true
        }
        findPreference<Preference?>("clearAllCache")?.setOnPreferenceClickListener {
            val currentActivity = activity
            val appContext = context?.applicationContext
            if (appContext != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        appContext.getSharedPreferences("NameOfPackages", Context.MODE_PRIVATE)
                            .edit().clear().apply()
                        deleteAllFiles(appContext.cacheDir, false)
                        deleteAllFiles(appContext.externalCacheDir, false)
                        deleteAllFiles(
                            File(appContext.filesDir.toString() + "/icon"),
                            false
                        )
                        withContext(Dispatchers.Main) {
                            showToast(currentActivity, R.string.success)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            showToast(currentActivity, R.string.failed)
                        }
                    }
                }
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
        val appContext = context?.applicationContext ?: return
        val currentActivity = activity
        CoroutineScope(Dispatchers.IO).launch {
            var success = removeUninstalledFromUserDefinedCategories(appContext)
            for (listKeyName in listOf(
                appContext.getString(R.string.sAutoFreezeApplicationList),
                appContext.getString(R.string.sOneKeyUFApplicationList),
                appContext.getString(R.string.sFreezeOnceQuit)
            )) {
                success = removeUninstalledFromOneKeyList(appContext, listKeyName) && success
            }
            success = purgeUninstalledPackageTimes(appContext) && success
            withContext(Dispatchers.Main) {
                showToast(currentActivity, if (success) R.string.success else R.string.failed)
            }
        }
    }

    private fun askIfResetTimes(dbName: String) {
        val activity: Activity? = activity
        val appContext = context?.applicationContext
        if (activity != null && appContext != null) {
            buildAlertDialog(
                activity, R.drawable.ic_warning, R.string.askIfDel, R.string.caution
            )
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        // resetTimes opens the counters database and rewrites every TimesList row;
                        // on a long-used list that is real disk work, not something to wait for here.
                        resetTimes(appContext, dbName)
                        withContext(Dispatchers.Main) {
                            showToast(activity, R.string.success)
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

}