package cf.playhi.freezeyou.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.BackupUtils.getExportContent
import cf.playhi.freezeyou.utils.ClipboardUtils
import cf.playhi.freezeyou.utils.GZipUtils
import cf.playhi.freezeyou.utils.ThemeUtils.processActionBar
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import cf.playhi.freezeyou.utils.ToastUtils

class BackupMainActivity : FreezeYouBaseActivity() {
    //    Camera mCamera = null; 先把文本方式稳定下来，再做 QRCode
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        processActionBar(supportActionBar)
        setContentView(R.layout.bma_main)
        onCreateInit()
    }

    private fun onCreateInit() {
        initButtons()
    }

    private fun initButtons() {
        val bmaMainExportButton = findViewById<Button>(R.id.bma_main_export_button)
        val bmaMainImportButton = findViewById<Button>(R.id.bma_main_import_button)
        val bmaMainCopyButton = findViewById<Button>(R.id.bma_main_copy_button)
        val bmaMainPasteButton = findViewById<Button>(R.id.bma_main_paste_button)
        bmaMainExportButton.setOnClickListener {
            val editText = findViewById<EditText>(R.id.bma_main_inputAndoutput_editText)
            editText.setText(GZipUtils.gzipCompress(getExportContent(applicationContext)))
            editText.selectAll()
        }
        bmaMainImportButton.setOnClickListener {
            val editText = findViewById<EditText>(R.id.bma_main_inputAndoutput_editText)
            startActivity(
                Intent(this@BackupMainActivity, BackupImportChooserActivity::class.java)
                    .putExtra(
                        "jsonObjectString",
                        GZipUtils.gzipDecompress(editText.text.toString())
                    )
            )
        }
        bmaMainCopyButton.setOnClickListener {
            val editText = findViewById<EditText>(R.id.bma_main_inputAndoutput_editText)
            if (ClipboardUtils.copyToClipboard(applicationContext, editText.text.toString())) {
                ToastUtils.showToast(this@BackupMainActivity, R.string.success)
            } else {
                ToastUtils.showToast(this@BackupMainActivity, R.string.failed)
            }
        }
        bmaMainPasteButton.setOnClickListener {
            val editText = findViewById<EditText>(R.id.bma_main_inputAndoutput_editText)
            editText.setText(ClipboardUtils.getClipboardItemText(applicationContext))
        }
    }
}
