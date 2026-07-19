package cf.playhi.freezeyou.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import androidx.biometric.BiometricManager.Authenticators.*
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon
import cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable
import cf.playhi.freezeyou.utils.ApplicationInfoUtils.getApplicationInfoFromPkgName
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import cf.playhi.freezeyou.utils.ToastUtils.showShortToast
import com.tencent.mmkv.MMKV
import java.util.*

class AppLockActivity : FreezeYouBaseActivity() {
    private lateinit var mBiometricPrompt: BiometricPrompt
    private lateinit var mPromptInfo: BiometricPrompt.PromptInfo
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.app_lock_main)
        val actionBar = supportActionBar
        actionBar?.hide()
        initBiometricPromptPart()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (!intent.getBooleanExtra("ignoreCurrentUnlockStatus", false)
            && !isLocked
        ) {
            finish()
            return
        }
        val unlockButton = findViewById<Button>(R.id.app_lock_main_unlock_button)
        val logoImageView = findViewById<ImageView>(R.id.app_lock_main_logo_imageView)
        unlockButton.setOnClickListener { mBiometricPrompt.authenticate(mPromptInfo) }
        logoImageView.setOnClickListener { mBiometricPrompt.authenticate(mPromptInfo) }
        val fingerprintImageButton =
            findViewById<ImageButton>(R.id.app_lock_main_fingerprint_imageButton)
        fingerprintImageButton.setOnClickListener { mBiometricPrompt.authenticate(mPromptInfo) }
        val logoPkgName = intent.getStringExtra("unlockLogoPkgName")
        if (logoPkgName != null) {
            logoImageView.setImageBitmap(
                getBitmapFromDrawable(
                    getApplicationIcon(
                        applicationContext, logoPkgName,
                        getApplicationInfoFromPkgName(logoPkgName, applicationContext),
                        false
                    )
                )
            )
        }
        mBiometricPrompt.authenticate(mPromptInfo)
    }

    override fun onPause() {
        super.onPause()
        mBiometricPrompt.cancelAuthentication()
    }

    private fun initBiometricPromptPart() {
        val executor = ContextCompat.getMainExecutor(this)
        mBiometricPrompt = BiometricPrompt(this@AppLockActivity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(
                    errorCode: Int, errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    showShortToast(
                        applicationContext,
                        String.format(
                            getString(R.string.authenticationError_colon), errString
                        )
                    )
                    setResult(RESULT_CANCELED)
                }

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    MMKV.mmkvWithAshmemID(
                        applicationContext, "AshmemKV",
                        32, MMKV.MULTI_PROCESS_MODE, null
                    )
                        .encode("unlockTime", Date().time)
                    setResult(RESULT_OK)
                    finish()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    showShortToast(applicationContext, R.string.authenticationFailed)
                    setResult(RESULT_CANCELED)
                }
            })
        mPromptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.authentication))
            .setSubtitle(getString(R.string.verifyToContinue))
            .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()
    }

    override fun activityNeedCheckAppLock(): Boolean {
        return false
    }
}
