package cf.playhi.freezeyou;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.service.OneKeyFreezeService;
import cf.playhi.freezeyou.ui.AppLockActivity;
import cf.playhi.freezeyou.utils.ServiceUtils;
import cf.playhi.freezeyou.utils.ShortcutTokenUtils;

import static cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilderKt.FreezeYouAlertDialogBuilder;
import static cf.playhi.freezeyou.utils.AuthenticationUtils.isShortcutProtectionEnabled;
import static cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme;

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
public class OneKeyFreeze extends FreezeYouBaseActivity {
    private static final int REQUEST_UNLOCK = 1;
    private AlertDialog confirmationDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!getIntent().hasExtra(ShortcutTokenUtils.EXTRA_AUTH_TOKEN)) {
            processSetTheme(this, true);
        }
        super.onCreate(savedInstanceState);
        handleIntent();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (confirmationDialog != null) {
            confirmationDialog.dismiss();
            confirmationDialog = null;
        }
        setIntent(intent);
        handleIntent();
    }

    private void handleIntent() {
        if (!getIntent().hasExtra(ShortcutTokenUtils.EXTRA_AUTH_TOKEN)) {
            return;
        }
        if (!ShortcutTokenUtils.verifyToken(getIntent(), ShortcutTokenUtils.ACTION_ONE_KEY_FREEZE)) {
            finish();
            return;
        }
        if (isLocked()) {
            startActivityForResult(
                    new Intent(this, AppLockActivity.class)
                            .putExtra("ignoreCurrentUnlockStatus", true),
                    REQUEST_UNLOCK);
            return;
        }
        executeAction();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_UNLOCK) {
            if (resultCode == RESULT_OK) {
                executeAction();
            } else {
                finish();
            }
        }
    }

    @Override
    protected boolean activityNeedCheckAppLock() {
        // The shortcut asks for the code itself before acting, so the resume gate must not raise a
        // second lock screen over the one handleIntent already put up.
        return false;
    }

    @Override
    protected boolean screenLockEnabled() {
        return isShortcutProtectionEnabled();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing() || isLocked() || confirmationDialog != null) {
            return;
        }
        final Intent pendingIntent = getIntent();
        String message = getString(R.string.oneKeyFreeze);
        if (pendingIntent.getBooleanExtra("autoCheckAndLockScreen", true)) {
            message += "\n" + getString(R.string.oneKeyLockScreen);
        }
        confirmationDialog = FreezeYouAlertDialogBuilder(this)
                .setTitle(R.string.plsConfirm)
                .setMessage(message)
                .setPositiveButton(R.string.okay, (dialog, which) -> {
                    if (getIntent() != pendingIntent || isFinishing()) {
                        return;
                    }
                    if (!isLocked()) {
                        executeAction();
                    } else {
                        finish();
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> finish())
                .setOnCancelListener(dialog -> finish())
                .show();
    }

    @Override
    protected void onDestroy() {
        if (confirmationDialog != null) {
            confirmationDialog.dismiss();
        }
        super.onDestroy();
    }

    private void executeAction() {
        ServiceUtils.startService(
                this,
                new Intent(getApplicationContext(), OneKeyFreezeService.class)
                        .putExtra("autoCheckAndLockScreen", getIntent().getBooleanExtra("autoCheckAndLockScreen", true)));
        finish();
    }
}
