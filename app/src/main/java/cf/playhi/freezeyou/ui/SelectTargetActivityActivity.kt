package cf.playhi.freezeyou.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.ListView
import android.widget.SimpleAdapter
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable
import cf.playhi.freezeyou.utils.ApplicationInfoUtils.getApplicationInfoFromPkgName
import cf.playhi.freezeyou.utils.ApplicationLabelUtils.getApplicationLabel
import cf.playhi.freezeyou.utils.ThemeUtils.processActionBar
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import java.util.*

class SelectTargetActivityActivity : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        processActionBar(supportActionBar)
        setContentView(R.layout.staa_main)
        init()
    }

    private fun init() {
        val arrayList = ArrayList<MutableMap<String, Any?>>()
        val intent = intent
        if (intent == null) {
            finish()
        } else {
            val pkgName = intent.getStringExtra("pkgName")
            if (pkgName == null) {
                finish()
            } else {
                val hm: MutableMap<String, Any?> = HashMap()
                hm["Img"] = getApplicationIcon(
                    this,
                    pkgName,
                    getApplicationInfoFromPkgName(pkgName, this),
                    false
                )
                hm["Name"] = getString(R.string.launch)
                hm["Label"] = getApplicationLabel(
                    this, packageManager,
                    getApplicationInfoFromPkgName(pkgName, this), pkgName
                )
                arrayList.add(hm)
                val hm2: MutableMap<String, Any?> = HashMap()
                hm2["Img"] = getApplicationIcon(
                    this,
                    pkgName,
                    getApplicationInfoFromPkgName(pkgName, this),
                    false
                )
                hm2["Name"] = getString(R.string.onlyUnfreeze)
                hm2["Label"] = getApplicationLabel(
                    this, packageManager,
                    getApplicationInfoFromPkgName(pkgName, this), pkgName
                )
                arrayList.add(hm2)
                try {
                    val pm = packageManager
                    val activityInfos =
                        pm.getPackageInfo(pkgName, PackageManager.GET_ACTIVITIES).activities
                    if (activityInfos != null) {
                        for (activityInfo in activityInfos) {
                            val ais = activityInfo.name
                            if (ais != null && activityInfo.exported) {
                                val hashMap: MutableMap<String, Any?> = HashMap()
                                hashMap["Img"] = activityInfo.loadIcon(pm)
                                hashMap["Name"] = ais
                                hashMap["Label"] = activityInfo.loadLabel(pm).toString()
                                arrayList.add(hashMap)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val adapter = SimpleAdapter(
                    this@SelectTargetActivityActivity,
                    arrayList,
                    R.layout.staa_main_item,
                    arrayOf("Img", "Label", "Name"),
                    intArrayOf(
                        R.id.staa_main_item_imageView,
                        R.id.staa_main_item_textView,
                        R.id.staa_main_item_subtitle_textView
                    )
                )
                adapter.setViewBinder { view: View?, data: Any?, _: String? ->
                    if (view is ImageView && data is Drawable) {
                        view.setImageDrawable(data)
                        true
                    } else {
                        false
                    }
                }
                val staaMainListView = findViewById<ListView>(R.id.staa_main_listView)
                staaMainListView.adapter = adapter
                staaMainListView.onItemClickListener =
                    AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                        val name = arrayList[position]["Name"] as String?
                        val label = arrayList[position]["Label"] as String?
                        val drawable = arrayList[position]["Img"] as Drawable?
                        val icon = if (drawable == null) null else getBitmapFromDrawable(drawable)
                        setResult(
                            RESULT_OK,
                            Intent()
                                .putExtra("name", name)
                                .putExtra("icon", icon)
                                .putExtra("label", label)
                                .putExtra("id", "FreezeYou!$pkgName $name")
                        )
                        finish()
                    }
            }
        }
    }
}
