package cf.playhi.freezeyou.utils

import android.content.Context
import androidx.biometric.BiometricManager
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.enableAuthentication

object AuthenticationUtils {
    @JvmStatic
    fun isAuthenticationEnabled(): Boolean {
        return enableAuthentication.getValue(null)
    }

    @JvmStatic
    fun isBiometricPromptPartAvailable(context: Context): Boolean {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> false
            else -> false
        }
    }
}
