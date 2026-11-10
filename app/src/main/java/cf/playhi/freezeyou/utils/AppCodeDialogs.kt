package cf.playhi.freezeyou.utils

import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilder
import cf.playhi.freezeyou.utils.AuthenticationUtils.isBiometricPromptPartAvailable

/**
 * The settings side of the built-in code: set it, change it, remove it. Setting asks for the code
 * twice and warns when the phone has no lock of its own, because then a forgotten code leaves only
 * a clear-data as the way back. Changing and removing ask for the current code; removing also
 * accepts the phone's own lock, which is the recovery for whoever forgot the code but still has the
 * device credential.
 */
object AppCodeDialogs {

    @JvmStatic
    fun showManageDialog(activity: FragmentActivity, onCodeStateChanged: (() -> Unit)? = null) {
        if (!AppCodeUtils.hasCode()) {
            showSetDialog(activity, onCodeStateChanged)
            return
        }
        FreezeYouAlertDialogBuilder(activity)
            .setTitle(R.string.appCode)
            .setItems(
                arrayOf(
                    activity.getString(R.string.changeCode),
                    activity.getString(R.string.removeCode)
                )
            ) { _, which ->
                if (which == 0) showChangeDialog(activity)
                else showRemoveDialog(activity, onCodeStateChanged)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSetDialog(activity: FragmentActivity, onCodeStateChanged: (() -> Unit)? = null) {
        val newBox = passwordBox(activity, R.string.newCode)
        val confirmBox = passwordBox(activity, R.string.confirmCode)
        val builder = FreezeYouAlertDialogBuilder(activity)
            .setTitle(R.string.setCode)
            .setView(twoFields(activity, newBox, confirmBox))
            .setPositiveButton(R.string.okay, null)
            .setNegativeButton(R.string.cancel, null)
        if (!isBiometricPromptPartAvailable(activity)) {
            builder.setMessage(R.string.noPhoneLockWarning)
        }
        val dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val code = newBox.text.toString()
            when {
                code.length < AppCodeUtils.MIN_CODE_LENGTH ->
                    ToastUtils.showToast(activity, R.string.codeTooShort)
                code != confirmBox.text.toString() ->
                    ToastUtils.showToast(activity, R.string.codeMismatch)
                else -> {
                    val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    positive.isEnabled = false
                    AppCodeUtils.setCodeAsync(code) {
                        if (dialog.isShowing && !activity.isFinishing) {
                            dialog.dismiss()
                            ToastUtils.showToast(activity, R.string.codeSetDone)
                            onCodeStateChanged?.invoke()
                        }
                    }
                }
            }
        }
    }

    private fun showChangeDialog(activity: FragmentActivity) {
        val currentBox = passwordBox(activity, R.string.currentCode)
        val newBox = passwordBox(activity, R.string.newCode)
        val confirmBox = passwordBox(activity, R.string.confirmCode)
        val holder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(activity, 24)
            setPadding(pad, pad / 3, pad, 0)
            addView(currentBox, fieldParams())
            addView(newBox, fieldParams())
            addView(confirmBox, fieldParams())
        }
        val dialog = FreezeYouAlertDialogBuilder(activity)
            .setTitle(R.string.changeCode)
            .setView(holder)
            .setPositiveButton(R.string.okay, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val code = newBox.text.toString()
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.isEnabled = false
            AppCodeUtils.verifyCodeAsync(currentBox.text.toString()) { ok ->
                if (!dialog.isShowing || activity.isFinishing) return@verifyCodeAsync
                positive.isEnabled = true
                if (!ok) {
                    ToastUtils.showToast(activity, R.string.wrongCode)
                    return@verifyCodeAsync
                }
                when {
                    code.length < AppCodeUtils.MIN_CODE_LENGTH ->
                        ToastUtils.showToast(activity, R.string.codeTooShort)
                    code != confirmBox.text.toString() ->
                        ToastUtils.showToast(activity, R.string.codeMismatch)
                    else -> {
                        positive.isEnabled = false
                        AppCodeUtils.setCodeAsync(code) {
                            if (dialog.isShowing && !activity.isFinishing) {
                                dialog.dismiss()
                                ToastUtils.showToast(activity, R.string.codeChangedDone)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showRemoveDialog(activity: FragmentActivity, onCodeStateChanged: (() -> Unit)? = null) {
        val currentBox = passwordBox(activity, R.string.currentCode)
        val builder = FreezeYouAlertDialogBuilder(activity)
            .setTitle(R.string.removeCode)
            .setView(twoFields(activity, currentBox, null))
            .setPositiveButton(R.string.okay, null)
            .setNegativeButton(R.string.cancel, null)
        if (isBiometricPromptPartAvailable(activity)) {
            builder.setNeutralButton(R.string.usePhoneLock) { _, _ ->
                recoverWithPhoneLock(activity) {
                    AppCodeUtils.clearCode()
                    ToastUtils.showToast(activity, R.string.codeRemovedDone)
                    onCodeStateChanged?.invoke()
                }
            }
        }
        val dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.isEnabled = false
            AppCodeUtils.verifyCodeAsync(currentBox.text.toString()) { ok ->
                if (!dialog.isShowing || activity.isFinishing) return@verifyCodeAsync
                if (ok) {
                    AppCodeUtils.clearCode()
                    dialog.dismiss()
                    ToastUtils.showToast(activity, R.string.codeRemovedDone)
                    onCodeStateChanged?.invoke()
                } else {
                    positive.isEnabled = true
                    ToastUtils.showToast(activity, R.string.wrongCode)
                }
            }
        }
    }

    /** The phone's own lock, the way back for whoever forgot the code but still has the device lock. */
    private fun recoverWithPhoneLock(activity: FragmentActivity, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED
                        && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        && errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        ToastUtils.showToast(activity, R.string.authenticationFailed)
                    }
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.authentication))
            .setSubtitle(activity.getString(R.string.verifyToContinue))
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
                        or BiometricManager.Authenticators.BIOMETRIC_WEAK
                        or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(info)
    }

    private fun passwordBox(activity: FragmentActivity, hintRes: Int): EditText {
        return EditText(activity).apply {
            // Mask via transformation: a password inputType makes the IME offer password generation.
            setInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
            transformationMethod = PasswordTransformationMethod.getInstance()
            setHint(hintRes)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
                // Some IMEs ignore the no-suggestions input type and still learn from what is typed.
                imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            }
        }
    }

    private fun twoFields(activity: FragmentActivity, first: EditText, second: EditText?): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(activity, 24)
            setPadding(pad, pad / 3, pad, 0)
            addView(first, fieldParams())
            if (second != null) addView(second, fieldParams())
        }
    }

    private fun fieldParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )

    private fun dp(activity: FragmentActivity, value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}