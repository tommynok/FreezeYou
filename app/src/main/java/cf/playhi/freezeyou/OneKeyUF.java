package cf.playhi.freezeyou;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.service.OneKeyUFService;
import cf.playhi.freezeyou.utils.ServiceUtils;
import cf.playhi.freezeyou.utils.ShortcutTokenUtils;

import static cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilderKt.FreezeYouAlertDialogBuilder;
import static cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme;

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
public class OneKeyUF extends FreezeYouBaseActivity {
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
        if (!ShortcutTokenUtils.verifyToken(getIntent(), ShortcutTokenUtils.ACTION_ONE_KEY_UF)) {
            finish();
            return;
        }
        executeAction();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing() || isLocked() || confirmationDialog != null) {
            return;
        }
        final Intent pendingIntent = getIntent();
        confirmationDialog = FreezeYouAlertDialogBuilder(this)
                .setTitle(R.string.plsConfirm)
                .setMessage(R.string.oneKeyUF)
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
        ServiceUtils.startService(this, new Intent(getApplicationContext(), OneKeyUFService.class));
        finish();
    }
}
