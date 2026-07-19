package cf.playhi.freezeyou.ui

import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.adapter.ReplaceableSimpleAdapter
import cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilder
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.*
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getGrayBitmap
import cf.playhi.freezeyou.utils.ApplicationLabelUtils.getApplicationLabel
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import java.util.*

open class ShortcutLauncherFolderActivity : FreezeYouBaseActivity(), OnSharedPreferenceChangeListener {
    private var dialog: AlertDialog? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this, Intent.ACTION_CREATE_SHORTCUT == intent.action)
        super.onCreate(savedInstanceState)
        val uuid = intent.getStringExtra("UUID")
        if (uuid != null) {
            val uuidSp = getSharedPreferences(uuid, MODE_PRIVATE)
            uuidSp.registerOnSharedPreferenceChangeListener(this)
        }
        if (Intent.ACTION_CREATE_SHORTCUT == intent.action) {
            doCreateShortCut()
        } else {
            doShowFolder()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        var uuid = getIntent().getStringExtra("UUID")
        if (uuid != null) {
            val uuidSp = getSharedPreferences(uuid, MODE_PRIVATE)
            uuidSp.unregisterOnSharedPreferenceChangeListener(this)
        }
        setIntent(intent)
        uuid = getIntent().getStringExtra("UUID")
        if (uuid != null) {
            val uuidSp = getSharedPreferences(uuid, MODE_PRIVATE)
            uuidSp.registerOnSharedPreferenceChangeListener(this)
        }
        if (Intent.ACTION_CREATE_SHORTCUT == intent.action) {
            doCreateShortCut()
        } else {
            doShowFolderContent()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 6 && resultCode == RESULT_OK && data != null && dialog != null) {
            val bm = data.getParcelableExtra<Bitmap>("Icon")
            val w = dialog!!.window
            if (w != null && bm != null) {
                val imageButton =
                    w.findViewById<ImageButton>(R.id.slfa_confirm_icon_name_dialog_imageButton)
                if (imageButton != null) {
                    imageButton.setImageDrawable(BitmapDrawable(resources, bm))
                }
            }
        }
    }

    override fun onDestroy() {
        val uuid = intent.getStringExtra("UUID")
        if (uuid != null) {
            val uuidSp = getSharedPreferences(uuid, MODE_PRIVATE)
            uuidSp.unregisterOnSharedPreferenceChangeListener(this)
        }
        super.onDestroy()
    }

    private fun doCreateShortCut() {
        val ab = FreezeYouAlertDialogBuilder(this)
        val confirmIconNameDialogView = View.inflate(this, R.layout.slfa_confirm_icon_name_dialog, null)
        val editText =
            confirmIconNameDialogView.findViewById<EditText>(R.id.slfa_confirm_icon_name_dialog_editText)
        val imageButton =
            confirmIconNameDialogView.findViewById<ImageButton>(R.id.slfa_confirm_icon_name_dialog_imageButton)
        imageButton.setOnClickListener {
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(this@ShortcutLauncherFolderActivity, SelectShortcutIconActivity::class.java),
                6
            )
        }
        ab.setView(confirmIconNameDialogView)
        ab.setOnCancelListener {
            setResult(RESULT_CANCELED)
            finish()
        }
        ab.setPositiveButton(R.string.finish) { _, _ ->
            val name = editText.text.toString()
            val uuid = "Folder_" + name.hashCode() + "_" + Date().time
            val intent = Intent()
            intent.putExtra(
                Intent.EXTRA_SHORTCUT_INTENT,
                Intent(this@ShortcutLauncherFolderActivity, ShortcutLauncherFolderActivity::class.java)
                    .putExtra("UUID", uuid)
            )
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name)
            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, getBitmapFromDrawable(imageButton.drawable))
            OneKeyListUtils.addToOneKeyList(this@ShortcutLauncherFolderActivity, "FolderUUIDs", uuid)
            setResult(RESULT_OK, intent)
            finish()
        }
        ab.setNegativeButton(R.string.cancel) { _, _ ->
            setResult(RESULT_CANCELED)
            finish()
        }
        val alertDialog = ab.show()
        dialog = alertDialog
        val w = alertDialog.window
        if (w != null) {
            val v = w.findViewById<View>(android.R.id.custom)
            if (v != null) {
                val p = v.parent as View?
                p?.minimumHeight = 0
            }
        }
    }

    private fun doShowFolder() {
        @Suppress("DEPRECATION")
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        setContentView(R.layout.shortcut_launcher_folder)
        doShowFolderContent()
    }

    private fun doShowFolderContent() {
        val uuid = intent.getStringExtra("UUID")
        if (uuid == null) {
            showToast(this, R.string.failed)
            finish()
            return
        }
        val slfAppsGridView = findViewById<GridView>(R.id.slf_apps_gridView)
        val slfFolderNameTextView = findViewById<TextView>(R.id.slf_folderName_textView)
        slfAppsGridView.columnWidth =
            (resources.getDimension(android.R.dimen.app_icon_size) * 1.8).toInt()
        val folderItems = ArrayList<MutableMap<String, Any?>>()
        val uuidSp = getSharedPreferences(uuid, MODE_PRIVATE)
        slfFolderNameTextView.text = uuidSp.getString("folderName", getString(R.string.folder))
        generateFolderItems(folderItems, uuidSp)
        val replaceableSimpleAdapter = ReplaceableSimpleAdapter(
            this, folderItems.clone() as ArrayList<MutableMap<String, Any?>>,
            R.layout.shortcut_launcher_folder_item, arrayOf("Icon", "Label"),
            intArrayOf(R.id.slfi_imageView, R.id.slfi_textView)
        )
        replaceableSimpleAdapter.setViewBinder { view: View?, data: Any?, _: String? ->
            if (view is ImageView && data is Drawable) {
                view.setImageDrawable(data)
                true
            } else false
        }
        slfAppsGridView.adapter = replaceableSimpleAdapter
        slfAppsGridView.onItemClickListener =
            AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                val pkg =
                    (slfAppsGridView.adapter as ReplaceableSimpleAdapter).getStoredArrayList()[position]["Package"] as String?
                if ("freezeyou@add" == pkg) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(
                        Intent(
                            this@ShortcutLauncherFolderActivity,
                            FUFLauncherShortcutCreator::class.java
                        )
                            .putExtra("slf_n", uuid),
                        7001
                    )
                } else {
                    FUFUtils.checkFrozenStatusAndStartApp(
                        this@ShortcutLauncherFolderActivity,
                        pkg ?: "",
                        null,
                        null
                    )
                }
            }
        slfAppsGridView.onItemLongClickListener =
            AdapterView.OnItemLongClickListener { _: AdapterView<*>?, view: View?, position: Int, _: Long ->
                val hm =
                    (slfAppsGridView.adapter as ReplaceableSimpleAdapter).getStoredArrayList()[position]
                val pkgName = hm["Package"] as String?
                if ("freezeyou@add" != pkgName) {
                    val name = hm["Label"] as String?
                    Support.showChooseActionPopupMenu(
                        this@ShortcutLauncherFolderActivity,
                        this@ShortcutLauncherFolderActivity,
                        view!!, pkgName ?: "",
                        name ?: "", true, uuidSp
                    )
                }
                true
            }
        slfFolderNameTextView.setOnClickListener {
            val builder = FreezeYouAlertDialogBuilder(this@ShortcutLauncherFolderActivity)
            val input = EditText(this@ShortcutLauncherFolderActivity)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            input.layoutParams = lp
            builder.setView(input)
            builder.setTitle(R.string.name)
            builder.setPositiveButton(R.string.save) { _, _ ->
                val sharedPreferences = getSharedPreferences(uuid, MODE_PRIVATE)
                val s = input.text.toString()
                sharedPreferences.edit().putString("folderName", s).apply()
                slfFolderNameTextView.text = s
            }
            builder.setNegativeButton(R.string.cancel, null)
            val alertDialog = builder.show()
            val w = alertDialog.window
            if (w != null) {
                val v1 = w.findViewById<View>(android.R.id.custom)
                if (v1 != null) {
                    val p = v1.parent as View?
                    p?.minimumHeight = 0
                }
            }
        }
    }

    private fun generateFolderItems(
        folderItems: ArrayList<MutableMap<String, Any?>>,
        UUIDSharedPreferences: SharedPreferences
    ) {
        val s = UUIDSharedPreferences.getString("pkgS", "")
        val pkgS = if (s == null) arrayOf() else s.split(",").toTypedArray()
        for (aPkg in pkgS) {
            if ("" != aPkg) {
                val map = HashMap<String, Any?>()
                map["Icon"] = if (FUFUtils.realGetFrozenStatus(this, aPkg, null)) {
                    BitmapDrawable(
                        resources,
                        getGrayBitmap(
                            getBitmapFromDrawable(
                                getApplicationIcon(
                                    this, aPkg,
                                    ApplicationInfoUtils.getApplicationInfoFromPkgName(aPkg, this),
                                    false
                                )
                            )
                        )
                    )
                } else {
                    getApplicationIcon(
                        this,
                        aPkg,
                        ApplicationInfoUtils.getApplicationInfoFromPkgName(aPkg, this),
                        false
                    )
                }
                map["Label"] = getApplicationLabel(this, null, null, aPkg)
                map["Package"] = aPkg
                folderItems.add(map)
            }
        }
        val map = HashMap<String, Any?>()
        @Suppress("DEPRECATION")
        map["Icon"] = resources.getDrawable(R.drawable.grid_add)
        map["Label"] = getString(R.string.add)
        map["Package"] = "freezeyou@add"
        folderItems.add(map)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if ("pkgS" == key) {
            val slfAppsGridView = findViewById<GridView>(R.id.slf_apps_gridView)
            if (slfAppsGridView != null) {
                val adapter = slfAppsGridView.adapter
                if (adapter is ReplaceableSimpleAdapter) {
                    val folderItems = ArrayList<MutableMap<String, Any?>>()
                    generateFolderItems(folderItems, sharedPreferences)
                    adapter.replaceAllInFormerArrayList(folderItems)
                }
            }
        } else if ("folderName" == key) {
            val slfFolderNameTextView = findViewById<TextView>(R.id.slf_folderName_textView)
            if (slfFolderNameTextView != null) {
                slfFolderNameTextView.text =
                    sharedPreferences.getString("folderName", getString(R.string.folder))
            }
        }
    }
}
