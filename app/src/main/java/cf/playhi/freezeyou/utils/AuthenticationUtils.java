package cf.playhi.freezeyou.utils;

import android.content.Context;

import androidx.biometric.BiometricManager;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.enableAppLock;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.enableAuthentication;
import static cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.lockOnEntry;

public class AuthenticationUtils {

    public static boolean isAuthenticationEnabled() {
        return enableAuthentication.getValue(null);
    }

    /**
     * Whether opening FreezeYou itself asks for authentication. Subordinate to
     * {@link #isAuthenticationEnabled()}: with this off the entry is left open while the shortcuts
     * and the App Lock still ask, which is the point of keeping the two promises apart.
     */
    public static boolean isLockOnEntryEnabled() {
        return lockOnEntry.getValue(null);
    }

    /**
     * Whether the applications on the App Lock list must be unlocked into.
     *
     * Subordinate to {@link #isAuthenticationEnabled()}: an installation that kept this switch on
     * with no protection method chosen must not intercept unprotected after the upgrade.
     */
    public static boolean isAppLockEnabled() {
        return enableAppLock.getValue(null) && isAuthenticationEnabled();
    }

    /**
     * Whether the setting to lock upon device screen off is applicable.
     *
     * Relocking on screen off is meaningful whenever either the lock on FreezeYou itself or the
     * lock on protected applications is active.
     */
    public static boolean isLockOnScreenOffApplicable(boolean authEnabled, boolean appLockEnabled) {
        return authEnabled || appLockEnabled;
    }

    public static boolean isLockOnScreenOffApplicable() {
        return isLockOnScreenOffApplicable(isAuthenticationEnabled(), isAppLockEnabled());
    }

    public static boolean isBiometricPromptPartAvailable(Context context) {
        BiometricManager biometricManager = BiometricManager.from(context);
        switch (biometricManager.canAuthenticate(
                BIOMETRIC_STRONG | BIOMETRIC_WEAK | DEVICE_CREDENTIAL)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                return true;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
            case BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED:
            case BiometricManager.BIOMETRIC_STATUS_UNKNOWN:
            default:
                return false;
        }
    }

    /** Whether a code built into FreezeYou has been set. */
    public static boolean isCodeSet() {
        return AppCodeUtils.hasCode();
    }

    /**
     * Whether there is anything to ask the user with: the phone's own lock, or the code built into
     * FreezeYou. The code is what lets the lock work on a device that has no screen lock set at all.
     */
    public static boolean isAuthenticatorAvailable(Context context) {
        return isBiometricPromptPartAvailable(context) || isCodeSet();
    }
}
