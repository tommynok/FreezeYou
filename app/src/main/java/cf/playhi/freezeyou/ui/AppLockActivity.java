package cf.playhi.freezeyou.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.utils.AppCodeUtils;
import cf.playhi.freezeyou.utils.ProcessSharedState;

import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG;
import static androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK;
import static androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL;
import static cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilderKt.FreezeYouAlertDialogBuilder;
import static cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme;
import static cf.playhi.freezeyou.utils.AuthenticationUtils.AUTH_METHOD_APP_CODE;
import static cf.playhi.freezeyou.utils.AuthenticationUtils.getPrimaryAuthenticationMethod;
import static cf.playhi.freezeyou.utils.AuthenticationUtils.isAuthenticationEnabled;
import static cf.playhi.freezeyou.utils.AuthenticationUtils.isBiometricPromptPartAvailable;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getApplicationIcon;
import static cf.playhi.freezeyou.utils.ApplicationIconUtils.getBitmapFromDrawable;
import static cf.playhi.freezeyou.utils.ApplicationInfoUtils.getApplicationInfoFromPkgName;
import static cf.playhi.freezeyou.utils.ToastUtils.showShortToast;

public class AppLockActivity extends FreezeYouBaseActivity {
    private BiometricPrompt mBiometricPrompt;
    private BiometricPrompt.PromptInfo mPromptInfo;
    private boolean mPromptHasBeenShown = false;
    /**
     * What the question now in flight was asked about: the application on screen and the session it
     * was stamped in, both taken at the moment the question went out rather than at the moment an
     * answer arrives.
     */
    private String mPromptedTargetPkg;
    private long mPromptedSessionEpoch = ProcessSharedState.NO_APP_LOCK_SESSION;
    /** Whether this screen is the window the user is looking at right now. */
    private boolean mHasWindowFocus = false;
    /** The code question, when a code is set; kept so a second focus does not stack another on top. */
    private AlertDialog mCodeDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        processSetTheme(this);
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
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

        reStampSessionOfAStandingScreen();

        if (!getIntent().getBooleanExtra("ignoreCurrentUnlockStatus", false)
                && !isLocked()) {
            finishImmediately(RESULT_OK);
            return;
        }

        Button unlockButton = findViewById(R.id.app_lock_main_unlock_button);
        ImageView logoImageView = findViewById(R.id.app_lock_main_logo_imageView);
        unlockButton.setOnClickListener(v -> showAuthenticator());
        logoImageView.setOnClickListener(v -> showAuthenticator());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ImageButton fingerprintImageButton = findViewById(R.id.app_lock_main_fingerprint_imageButton);
            if (fingerprintImageButton != null) {
                fingerprintImageButton.setOnClickListener(v -> showAuthenticator());
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
        mHasWindowFocus = hasFocus;
        if (hasFocus && !mPromptHasBeenShown) {
            mPromptHasBeenShown = true;
            getWindow().getDecorView().post(this::showAuthenticator);
        }
    }

    /**
     * The session an interception lock is standing in is refreshed when the screen comes back to it.
     *
     * A device that went dark ended that session, but the lock left on top of the application asks the
     * same person the same question, and an answer given to it is not the device's mistake to punish:
     * the stamp moves with the screen rather than turning an honest answer into a trip to the home
     * screen. What the screen-off took back stays taken - the session set is empty from that moment,
     * so the application behind this screen opens again only for whoever proves themselves now.
     */
    private void reStampSessionOfAStandingScreen() {
        if (!isInterceptionRequest(getIntent())) return;
        long epoch = ProcessSharedState.getSessionEpoch();
        getIntent().putExtra("appLockSessionEpoch", epoch);
        mPromptedSessionEpoch = epoch;
    }

    private void showBiometricPrompt() {
        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return;
        }
        if (mBiometricPrompt != null && mPromptInfo != null) {
            // The answer comes back with nothing attached to it, so what it was asked about is
            // recorded here - the last thing that is still true of this screen when the question is
            // the one being answered.
            mPromptedTargetPkg = targetPkgName();
            mPromptedSessionEpoch = getIntent().getLongExtra(
                    "appLockSessionEpoch", ProcessSharedState.NO_APP_LOCK_SESSION
            );
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
     * Whether an answer to a prompt is an answer about what this screen is asking now.
     *
     * A prompt outlives the moment it was raised: the device may answer it after the interception has
     * re-tasked this screen over another application, or after a screen-off ended the session the
     * question was stamped in. Either way the biometric proves who is holding the phone and not which
     * application they were being let into, so an answer that names a different question than the one
     * on screen hands over nothing.
     *
     * <p>A screen the interception did not stamp carries no session, and an answer given there is not
     * a thing a screen-off may take back: that is the settings row proving the door opens, and the
     * user is still standing in front of it.
     *
     * Kept over plain values, like the gate the service raises the screen through, so the rule can be
     * read and tested without a screen.
     */
    public static boolean isAnswerForCurrentQuestion(
            String promptedTarget, String currentTarget,
            long promptedEpoch, long currentEpoch) {
        // Null means "this screen is the lock of this application", and only the same kind of screen
        // can answer it. Compared by hand: the platform helper for it is newer than this application
        // runs on.
        if (promptedTarget == null) {
            if (currentTarget != null) return false;
        } else if (!promptedTarget.equals(currentTarget)) {
            return false;
        }
        return !ProcessSharedState.isAppLockSessionStale(promptedEpoch, currentEpoch);
    }

    /** Whether the answer in flight belongs to the question this screen is asking now. */
    private boolean answerIsForCurrentQuestion() {
        return isAnswerForCurrentQuestion(
                mPromptedTargetPkg, targetPkgName(),
                mPromptedSessionEpoch, ProcessSharedState.getSessionEpoch()
        );
    }

    /**
     * Puts the question again to the user who just answered an older one.
     *
     * Only while this screen is the window they are looking at: a prompt raised at a screen nobody can
     * see is a question asked of the next person to wake the device, and the answer to it arrives with
     * no user in front of it.
     */
    private void askAgainIfStillTheScreenInTheFront() {
        if (!mHasWindowFocus || isFinishing()) return;
        showAuthenticator();
    }

    /**
     * The question this screen asks:
     * - If {@code forceBiometricPrompt} is requested (e.g. settings testing device lock), biometric prompt.
     * - If user selected App Code as primary and code is set, the code dialog.
     * - If device lock is available, the biometric prompt.
     * - If code is set, the code dialog.
     * In cooperative mode, both methods can coexist and either one can fall back or switch to the other.
     */
    private void showAuthenticator() {
        if (getIntent().getBooleanExtra("forceBiometricPrompt", false)) {
            showBiometricPrompt();
            return;
        }
        String primary = getPrimaryAuthenticationMethod();
        if (AUTH_METHOD_APP_CODE.equals(primary) && AppCodeUtils.hasCode()) {
            showCodeDialog();
        } else if (isBiometricPromptPartAvailable(this)) {
            showBiometricPrompt();
        } else if (AppCodeUtils.hasCode()) {
            showCodeDialog();
        } else {
            finishImmediately(RESULT_CANCELED);
        }
    }

    /**
     * The code question, asked over the same screen the biometric uses. A wrong code keeps the
     * screen up and pays the persistent attempt counter; a refusal leaves towards the home screen
     * when this is an interception, exactly as a cancelled biometric does. The phone's own lock is
     * offered as the way back for whoever forgot the code, but only when the device has one set.
     */
    private void showCodeDialog() {
        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return;
        }
        if (mCodeDialog != null && mCodeDialog.isShowing()) return;

        // Recorded when the question goes out, like the biometric stamp: the answer is checked
        // against the application and the session this screen is asking about now.
        mPromptedTargetPkg = targetPkgName();
        mPromptedSessionEpoch = getIntent().getLongExtra(
                "appLockSessionEpoch", ProcessSharedState.NO_APP_LOCK_SESSION
        );

        EditText codeBox = new EditText(this);
        // Mask via transformation: a password inputType makes the IME offer password generation.
        codeBox.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        codeBox.setTransformationMethod(PasswordTransformationMethod.getInstance());
        codeBox.setHint(R.string.enterCode);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            codeBox.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            // Some IMEs ignore the no-suggestions input type and still learn from what is typed.
            codeBox.setImeOptions(codeBox.getImeOptions() | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        }
        LinearLayout holder = new LinearLayout(this);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        holder.setPadding(pad, pad / 3, pad, 0);
        holder.addView(codeBox, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder builder = FreezeYouAlertDialogBuilder(this)
                .setTitle(R.string.authentication)
                .setView(holder)
                .setPositiveButton(R.string.okay, null)
                .setNegativeButton(R.string.cancel, (d, w) -> refuseCode())
                .setOnCancelListener(d -> refuseCode());
        if (isBiometricPromptPartAvailable(this)) {
            builder.setNeutralButton(R.string.usePhoneLock, (d, w) -> {
                dismissCodeDialog();
                showBiometricPrompt();
            });
        }
        mCodeDialog = builder.create();
        mCodeDialog.show();
        mCodeDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (AppCodeUtils.cooldownRemainingMs() > 0) {
                showShortToast(getApplicationContext(), R.string.codeCooldown);
                return;
            }
            String entered = codeBox.getText().toString();
            Button positive = mCodeDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            // The rounds run off the main thread so the button keeps drawing while they grind; the
            // answer comes back on the main thread, where the counters and the screen are touched.
            positive.setEnabled(false);
            AppCodeUtils.verifyCodeAsync(entered, ok -> {
                if (isFinishing()
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())
                        || mCodeDialog == null || !mCodeDialog.isShowing()) {
                    return;
                }
                positive.setEnabled(true);
                if (ok) {
                    AppCodeUtils.noteSuccess();
                    if (!answerIsForCurrentQuestion()) {
                        dismissCodeDialog();
                        askAgainIfStillTheScreenInTheFront();
                        return;
                    }
                    ProcessSharedState.setPackageUnlocked(mPromptedTargetPkg);
                    resetLockState(AppLockActivity.this);
                    dismissCodeDialog();
                    finishImmediately(RESULT_OK);
                } else {
                    AppCodeUtils.noteFailure();
                    codeBox.setText("");
                    showShortToast(getApplicationContext(),
                            AppCodeUtils.cooldownRemainingMs() > 0
                                    ? R.string.codeCooldown : R.string.wrongCode);
                }
            });
        });
    }

    private void dismissCodeDialog() {
        if (mCodeDialog != null) {
            mCodeDialog.dismiss();
            mCodeDialog = null;
        }
    }

    /** A refusal of the code question: home for an interception, a cancelled result for own lock. */
    private void refuseCode() {
        dismissCodeDialog();
        setResult(RESULT_CANCELED);
        if (!dismissWithoutRevealingTarget()) {
            finishImmediately(RESULT_CANCELED);
        }
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
        moveTaskToBack(true);
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
        dismissCodeDialog();
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
                        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                            return;
                        }

                        if (getIntent().getBooleanExtra("forceBiometricPrompt", false)) {
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
                            finishImmediately(RESULT_CANCELED);
                            return;
                        }

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

                        if (AppCodeUtils.hasCode()) {
                            showCodeDialog();
                            return;
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
                        // round to it, and by then this screen may be asking about another
                        // application or the session this question was stamped in may have ended.
                        if (!answerIsForCurrentQuestion()) {
                            // Nothing is handed over, and the user is not sent away either: this
                            // screen is the one thing standing between the application and whoever is
                            // holding the phone, so it asks again - about the application on it now.
                            askAgainIfStillTheScreenInTheFront();
                            return;
                        }

                        // The application the user is now going into is open for as long as it stays
                        // the window in front; the accessibility service takes that back the moment
                        // it is not.
                        ProcessSharedState.setPackageUnlocked(mPromptedTargetPkg);

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

    @Override
    protected boolean screenLockEnabled() {
        // This screen is raised by the entry, by a shortcut and by the interception alike. It reads
        // its own timeout against authentication alone, so an entry opted out does not let the
        // shortcut or the application behind it slip through the re-check on resume.
        return isAuthenticationEnabled();
    }

    @Override
    public void applyRecentsVisibility() {
        super.applyRecentsVisibility();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
}
