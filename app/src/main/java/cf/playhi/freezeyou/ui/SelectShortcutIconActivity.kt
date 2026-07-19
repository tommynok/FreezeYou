package cf.playhi.freezeyou.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.GridView
import android.widget.ImageView
import android.widget.SimpleAdapter
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable
import cf.playhi.freezeyou.utils.ThemeUtils
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import java.io.FileNotFoundException

class SelectShortcutIconActivity : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.processSetTheme(this)
        super.onCreate(savedInstanceState)
        ThemeUtils.processActionBar(supportActionBar)
        setContentView(R.layout.ssia_main)
        init()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 21 && data != null) {
            val fullPhotoUri = data.data
            if (fullPhotoUri != null) {
                val contentResolver = contentResolver
                try {
                    var bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(fullPhotoUri))
                    @Suppress("DEPRECATION")
                    if (bitmap.byteCount > getBitmapFromDrawable(resources.getDrawable(R.mipmap.ic_launcher_new_round)).byteCount * 5) {
                        val width = bitmap.width
                        val height = bitmap.height
                        val matrix = Matrix()
                        val scaleWidth = 192.toFloat() / width
                        val scaleHeight = 192.toFloat() / height
                        matrix.postScale(scaleWidth, scaleHeight)
                        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
                    }
                    setResult(
                        RESULT_OK,
                        Intent().putExtra("Icon", bitmap)
                    )
                    finish()
                } catch (e: FileNotFoundException) {
                    showToast(this, R.string.failed)
                }
            }
        }
    }

    private fun init() {
        val icons = ArrayList<HashMap<String, Drawable?>>()

        //选择更多（扔第一个，免得被淹没看不到）
        @Suppress("DEPRECATION")
        addToIconsArrayList(icons, resources.getDrawable(R.drawable.grid_add))
        //自带
        @Suppress("DEPRECATION")
        addToIconsArrayList(icons, resources.getDrawable(R.mipmap.ic_launcher_new_round))
        //自带
        @Suppress("DEPRECATION")
        addToIconsArrayList(icons, resources.getDrawable(R.mipmap.ic_launcher_round))
        //自带
        @Suppress("DEPRECATION")
        addToIconsArrayList(icons, resources.getDrawable(R.mipmap.ic_launcher))
        //自带
        @Suppress("DEPRECATION")
        addToIconsArrayList(icons, resources.getDrawable(R.drawable.screenlock))
        //自带
        @Suppress("DEPRECATION")
        addToIconsArrayList(icons, resources.getDrawable(R.drawable.ic_notification))
        val applicationInfoS =
            packageManager.getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES)
        for (applicationInfo in applicationInfoS) {
            addToIconsArrayList(
                icons,
                getApplicationIcon(
                    this@SelectShortcutIconActivity,
                    applicationInfo.packageName,
                    applicationInfo,
                    false
                )
            )
        }
        val simpleAdapter = SimpleAdapter(
            this, icons,
            R.layout.ssia_main_grid_item, arrayOf("Icon"), intArrayOf(R.id.ssia_mgi_imageView)
        )
        simpleAdapter.setViewBinder { view: View?, data: Any?, _: String? ->
            if (view is ImageView && data is Drawable) {
                view.setImageDrawable(data)
                true
            } else false
        }
        val gridView = findViewById<GridView>(R.id.ssia_main_gridView)
        gridView.adapter = simpleAdapter
        gridView.onItemClickListener =
            AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                if (position == 0) {
                    val intent = Intent(Intent.ACTION_GET_CONTENT)
                    intent.type = "image/*"
                    if (intent.resolveActivity(packageManager) != null) {
                        @Suppress("DEPRECATION")
                        startActivityForResult(intent, 21)
                    }
                } else {
                    setResult(
                        RESULT_OK,
                        Intent()
                            .putExtra(
                                "Icon",
                                getBitmapFromDrawable(icons[position]["Icon"])
                            )
                    )
                    finish()
                }
            }
    }

    private fun addToIconsArrayList(
        icons: ArrayList<HashMap<String, Drawable?>>,
        drawable: Drawable?
    ) {
        val map = HashMap<String, Drawable?>()
        map["Icon"] = drawable
        icons.add(map)
    }
}
