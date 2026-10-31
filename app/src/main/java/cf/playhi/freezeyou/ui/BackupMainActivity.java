package cf.playhi.freezeyou.ui;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.utils.BackupUtils;
import cf.playhi.freezeyou.utils.ClipboardUtils;
import cf.playhi.freezeyou.utils.GZipUtils;
import cf.playhi.freezeyou.utils.ToastUtils;

import static cf.playhi.freezeyou.utils.BackupUtils.getExportContent;
import static cf.playhi.freezeyou.utils.ThemeUtils.processActionBar;
import static cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme;

public class BackupMainActivity extends FreezeYouBaseActivity {

    /**
     * Files hold exactly what the text field holds (gzip + Base64), so a backup copied to the
     * clipboard by an older version can be pasted into a file and restored, and vice versa.
     */
    private static final String BACKUP_MIME_TYPE = "application/octet-stream";

    private static final int MAX_BACKUP_FILE_CHARS = 16 * 1024 * 1024;

    private final ExecutorService mBackupExecutor = Executors.newSingleThreadExecutor();
    private ProgressDialog mProgressDialog;

    private ActivityResultLauncher<Intent> mSaveToFileLauncher;
    private ActivityResultLauncher<Intent> mRestoreFromFileLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        processSetTheme(this);
        super.onCreate(savedInstanceState);
        processActionBar(getSupportActionBar());
        setContentView(R.layout.bma_main);

        registerFileLaunchers();
        initButtons();
    }

    @Override
    protected void onDestroy() {
        mBackupExecutor.shutdownNow();
        dismissProgress();
        super.onDestroy();
    }

    private void registerFileLaunchers() {
        mSaveToFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        writeBackupToUri(uri);
                    }
                });

        mRestoreFromFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                        return;
                    }
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        readBackupFromUri(uri);
                    }
                });
    }

    private void initButtons() {
        Button bma_main_saveToFile_button = findViewById(R.id.bma_main_saveToFile_button);
        Button bma_main_restoreFromFile_button = findViewById(R.id.bma_main_restoreFromFile_button);
        Button bma_main_export_button = findViewById(R.id.bma_main_export_button);
        Button bma_main_import_button = findViewById(R.id.bma_main_import_button);
        Button bma_main_copy_button = findViewById(R.id.bma_main_copy_button);
        Button bma_main_paste_button = findViewById(R.id.bma_main_paste_button);

        // The storage-access-framework pickers the file buttons rely on only exist on API 19+;
        // below that the clipboard path stays as the only way to move a backup around.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            bma_main_saveToFile_button.setVisibility(View.GONE);
            bma_main_restoreFromFile_button.setVisibility(View.GONE);
            findViewById(R.id.bma_main_viaText_textView).setVisibility(View.GONE);
        }

        bma_main_saveToFile_button.setOnClickListener(v -> launchPicker(
                mSaveToFileLauncher,
                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(BACKUP_MIME_TYPE)
                        .putExtra(Intent.EXTRA_TITLE, generateBackupFileName())
        ));

        bma_main_restoreFromFile_button.setOnClickListener(v -> launchPicker(
                mRestoreFromFileLauncher,
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*")
        ));

        bma_main_export_button.setOnClickListener(v -> {
            EditText editText = findViewById(R.id.bma_main_inputAndoutput_editText);
            editText.setText(GZipUtils.gzipCompress(getExportContent(getApplicationContext())));
            editText.selectAll();
        });

        bma_main_import_button.setOnClickListener(v -> {
            EditText editText = findViewById(R.id.bma_main_inputAndoutput_editText);
            startImportChooser(editText.getText().toString());
        });

        bma_main_copy_button.setOnClickListener(v -> {
            EditText editText = findViewById(R.id.bma_main_inputAndoutput_editText);
            if (ClipboardUtils.copyToClipboard(getApplicationContext(), editText.getText().toString())) {
                ToastUtils.showToast(BackupMainActivity.this, R.string.success);
            } else {
                ToastUtils.showToast(BackupMainActivity.this, R.string.failed);
            }
        });

        bma_main_paste_button.setOnClickListener(v -> {
            EditText editText = findViewById(R.id.bma_main_inputAndoutput_editText);
            editText.setText(ClipboardUtils.getClipboardItemText(getApplicationContext()));
        });

    }

    /**
     * The document picker lives in a separate app (DocumentsUI) that this very app is capable of
     * freezing or disabling — in which case launching it throws instead of opening anything. Fall
     * back to a toast and the clipboard route rather than taking the whole activity down.
     */
    private void launchPicker(ActivityResultLauncher<Intent> launcher, Intent intent) {
        if (intent.resolveActivity(getPackageManager()) == null) {
            ToastUtils.showToast(BackupMainActivity.this, R.string.failed);
            return;
        }
        try {
            launcher.launch(intent);
        } catch (Exception e) {
            e.printStackTrace();
            ToastUtils.showToast(BackupMainActivity.this, R.string.failed);
        }
    }

    private static String generateBackupFileName() {
        return "FreezeYou-backup-"
                + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(new Date())
                + ".fybak";
    }

    private void startImportChooser(String compressedContent) {
        if (compressedContent.isEmpty() || compressedContent.length() > MAX_BACKUP_FILE_CHARS) {
            ToastUtils.showToast(BackupMainActivity.this, R.string.parseFailed);
            return;
        }
        processBackupAsync(() -> GZipUtils.gzipDecompress(compressedContent));
    }

    private void readBackupFromUri(Uri uri) {
        final ContentResolver contentResolver = getContentResolver();
        processBackupAsync(() -> {
            StringBuilder builder = new StringBuilder();
            try (InputStream inputStream = contentResolver.openInputStream(uri)) {
                if (inputStream == null) {
                    throw new IOException("Cannot open the selected backup file");
                }
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(inputStream, "UTF-8"));
                char[] buffer = new char[8192];
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    builder.append(buffer, 0, n);
                    if (builder.length() > MAX_BACKUP_FILE_CHARS) {
                        throw new IOException("Backup file is too large");
                    }
                }
            }
            return GZipUtils.gzipDecompress(builder.toString());
        });
    }

    private void processBackupAsync(Callable<String> reader) {
        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return;
        }
        showProgress();
        mBackupExecutor.execute(() -> {
            String json = null;
            boolean readFailed = false;
            try {
                json = reader.call();
            } catch (Exception | OutOfMemoryError e) {
                e.printStackTrace();
                readFailed = true;
            }
            final String cacheFileName = !readFailed && json != null
                    ? writeRestoreCache(json) : null;
            final boolean failed = readFailed;
            runOnUiThread(() -> {
                dismissProgress();
                if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                    // No chooser will open to consume and delete this payload.
                    if (cacheFileName != null) {
                        new File(getCacheDir(), cacheFileName).delete();
                    }
                    return;
                }
                if (failed) {
                    ToastUtils.showToast(BackupMainActivity.this, R.string.failed);
                    return;
                }
                if (cacheFileName == null) {
                    ToastUtils.showToast(BackupMainActivity.this, R.string.parseFailed);
                    return;
                }
                try {
                    startActivity(
                            new Intent(BackupMainActivity.this, BackupImportChooserActivity.class)
                                    .putExtra(BackupUtils.EXTRA_RESTORE_CACHE_FILE, cacheFileName)
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    new File(getCacheDir(), cacheFileName).delete();
                    ToastUtils.showToast(BackupMainActivity.this, R.string.failed);
                }
            });
        });
    }

    private String writeRestoreCache(String json) {
        if (json.isEmpty()) {
            return null;
        }
        final String fileName =
                BackupUtils.RESTORE_CACHE_FILE_PREFIX + UUID.randomUUID() + ".json";
        File file = new File(getCacheDir(), fileName);
        try (OutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(json.getBytes("UTF-8"));
            outputStream.flush();
            return fileName;
        } catch (Exception | OutOfMemoryError e) {
            e.printStackTrace();
            file.delete();
            return null;
        }
    }

    private void showProgress() {
        dismissProgress();
        mProgressDialog = ProgressDialog.show(
                this, getString(R.string.plsWait), getString(R.string.loading___));
    }

    private void dismissProgress() {
        if (mProgressDialog != null) {
            if (mProgressDialog.isShowing()) {
                mProgressDialog.dismiss();
            }
            mProgressDialog = null;
        }
    }

    private void writeBackupToUri(Uri uri) {
        String content = GZipUtils.gzipCompress(getExportContent(getApplicationContext()));
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri)) {
            if (outputStream == null) {
                ToastUtils.showToast(BackupMainActivity.this, R.string.failed);
                return;
            }
            outputStream.write(content.getBytes("UTF-8"));
            outputStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
            ToastUtils.showToast(BackupMainActivity.this, R.string.failed);
            return;
        }
        // Deliberately not mirrored into the text field: it sits in a wrap_content ScrollView,
        // so a large backup would be laid out in full at once and freeze the UI for seconds.
        ToastUtils.showToast(BackupMainActivity.this, R.string.success);
    }
}
