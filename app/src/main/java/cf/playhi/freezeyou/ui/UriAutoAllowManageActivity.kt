package cf.playhi.freezeyou.ui

import android.os.Bundle
import android.util.Base64
import android.widget.AdapterView
import android.widget.ListView
import android.widget.SimpleAdapter
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.AlertDialogUtils
import cf.playhi.freezeyou.utils.ApplicationLabelUtils
import cf.playhi.freezeyou.utils.MoreUtils
import cf.playhi.freezeyou.utils.ThemeUtils.processActionBar
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import net.grandcentrix.tray.AppPreferences
import java.io.File
import java.util.*

// Important!
// Also used to deal with ipa_autoAllow
class UriAutoAllowManageActivity : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.uaam_main)
        processActionBar(supportActionBar)
        init()
    }

    private fun init() {
        val uaamListView = findViewById<ListView>(R.id.uaam_listView)
        val ipaMode = intent.getBooleanExtra("isIpaMode", false) //Install Package
        if (ipaMode) setTitle(R.string.manageIpaAutoAllow)
        val defaultSharedPreferences = AppPreferences(this)
        val string = defaultSharedPreferences.getString(
            if (ipaMode) "installPkgs_autoAllowPkgs_allows" else "uriAutoAllowPkgs_allows",
            ""
        )
        val pkgList: MutableList<HashMap<String, String?>> = ArrayList()
        if (!string.isNullOrEmpty()) {
            val strings = string.split(",").toTypedArray()
            for (aString in strings) {
                if (aString.isEmpty()) continue
                val s = String(Base64.decode(aString, Base64.DEFAULT))
                val hashMap = HashMap<String, String?>()
                hashMap["Name"] = ApplicationLabelUtils.getApplicationLabel(this, null, null, s)
                hashMap["PkgName"] = s
                pkgList.add(hashMap)
            }
            val adapter = SimpleAdapter(
                this, pkgList,
                R.layout.uaam_list, arrayOf("Name", "PkgName"), intArrayOf(
                    R.id.uaaml_name,
                    R.id.uaaml_pkgName
                )
            )
            uaamListView.adapter = adapter
            uaamListView.onItemClickListener =
                AdapterView.OnItemClickListener { _: AdapterView<*>?, _: android.view.View?, position: Int, _: Long ->
                    val hashMap = adapter.getItem(position) as HashMap<String, String>
                    val pkgName = hashMap["PkgName"]
                    if (pkgName != null) {
                        AlertDialogUtils.buildAlertDialog(
                            this@UriAutoAllowManageActivity,
                            null,
                            (hashMap["Name"] + System.getProperty("line.separator") + pkgName),
                            getString(R.string.askIfDel)
                        )
                            .setNegativeButton(R.string.no, null)
                            .setPositiveButton(R.string.yes) { _, _ ->
                                val ls = MoreUtils.convertToList(strings)
                                ls.remove(Base64.encodeToString(pkgName.toByteArray(), Base64.DEFAULT))
                                defaultSharedPreferences.put(
                                    if (ipaMode) "installPkgs_autoAllowPkgs_allows" else "uriAutoAllowPkgs_allows",
                                    MoreUtils.listToString(ls, ",")
                                )
                                init()
                            }
                            .create().show()
                    }
                }
        } else {
            val hashMap = HashMap<String, String?>()
            hashMap["Name"] = getString(R.string.notAvailable)
            hashMap["PkgName"] = getString(R.string.notAvailable)
            pkgList.add(hashMap)
            val adapter = SimpleAdapter(
                this, pkgList,
                R.layout.uaam_list, arrayOf("Name", "PkgName"), intArrayOf(
                    R.id.uaaml_name,
                    R.id.uaaml_pkgName
                )
            )
            uaamListView.adapter = adapter
            uaamListView.onItemClickListener = null
        }
    }
}
