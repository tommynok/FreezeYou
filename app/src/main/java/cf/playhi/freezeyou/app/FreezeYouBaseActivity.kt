package cf.playhi.freezeyou.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.MenuItem
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys
import cf.playhi.freezeyou.ui.AppLockActivity
import cf.playhi.freezeyou.utils.AuthenticationUtils.isAuthenticationEnabled
import cf.playhi.freezeyou.utils.AuthenticationUtils.isBiometricPromptPartAvailable
import cf.playhi.freezeyou.utils.Support.getLocalString
import com.tencent.mmkv.MMKV
import java.util.*

open class FreezeYouBaseActivity : AppCompatActivity() {
    private var mUnlockLogoPkgName: String? = null
    private var mHadBeenUnlocked = false

    @CallSuper
    override fun attachBaseContext(newBase: Context) {
        val locale = getLocalString(newBase)
        val configuration = Configuration()
        configuration.setLocale(
            if (DefaultMultiProcessMMKVStorageStringKeys.languagePref.defaultValue()
                    .equals(locale, ignoreCase = true)
            ) Locale.getDefault() else Locale.forLanguageTag(locale)
        )
        val context = newBase.createConfigurationContext(configuration)
        super.attachBaseContext(context)
    }

    @CallSuper
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    @CallSuper
    override fun onResume() {
        super.onResume()
        if (activityNeedCheckAppLock() && isAuthenticationEnabled()
            && isBiometricPromptPartAvailable(this)
        ) {
            if (isLocked) {
                mHadBeenUnlocked = false
                @Suppress("DEPRECATION")
                startActivityForResult(
                    Intent(this, AppLockActivity::class.java)
                        .putExtra("unlockLogoPkgName", mUnlockLogoPkgName),
                    APP_LOCK_ACTIVITY_REQUEST_CODE
                )
            } else {
                mHadBeenUnlocked = true
            }
        }
    }

    @CallSuper
    override fun onPause() {
        super.onPause()
        if (mHadBeenUnlocked) {
            resetLockWaitTime(
                MMKV.mmkvWithAshmemID(
                    applicationContext, "AshmemKV",
                    32, MMKV.MULTI_PROCESS_MODE, null
                )
            )
        }
    }

    @CallSuper
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == APP_LOCK_ACTIVITY_REQUEST_CODE && resultCode == RESULT_CANCELED) {
            finish()
        }
    }

    @CallSuper
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * If the activity does not need the app lock logic,
     * override the method and return false.
     *
     * @return Whether the app lock needs to be checked.
     */
    protected open fun activityNeedCheckAppLock(): Boolean {
        return true
    }

    protected val isLocked: Boolean
        get() {
            if (!isAuthenticationEnabled()) return false
            if (!isBiometricPromptPartAvailable(this)) return false
            val mmkv = MMKV.mmkvWithAshmemID(
                applicationContext, "AshmemKV",
                32, MMKV.MULTI_PROCESS_MODE, null
            )
            val currentTime = Date().time
            // 15 minutes
            return if (mmkv.decodeLong("unlockTime", 0) < currentTime - 900000) {
                true
            } else {
                resetLockWaitTime(mmkv)
                false
            }
        }

    protected fun resetLockWaitTime(mmkv: MMKV?): Boolean {
        return mmkv?.encode("unlockTime", Date().time) ?: false
    }

    protected fun setUnlockLogoPkgName(pkgName: String?) {
        mUnlockLogoPkgName = pkgName
    }

    companion object {
        private const val APP_LOCK_ACTIVITY_REQUEST_CODE = 65533
    }
}
