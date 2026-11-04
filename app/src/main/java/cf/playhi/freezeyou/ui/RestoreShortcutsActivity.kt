package cf.playhi.freezeyou.ui

import android.content.pm.ShortcutManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.LauncherShortcutUtils
import cf.playhi.freezeyou.utils.LauncherShortcutUtils.ShortcutRecord
import cf.playhi.freezeyou.utils.ThemeUtils.processActionBar
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import cf.playhi.freezeyou.utils.ToastUtils.showToast

class RestoreShortcutsActivity : FreezeYouBaseActivity() {

    private var mShortcuts: List<ShortcutRecord> = emptyList()
    private var mCurrentIndex: Int = 0
    private var mPinRequested: Boolean = false

    private lateinit var mCounterTextView: TextView
    private lateinit var mIconImageView: ImageView
    private lateinit var mNameTextView: TextView
    private lateinit var mDetailTextView: TextView
    private lateinit var mAddButton: Button
    private lateinit var mSkipButton: Button
    private lateinit var mCancelButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        processActionBar(supportActionBar)
        setContentView(R.layout.activity_restore_shortcuts)

        if (savedInstanceState != null) {
            mCurrentIndex = savedInstanceState.getInt("currentIndex", 0)
            mPinRequested = savedInstanceState.getBoolean("pinRequested", false)
        }

        mCounterTextView = findViewById(R.id.restoreShortcuts_counter_textView)
        mIconImageView = findViewById(R.id.restoreShortcuts_icon_imageView)
        mNameTextView = findViewById(R.id.restoreShortcuts_name_textView)
        mDetailTextView = findViewById(R.id.restoreShortcuts_detail_textView)
        mAddButton = findViewById(R.id.restoreShortcuts_add_button)
        mSkipButton = findViewById(R.id.restoreShortcuts_skip_button)
        mCancelButton = findViewById(R.id.restoreShortcuts_cancel_button)

        val allRecords = LauncherShortcutUtils.getSavedShortcutRecords(this)
        val pinnedIds: Set<String>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = getSystemService(ShortcutManager::class.java)
            if (shortcutManager != null) {
                try {
                    shortcutManager.pinnedShortcuts.map { it.id }.toSet()
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        } else {
            null
        }
        mShortcuts = filterUnpinnedShortcuts(allRecords, pinnedIds)

        if (mShortcuts.isEmpty()) {
            showToast(this, R.string.noShortcutsToRestore)
            finish()
            return
        }

        mCancelButton.setOnClickListener { finish() }
        mSkipButton.setOnClickListener { advanceToNext() }
        mAddButton.setOnClickListener {
            if (mCurrentIndex in mShortcuts.indices) {
                val record = mShortcuts[mCurrentIndex]
                val success = LauncherShortcutUtils.restoreShortcut(this, record)
                if (success) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        advanceToNext()
                    } else {
                        mPinRequested = true
                    }
                } else {
                    showToast(this, getString(R.string.restoreShortcutFailed, record.name))
                }
            }
        }

        updateCurrentShortcutView()
    }

    override fun onResume() {
        super.onResume()
        if (mPinRequested) {
            mPinRequested = false
            advanceToNext()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentIndex", mCurrentIndex)
        outState.putBoolean("pinRequested", mPinRequested)
    }

    private fun advanceToNext() {
        mCurrentIndex++
        if (mCurrentIndex >= mShortcuts.size) {
            showToast(this, R.string.finish)
            finish()
        } else {
            updateCurrentShortcutView()
        }
    }

    private fun updateCurrentShortcutView() {
        if (mCurrentIndex !in mShortcuts.indices) {
            finish()
            return
        }
        val record = mShortcuts[mCurrentIndex]
        mCounterTextView.text = getString(
            R.string.restoreShortcutsProgress,
            mCurrentIndex + 1,
            mShortcuts.size
        )
        mNameTextView.text = record.name
        mDetailTextView.text = record.pkgName
        val icon = LauncherShortcutUtils.resolveShortcutIcon(this, record.pkgName)
        mIconImageView.setImageDrawable(icon)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        @JvmStatic
        fun filterUnpinnedShortcuts(
            allRecords: List<ShortcutRecord>,
            pinnedIds: Set<String>?
        ): List<ShortcutRecord> {
            if (pinnedIds == null) return allRecords
            return allRecords.filter { it.id !in pinnedIds }
        }
    }
}
