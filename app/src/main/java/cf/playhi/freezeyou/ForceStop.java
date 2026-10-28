package cf.playhi.freezeyou;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.service.ForceStopService;
import cf.playhi.freezeyou.utils.ServiceUtils;
import cf.playhi.freezeyou.utils.ShortcutTokenUtils;

import static cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilderKt.FreezeYouAlertDialogBuilder;
import static cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme;
import static cf.playhi.freezeyou.utils.ToastUtils.showToast;

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
public class ForceStop extends FreezeYouBaseActivity {
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
        if (!ShortcutTokenUtils.verifyToken(getIntent(), ShortcutTokenUtils.ACTION_FORCE_STOP)) {
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
        String pkgName = pendingIntent.getStringExtra("pkgName");
        if (pkgName == null || pkgName.isEmpty()) {
            finish();
            return;
        }
        confirmationDialog = FreezeYouAlertDialogBuilder(this)
                .setTitle(R.string.plsConfirm)
                .setMessage(getString(R.string.forceStop) + "\n" + pkgName)
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
        String pkgName = getIntent().getStringExtra("pkgName");

        if (pkgName == null || pkgName.equals("")) {
            finish();
            return;
        }

        String[] packages = null;

        if (pkgName.startsWith("FORCESTOPCATEGORY")) {
            String categoryLabel = pkgName.substring("FORCESTOPCATEGORY".length());

            SQLiteDatabase userDefinedDb = openOrCreateDatabase("userDefinedCategories", Context.MODE_PRIVATE, null);
            userDefinedDb.execSQL(
                    "create table if not exists categories(_id integer primary key autoincrement,label varchar,packages varchar)"
            );
            Cursor cursor = null;
            try {
                cursor = userDefinedDb.query(
                        "categories",
                        new String[]{"packages"},
                        "label = ?",
                        new String[]{categoryLabel},
                        null, null, null, null
                );
                if (cursor != null && cursor.moveToFirst()) {
                    int colIndex = cursor.getColumnIndex("packages");
                    if (colIndex >= 0) {
                        String pkgString = cursor.getString(colIndex);
                        if (pkgString != null) {
                            packages = pkgString.split(",");
                        }
                    }
                } else {
                    showToast(this, R.string.failed);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                userDefinedDb.close();
            }
        } else {
            packages = new String[] {pkgName};
        }

        if (packages != null) {
            ServiceUtils.startService(
                    this,
                    new Intent(getApplicationContext(), ForceStopService.class)
                            .putExtra("packages", packages)
            );
        }
        finish();
    }
}
