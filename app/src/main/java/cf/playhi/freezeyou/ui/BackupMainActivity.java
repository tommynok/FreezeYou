package cf.playhi.freezeyou.ui;

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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
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
        String decompressed = GZipUtils.gzipDecompress(compressedContent);
        if ("".equals(decompressed)) {
            ToastUtils.showToast(BackupMainActivity.this, R.string.parseFailed);
            return;
        }
        startActivity(
                new Intent(BackupMainActivity.this, BackupImportChooserActivity.class)
                        .putExtra("jsonObjectString", decompressed)
        );
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

    private void readBackupFromUri(Uri uri) {
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) {
                ToastUtils.showToast(BackupMainActivity.this, R.string.failed);
                return;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
            ToastUtils.showToast(BackupMainActivity.this, R.string.failed);
            return;
        }
        startImportChooser(builder.toString());
    }
}
