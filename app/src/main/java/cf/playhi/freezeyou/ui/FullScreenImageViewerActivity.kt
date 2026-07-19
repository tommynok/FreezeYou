package cf.playhi.freezeyou.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.ThemeUtils

class FullScreenImageViewerActivity : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeUtils.processSetTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fsiva_main)
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(false)
            actionBar.setDisplayShowTitleEnabled(false)
            actionBar.setDisplayHomeAsUpEnabled(true)
        }
        val intent = intent
        if (intent != null) {
            val fsivaMainImageView = findViewById<ImageView>(R.id.fsiva_main_imageView)
            fsivaMainImageView.setImageBitmap(
                BitmapFactory.decodeFile(intent.getStringExtra("imgPath"))
            )
            fsivaMainImageView.setOnClickListener { finish() }
        } else {
            finish()
        }
    }
}
