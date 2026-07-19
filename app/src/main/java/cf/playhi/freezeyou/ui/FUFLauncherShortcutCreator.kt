package cf.playhi.freezeyou.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.View
import android.widget.*
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.adapter.ReplaceableSimpleAdapter
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon
import cf.playhi.freezeyou.utils.ApplicationLabelUtils.getApplicationLabel
import cf.playhi.freezeyou.utils.FUFUtils.realGetFrozenStatus
import cf.playhi.freezeyou.utils.MoreUtils.processListFilter
import cf.playhi.freezeyou.utils.OneKeyListUtils.existsInOneKeyList
import cf.playhi.freezeyou.utils.ThemeUtils
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import java.util.*

class FUFLauncherShortcutCreator : FreezeYouBaseActivity() {
    private var customThemeDisabledDot = R.drawable.shapedotblue
    private var customThemeEnabledDot = R.drawable.shapedotblack
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.processSetTheme(this)
        super.onCreate(savedInstanceState)
        ThemeUtils.processActionBar(supportActionBar)
        val intent = intent
        val slf_n = intent.getStringExtra("slf_n")
        val returnPkgName = intent.getBooleanExtra("returnPkgName", false)
        val isSlfMode = slf_n != null
        try {
            customThemeDisabledDot = ThemeUtils.getThemeDot(this)
            customThemeEnabledDot = ThemeUtils.getThemeSecondDot(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (isSlfMode || returnPkgName) {
            setContentView(R.layout.fuflsc_select_application)
            if (isSlfMode) setTitle(R.string.add) else setTitle(R.string.plsSelect)
            Thread {
                val app_listView = findViewById<ListView>(R.id.fuflsc_app_list)
                val progressBar = findViewById<ProgressBar>(R.id.fuflsc_progressBar)
                val linearLayout = findViewById<LinearLayout>(R.id.fuflsc_linearLayout)
                val AppList = ArrayList<MutableMap<String, Any?>>()
                val search_editText = findViewById<EditText>(R.id.fuflsc_search_editText)
                runOnUiThread {
                    linearLayout.visibility = View.VISIBLE
                    progressBar.visibility = View.VISIBLE
                    app_listView.visibility = View.GONE
                }
                app_listView.choiceMode = AbsListView.CHOICE_MODE_NONE
                val applicationContext = applicationContext
                val packageManager = applicationContext.packageManager
                val applicationInfo =
                    packageManager.getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES)
                val size = applicationInfo.size
                for (i in 0 until size) {
                    val applicationInfo1 = applicationInfo[i]
                    val keyValuePair = processAppStatus(
                        getApplicationLabel(
                            applicationContext,
                            packageManager,
                            applicationInfo1,
                            applicationInfo1.packageName
                        ),
                        applicationInfo1.packageName,
                        applicationInfo1,
                        packageManager
                    )
                    if (keyValuePair != null) {
                        AppList.add(keyValuePair)
                    }
                }
                if (AppList.isNotEmpty()) {
                    Collections.sort(AppList) { stringObjectMap: MutableMap<String, Any?>, t1: MutableMap<String, Any?> ->
                        (stringObjectMap["PackageName"] as String).compareTo(
                            (t1["PackageName"] as String)
                        )
                    }
                }
                val adapter = ReplaceableSimpleAdapter(
                    this@FUFLauncherShortcutCreator,
                    AppList.clone() as ArrayList<MutableMap<String, Any?>>,
                    R.layout.app_list_1,
                    arrayOf("Img", "Name", "PackageName", "isFrozen"),
                    intArrayOf(R.id.img, R.id.name, R.id.pkgName, R.id.isFrozen)
                ) //isFrozen、isAutoList传图像资源id
                adapter.setViewBinder { view: View?, data: Any?, _: String? ->
                    if (view is ImageView && data is Drawable) {
                        view.setImageDrawable(data)
                        true
                    } else false
                }
                search_editText.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(
                        charSequence: CharSequence,
                        i: Int,
                        i1: Int,
                        i2: Int
                    ) {
                    }

                    override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                        if (TextUtils.isEmpty(charSequence)) {
                            adapter.replaceAllInFormerArrayList(AppList)
                        } else {
                        adapter.replaceAllInFormerArrayList(
                            processListFilter(
                                charSequence,
                                AppList as ArrayList<Map<String, Any?>>
                            ) as List<MutableMap<String, Any?>>
                        )
                    }
                    }

                    override fun afterTextChanged(editable: Editable) {}
                })
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    linearLayout.visibility = View.GONE
                    app_listView.adapter = adapter
                    app_listView.isTextFilterEnabled = true
                    app_listView.visibility = View.VISIBLE
                }
                app_listView.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, i: Int, _: Long ->
                        val map = app_listView.getItemAtPosition(i) as Map<String, Any?>
                        val name = map["Name"] as String?
                        val pkgName = map["PackageName"] as String?
                        if (isSlfMode) {
                            val sp = getSharedPreferences(
                                intent.getStringExtra("slf_n"),
                                MODE_PRIVATE
                            )
                            if (!existsInOneKeyList(sp.getString("pkgS", ""), pkgName)) {
                                sp.edit().putString("pkgS", sp.getString("pkgS", "") + pkgName + ",")
                                    .apply()
                                showToast(this@FUFLauncherShortcutCreator, R.string.added)
                            } else {
                                showToast(this@FUFLauncherShortcutCreator, R.string.alreadyExist)
                            }
                            setResult(RESULT_OK)
                        } else { // if (returnPkgName)s
                            setResult(
                                RESULT_OK, Intent()
                                    .putExtra("pkgName", pkgName)
                                    .putExtra("name", name)
                                    .putExtra("id", "FreezeYou! $pkgName")
                            )
                            finish()
                        }
                    }
            }.start()
        } else {
            finish()
        }
    }

    private fun processAppStatus(
        name: String,
        packageName: String,
        applicationInfo: ApplicationInfo,
        packageManager: PackageManager
    ): MutableMap<String, Any?>? {
        if (!("android" == packageName || "cf.playhi.freezeyou" == packageName)) {
            val keyValuePair: MutableMap<String, Any?> = HashMap()
            keyValuePair["Img"] =
                getApplicationIcon(this@FUFLauncherShortcutCreator, packageName, applicationInfo, true)
            keyValuePair["Name"] = name
            processFrozenStatus(keyValuePair, packageName, packageManager)
            keyValuePair["PackageName"] = packageName
            return keyValuePair
        }
        return null
    }

    private fun processFrozenStatus(
        keyValuePair: MutableMap<String, Any?>,
        packageName: String,
        packageManager: PackageManager
    ) {
        keyValuePair["isFrozen"] = getFrozenStatus(packageName, packageManager)
    }

    /**
     * @param packageName 应用包名
     * @return 资源 Id
     */
    private fun getFrozenStatus(packageName: String, packageManager: PackageManager): Int {
        return if (realGetFrozenStatus(
                this@FUFLauncherShortcutCreator,
                packageName,
                packageManager
            )
        ) customThemeDisabledDot else customThemeEnabledDot
    }
}
