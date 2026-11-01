package cf.playhi.freezeyou.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.utils.ProcessSharedState;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;
import static cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable;
import static cf.playhi.freezeyou.utils.ApplicationInfoUtils.getApplicationInfoFromPkgName;
import static cf.playhi.freezeyou.utils.ToastUtils.showShortToast;

public class AppLockActivity extends FreezeYouBaseActivity {
    private BiometricPrompt mBiometricPrompt;
    private BiometricPrompt.PromptInfo mPromptInfo;
    private boolean mPromptHasBeenShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        processSetTheme(this);
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }
        setContentView(R.layout.app_lock_main);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) actionBar.hide();

        initBiometricPromptPart();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        mPromptHasBeenShown = false;
        // The screen now answers for a different application than the one it was raised over, so
        // the face on it has to change with it: a lock that shows one application while unlocking
        // another tells the user nothing about what they are about to let open.
        applyUnlockLogo();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!getIntent().getBooleanExtra("ignoreCurrentUnlockStatus", false)
                && !isLocked()) {
            finishImmediately(RESULT_OK);
            return;
        }

        Button unlockButton = findViewById(R.id.app_lock_main_unlock_button);
        ImageView logoImageView = findViewById(R.id.app_lock_main_logo_imageView);
        unlockButton.setOnClickListener(v -> showBiometricPrompt());
        logoImageView.setOnClickListener(v -> showBiometricPrompt());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ImageButton fingerprintImageButton = findViewById(R.id.app_lock_main_fingerprint_imageButton);
            if (fingerprintImageButton != null) {
                fingerprintImageButton.setOnClickListener(v -> showBiometricPrompt());
            }
        }
        applyUnlockLogo();
    }

    /** The face of the application this screen is standing in front of, when it stands in front of one. */
    private void applyUnlockLogo() {
        ImageView logoImageView = findViewById(R.id.app_lock_main_logo_imageView);
        if (logoImageView == null) return;
        String logoPkgName = getIntent().getStringExtra("unlockLogoPkgName");
        if (logoPkgName != null) {
            logoImageView.setImageBitmap(
                    getBitmapFromDrawable(
                            getApplicationIcon(
                                    getApplicationContext(), logoPkgName,
                                    getApplicationInfoFromPkgName(logoPkgName, getApplicationContext()),
                                    false
                            )
                    )
            );
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !mPromptHasBeenShown) {
            mPromptHasBeenShown = true;
            getWindow().getDecorView().post(this::showBiometricPrompt);
        }
    }

    private void showBiometricPrompt() {
        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return;
        }
        if (mBiometricPrompt != null && mPromptInfo != null) {
            try {
                mBiometricPrompt.authenticate(mPromptInfo);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void finishImmediately(int resultCode) {
        View content = findViewById(android.R.id.content);
        if (content != null) {
            content.setVisibility(View.GONE);
        }
        setResult(resultCode);
        finish();
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }
    }

    /**
     * Whether this screen was raised over somebody else's application rather than opened as the
     * lock of this one. The extra is what says so: only the interception names the application it
     * is standing in front of.
     */
    public static boolean isInterceptionRequest(Intent intent) {
        if (intent == null) return false;
        String targetPkgName = intent.getStringExtra("targetPkgName");
        return targetPkgName != null && !"".equals(targetPkgName.trim());
    }

    /** The application this screen was raised over, or null when it is this application's own lock. */
    private String targetPkgName() {
        Intent intent = getIntent();
        return isInterceptionRequest(intent) ? intent.getStringExtra("targetPkgName") : null;
    }

    /**
     * Whether the unlocked session this screen was raised in has ended since.
     *
     * A screen the interception did not raise carries no stamp, and an answer given there is not a
     * thing a screen-off may take back: that is the settings row proving the door opens, and the
     * user is still standing in front of it.
     */
    private boolean isSessionStale() {
        return ProcessSharedState.isAppLockSessionStale(
                getIntent().getLongExtra("appLockSessionEpoch", ProcessSharedState.NO_APP_LOCK_SESSION),
                ProcessSharedState.getSessionEpoch()
        );
    }

    /**
     * A refusal cannot simply close this screen: the window the user then sees is the application
     * they were just stopped from opening, drawn while they watch. So the screen leaves towards the
     * home screen, which is a window of its own and the only one that can be behind an application
     * nobody has been let into.
     *
     * @return whether this screen did the leaving. The lock of this application is not sent home:
     *         there the caller is a screen of this application, is told the answer was "no", and
     *         finishes itself - and the settings row that only wants proof the lock can be opened
     *         again would throw the user out of the app over a mistap.
     */
    private boolean dismissWithoutRevealingTarget() {
        if (!isInterceptionRequest(getIntent()) || isFinishing()) return false;
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(home);
        } catch (RuntimeException e) {
            // Still leaving: a screen that stayed because it had nowhere to go would be the one
            // place the application behind it is guaranteed to be visible. The service raises the
            // lock again over the window that takes the screen.
            e.printStackTrace();
        }
        finishImmediately(RESULT_CANCELED);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (!dismissWithoutRevealingTarget()) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBiometricPrompt != null) {
            try {
                mBiometricPrompt.cancelAuthentication();
            } catch (Exception ignored) {
            }
        }
    }

    private void initBiometricPromptPart() {
        Executor executor = ContextCompat.getMainExecutor(this);
        mBiometricPrompt = new BiometricPrompt(AppLockActivity.this, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(
                            int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED
                                && errorCode != BiometricPrompt.ERROR_CANCELED
                                && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            showShortToast(
                                    getApplicationContext(),
                                    String.format(
                                            getString(R.string.authenticationError_colon), errString
                                    )
                            );
                        }
                        setResult(RESULT_CANCELED);
                        // A prompt that was cancelled, timed out or locked out is a refusal, and a
                        // refusal is not allowed to leave the application it was raised over visible.
                        dismissWithoutRevealingTarget();
                    }

                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);

                        // The prompt is not answered when it is raised but whenever the device gets
                        // round to it, and a screen-off in between has already ended the session
                        // this screen was stamped in. Handing the application over now would open
                        // it for whoever wakes the device next, so the answer is dropped and the
                        // user is taken home rather than left looking at an application that was
                        // never opened for them.
                        if (isSessionStale()) {
                            dismissWithoutRevealingTarget();
                            return;
                        }

                        // The application the user is now going into is open for as long as it stays
                        // the window in front; the accessibility service takes that back the moment
                        // it is not.
                        ProcessSharedState.setPackageUnlocked(targetPkgName());

                        // Clears the background and screen-off triggers too: the app is open again
                        // from now, and an old stamp would lock it on the next resume.
                        resetLockState(AppLockActivity.this);

                        finishImmediately(RESULT_OK);
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        showShortToast(getApplicationContext(), R.string.authenticationFailed);
                        setResult(RESULT_CANCELED);
                    }
                });

        mPromptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.authentication))
                .setSubtitle(getString(R.string.verifyToContinue))
                .setAllowedAuthenticators(BIOMETRIC_STRONG | BIOMETRIC_WEAK | DEVICE_CREDENTIAL)
                .build();
    }

    @Override
    protected boolean activityNeedCheckAppLock() {
        return false;
    }
}
