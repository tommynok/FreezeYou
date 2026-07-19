package cf.playhi.freezeyou.ui

import android.os.Bundle
import android.widget.AdapterView
import android.widget.ListView
import android.widget.SimpleAdapter
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.AlertDialogUtils
import cf.playhi.freezeyou.utils.ApplicationLabelUtils
import cf.playhi.freezeyou.utils.NotificationUtils
import cf.playhi.freezeyou.utils.ThemeUtils.processActionBar
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import net.grandcentrix.tray.AppPreferences

class FUFNotificationsManageActivity : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fufnm_main)
        processActionBar(supportActionBar)
        init()
    }

    private fun init() {
        val fufnmListView = findViewById<ListView>(R.id.fufnm_listView)
        val defaultSharedPreferences = AppPreferences(this)
        val string = defaultSharedPreferences.getString("notifying", "")
        if (!string.isNullOrEmpty()) {
            val pkgList: MutableList<HashMap<String, String?>> = ArrayList()
            val strings = string.split(",").toTypedArray()
            for (aString in strings) {
                if (aString.isEmpty()) continue
                val hashMap = HashMap<String, String?>()
                hashMap["Name"] = ApplicationLabelUtils.getApplicationLabel(this, null, null, aString)
                hashMap["PkgName"] = aString
                pkgList.add(hashMap)
            }
            val adapter = SimpleAdapter(
                this, pkgList,
                R.layout.fufnm_list, arrayOf("Name", "PkgName"), intArrayOf(
                    R.id.fufnml_name,
                    R.id.fufnml_pkgName
                )
            )
            fufnmListView.adapter = adapter
            fufnmListView.onItemClickListener =
                AdapterView.OnItemClickListener { _, _, position, _ ->
                    val hashMap = adapter.getItem(position) as HashMap<String, String>
                    val pkgName = hashMap["PkgName"] ?: ""
                    AlertDialogUtils.buildAlertDialog(
                        this@FUFNotificationsManageActivity,
                        null,
                        (hashMap["Name"] + System.getProperty("line.separator") + pkgName),
                        getString(R.string.askIfDel)
                    )
                        .setNegativeButton(R.string.no, null)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            defaultSharedPreferences.put("notifying", string.replace("$pkgName,", ""))
                            NotificationUtils.deleteNotification(
                                this@FUFNotificationsManageActivity,
                                pkgName
                            )
                            init()
                        }
                        .create().show()
                }
        } else {
            val pkgList: MutableList<HashMap<String, String>> = ArrayList()
            val hashMap = HashMap<String, String>()
            hashMap["Name"] = getString(R.string.notAvailable)
            hashMap["PkgName"] = getString(R.string.notAvailable)
            pkgList.add(hashMap)
            val adapter = SimpleAdapter(
                this, pkgList,
                R.layout.fufnm_list, arrayOf("Name", "PkgName"), intArrayOf(
                    R.id.fufnml_name,
                    R.id.fufnml_pkgName
                )
            )
            fufnmListView.adapter = adapter
            fufnmListView.onItemClickListener = null
        }
    }
}
