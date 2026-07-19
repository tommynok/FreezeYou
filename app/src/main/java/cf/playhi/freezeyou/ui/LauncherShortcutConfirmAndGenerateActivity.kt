package cf.playhi.freezeyou.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import cf.playhi.freezeyou.Freeze
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable
import cf.playhi.freezeyou.utils.ApplicationInfoUtils.getApplicationInfoFromPkgName
import cf.playhi.freezeyou.utils.LauncherShortcutUtils.createShortCut
import cf.playhi.freezeyou.utils.MoreUtils.requestOpenWebSite
import cf.playhi.freezeyou.utils.ThemeUtils
import cf.playhi.freezeyou.utils.ThemeUtils.processActionBar
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import java.io.File
import java.util.*

class LauncherShortcutConfirmAndGenerateActivity : FreezeYouBaseActivity() {
    private var requestFromLauncher = false
    private var targetSelfCls: Class<*>? = null
    private var finalDrawable: Drawable? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        processActionBar(supportActionBar)
        setContentView(R.layout.lscaga_main)
        val intent = intent
        requestFromLauncher = Intent.ACTION_CREATE_SHORTCUT == intent.action
        targetSelfCls = if (requestFromLauncher) {
            Freeze::class.java
        } else {
            intent.getSerializableExtra("class") as Class<*>?
        }
        init()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.lscaga_menu, menu)
        val cTheme = ThemeUtils.getUiTheme(this)
        if ("white" == cTheme || "default" == cTheme) menu.findItem(R.id.lscaga_menu_help)
            .setIcon(R.drawable.ic_action_help_outline_light)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.lscaga_menu_help -> {
                requestOpenWebSite(
                    this,
                    String.format(
                        "https://www.zidon.net/%1\$s/guide/schedules.html",
                        getString(R.string.correspondingAndAvailableWebsiteUrlLanguageCode)
                    )
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data == null) return
        when (requestCode) {
            8 -> if (resultCode == RESULT_OK) {
                val lscagaTargetEditText = findViewById<EditText>(R.id.lscaga_target_editText)
                val lscagaIdEditText = findViewById<EditText>(R.id.lscaga_id_editText)
                val lscagaIconImageButton = findViewById<ImageButton>(R.id.lscaga_icon_imageButton)
                val lscagaDisplayNameEditText =
                    findViewById<EditText>(R.id.lscaga_displayName_editText)
                lscagaTargetEditText.setText(data.getStringExtra("name"))
                lscagaIdEditText.setText(data.getStringExtra("id"))
                lscagaDisplayNameEditText.setText(data.getStringExtra("label"))
                val bm = data.getParcelableExtra<Bitmap>("icon")
                if (bm != null) {
                    finalDrawable = BitmapDrawable(resources, bm)
                    lscagaIconImageButton.setImageDrawable(finalDrawable)
                }
            }
            11 -> if (resultCode == RESULT_OK) {
                setIntent(data)
                init()
            }
            21 -> if (resultCode == RESULT_OK) {
                val bm = data.getParcelableExtra<Bitmap>("Icon")
                if (bm != null) {
                    finalDrawable = BitmapDrawable(resources, bm)
                    val lscagaIconImageButton =
                        findViewById<ImageButton>(R.id.lscaga_icon_imageButton)
                    lscagaIconImageButton.setImageDrawable(finalDrawable)
                }
            }
            else -> {}
        }
    }

    private fun checkAndAvoidNull(s: String?, alternate: String?): String {
        return s ?: (alternate ?: "")
    }

    private fun init() {
        val intent = intent
        val name = checkAndAvoidNull(intent.getStringExtra("name"), getString(R.string.name))
        val id = checkAndAvoidNull(
            intent.getStringExtra("id"),
            Date().time.toString()
        ) //若桌面（类小部件快捷方式）发起，id 值无需考虑，无需使用。
        val pkgName =
            checkAndAvoidNull(intent.getStringExtra("pkgName"), getString(R.string.plsSelect))
        val lscagaPackageButton = findViewById<Button>(R.id.lscaga_package_button)
        val lscagaTargetButton = findViewById<Button>(R.id.lscaga_target_button)
        val lscagaGenerateButton = findViewById<Button>(R.id.lscaga_generate_button)
        val lscagaCancelButton = findViewById<Button>(R.id.lscaga_cancel_button)
        val lscagaSimulateButton = findViewById<Button>(R.id.lscaga_simulate_button)
        val lscagaPackageEditText = findViewById<EditText>(R.id.lscaga_package_editText)
        val lscagaDisplayNameEditText = findViewById<EditText>(R.id.lscaga_displayName_editText)
        val lscagaTargetEditText = findViewById<EditText>(R.id.lscaga_target_editText)
        val lscagaTaskEditText = findViewById<EditText>(R.id.lscaga_task_editText)
        val lscagaIdEditText = findViewById<EditText>(R.id.lscaga_id_editText)
        val lscagaIconImageButton = findViewById<ImageButton>(R.id.lscaga_icon_imageButton)
        processSelectedPackageEditText(pkgName, lscagaPackageEditText)
        processSelectPackageButton(lscaga_package_button = lscagaPackageButton)
        processDisplayNameEditText(name, lscagaDisplayNameEditText)
        processSelectedTargetEditText(pkgName, lscagaTargetEditText)
        processChangeIconImageButton(pkgName, lscagaIconImageButton)
        processSelectTargetButton(pkgName, lscagaTargetButton)
        processTaskEditText(lscagaTaskEditText)
        processIDEditText(id, lscagaIdEditText)
        processCancelButton(lscagaCancelButton)
        processSimulateButton(
            lscagaPackageEditText,
            lscagaTargetEditText,
            lscagaTaskEditText,
            lscagaSimulateButton
        )
        processGenerateButton(
            lscagaGenerateButton,
            lscagaPackageEditText,
            lscagaDisplayNameEditText,
            lscagaTargetEditText,
            lscagaIdEditText,
            lscagaTaskEditText
        )
    }

    private fun processDisplayNameEditText(name: String, lscaga_displayName_editText: EditText) {
        lscaga_displayName_editText.setText(name)
    }

    private fun processSelectedTargetEditText(pkgName: String, lscaga_target_editText: EditText) {
        lscaga_target_editText.setText(R.string.launch)
        lscaga_target_editText.setOnClickListener { startSelectTargetActivityForResult(pkgName) }
    }

    private fun processSelectedPackageEditText(pkgName: String, lscaga_package_editText: EditText) {
        lscaga_package_editText.setText(pkgName)
        lscaga_package_editText.setOnClickListener { startSelectPackageActivityForResult() }
    }

    private fun processTaskEditText(lscaga_task_editText: EditText) {
        lscaga_task_editText.setText("")
    }

    private fun processIDEditText(id: String, lscaga_id_editText: EditText) {
        lscaga_id_editText.setText(id)
    }

    private fun processChangeIconImageButton(pkgName: String?, lscaga_icon_imageButton: ImageButton) {
        var widthAndHeight = (resources.displayMetrics.widthPixels * 0.35).toInt()
        if (widthAndHeight <= 0) widthAndHeight = 1
        val layoutParams = lscaga_icon_imageButton.layoutParams
        layoutParams.height = widthAndHeight
        layoutParams.width = widthAndHeight
        lscaga_icon_imageButton.layoutParams = layoutParams
        if (pkgName != null) {
            when (pkgName) {
                "cf.playhi.freezeyou.extra.fuf", "OF", "UF", "OO", "OOU", "FOQ", "OS", "OU", "UFU" -> @Suppress(
                    "DEPRECATION"
                )
                finalDrawable = resources.getDrawable(R.mipmap.ic_launcher_round)
                "cf.playhi.freezeyou.extra.oklock" -> @Suppress("DEPRECATION")
                finalDrawable = resources.getDrawable(R.drawable.screenlock)
                else -> if (pkgName.startsWith("CATEGORY") || pkgName.startsWith("FORCESTOPCATEGORY")) {
                    @Suppress("DEPRECATION")
                    finalDrawable = resources.getDrawable(R.mipmap.ic_launcher_round)
                } else {
                    @Suppress("DEPRECATION")
                    finalDrawable = if (getString(R.string.plsSelect) == pkgName) resources.getDrawable(
                        R.drawable.grid_add
                    ) else getApplicationIcon(
                        this,
                        pkgName,
                        getApplicationInfoFromPkgName(pkgName, this),
                        false
                    )
                }
            }
            lscaga_icon_imageButton.setImageDrawable(finalDrawable)
            lscaga_icon_imageButton.setOnClickListener {
                @Suppress("DEPRECATION")
                startActivityForResult(
                    Intent(
                        this@LauncherShortcutConfirmAndGenerateActivity,
                        SelectShortcutIconActivity::class.java
                    ),
                    21
                )
            }
        }
    }

    private fun processSelectPackageButton(lscaga_package_button: Button) {
        lscaga_package_button.setOnClickListener { startSelectPackageActivityForResult() }
    }

    private fun processSelectTargetButton(pkgName: String, lscaga_target_button: Button) {
        lscaga_target_button.setOnClickListener { startSelectTargetActivityForResult(pkgName) }
    }

    private fun processGenerateButton(
        lscaga_generate_button: Button,
        lscaga_package_editText: EditText,
        lscaga_displayName_editText: EditText,
        lscaga_target_editText: EditText,
        lscaga_id_editText: EditText,
        lscaga_task_editText: EditText
    ) {
        lscaga_generate_button.setOnClickListener {
            val context = applicationContext
            val pkgName = lscaga_package_editText.text.toString()
            val title = lscaga_displayName_editText.text.toString()
            var target = lscaga_target_editText.text.toString()
            val tasks = lscaga_task_editText.text.toString()
            if (getString(R.string.launch) == target) target = ""
            if (requestFromLauncher) {
                val shortcutIntent =
                    Intent(this@LauncherShortcutConfirmAndGenerateActivity, Freeze::class.java)
                shortcutIntent.putExtra("pkgName", pkgName)
                shortcutIntent.putExtra("target", target)
                shortcutIntent.putExtra("tasks", tasks)
                val intent = Intent()
                intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
                intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, title)
                intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, getBitmapFromDrawable(finalDrawable))
                setResult(RESULT_OK, intent)
                finish()
            } else {
                createShortCut(
                    title,
                    pkgName,
                    finalDrawable!!,
                    targetSelfCls!!,
                    lscaga_id_editText.text.toString(),
                    context,
                    target,
                    tasks
                )
            }
        }
    }

    private fun processCancelButton(lscaga_cancel_button: Button) {
        lscaga_cancel_button.setOnClickListener { finish() }
    }

    private fun processSimulateButton(
        lscaga_package_editText: EditText,
        lscaga_target_editText: EditText,
        lscaga_task_editText: EditText,
        lscaga_simulate_button: Button
    ) {
        lscaga_simulate_button.setOnClickListener {
            val pkgName = lscaga_package_editText.text.toString()
            var target = lscaga_target_editText.text.toString()
            val tasks = lscaga_task_editText.text.toString()
            if (getString(R.string.launch) == target) target = ""
            startActivity(
                Intent(this@LauncherShortcutConfirmAndGenerateActivity, Freeze::class.java)
                    .putExtra("pkgName", pkgName)
                    .putExtra("target", target)
                    .putExtra("tasks", tasks)
            )
        }
    }

    private fun startSelectPackageActivityForResult() {
        @Suppress("DEPRECATION")
        startActivityForResult(
            Intent(
                this@LauncherShortcutConfirmAndGenerateActivity,
                FUFLauncherShortcutCreator::class.java
            )
                .putExtra("returnPkgName", true),
            11
        )
    }

    private fun startSelectTargetActivityForResult(pkgName: String) {
        try {
            val packageInfo = packageManager.getPackageInfo(pkgName, PackageManager.GET_ACTIVITIES)
            val activityInfoS = packageInfo.activities
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(
                    this@LauncherShortcutConfirmAndGenerateActivity,
                    SelectTargetActivityActivity::class.java
                )
                    .putExtra("pkgName", pkgName),
                8
            )
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            showToast(this@LauncherShortcutConfirmAndGenerateActivity, R.string.packageNotFound)
        } catch (e: Exception) {
            e.printStackTrace()
            showToast(
                this@LauncherShortcutConfirmAndGenerateActivity,
                getString(R.string.failed) + File.separator + e.localizedMessage
            )
        }
    }
}
