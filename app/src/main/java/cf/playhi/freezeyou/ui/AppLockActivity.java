package cf.playhi.freezeyou.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.tencent.mmkv.MMKV;

import java.util.Date;
import java.util.concurrent.Executor;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.app.FreezeYouBaseActivity;

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
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!getIntent().getBooleanExtra("ignoreCurrentUnlockStatus", false)
                && !isLocked()) {
            setResult(RESULT_OK);
            finish();
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
                    }

                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);

                        MMKV.mmkvWithAshmemID(
                                        getApplicationContext(), "AshmemKV",
                                        32, MMKV.MULTI_PROCESS_MODE, null)
                                .encode("unlockTime", new Date().getTime());

                        setResult(RESULT_OK);
                        finish();
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
