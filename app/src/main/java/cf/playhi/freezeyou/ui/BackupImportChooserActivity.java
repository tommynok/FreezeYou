package cf.playhi.freezeyou.ui;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.ListView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.adapter.BackupImportChooserActivitySwitchSimpleAdapter;
import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.utils.BackupUtils;
import cf.playhi.freezeyou.utils.ToastUtils;

import static cf.playhi.freezeyou.utils.ThemeUtils.processActionBar;
import static cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme;
import static cf.playhi.freezeyou.utils.ThemeUtils.recreateAllActivities;

public class BackupImportChooserActivity extends FreezeYouBaseActivity {

    /**
     * One switch per meaningful group of data instead of one per stored key: the previous screen
     * listed every single setting separately (40+ rows), which is far more granularity than
     * anyone restoring a backup wants to work through.
     */
    private static final class ImportGroup {
        private final int titleStringId;
        private final String[] categories;

        private ImportGroup(int titleStringId, String... categories) {
            this.titleStringId = titleStringId;
            this.categories = categories;
        }
    }

    private static final ImportGroup[] IMPORT_GROUPS = new ImportGroup[]{
            new ImportGroup(R.string.moreSettings,
                    "generalSettings_boolean", "generalSettings_string", "generalSettings_int"),
            new ImportGroup(R.string.backupGroupOneKeyLists, "oneKeyList"),
            new ImportGroup(R.string.scheduledTasks,
                    "userTimeScheduledTasks", "userTriggerScheduledTasks"),
            new ImportGroup(R.string.myCustomization, "userDefinedCategories"),
            new ImportGroup(R.string.backupGroupAllowLists,
                    "uriAutoAllowPkgs_allows", "installPkgs_autoAllowPkgs_allows")
    };

    private final ExecutorService mLoadExecutor = Executors.newSingleThreadExecutor();
    private ProgressDialog mProgressDialog;
    private Button mFinishButton;
    private Button mCancelButton;
    private volatile boolean mImporting;
    private String mCacheFileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        processSetTheme(this);
        super.onCreate(savedInstanceState);
        processActionBar(getSupportActionBar());
        setContentView(R.layout.bica_main);

        Intent intent = getIntent();
        String requestedCacheFile =
                intent == null ? null : intent.getStringExtra(BackupUtils.EXTRA_RESTORE_CACHE_FILE);
        mCacheFileName = isRestoreCacheFileName(requestedCacheFile) ? requestedCacheFile : null;

        mFinishButton = findViewById(R.id.bica_finish_button);
        mCancelButton = findViewById(R.id.bica_cancel_button);
        mFinishButton.setEnabled(false);
        mCancelButton.setOnClickListener(v -> {
            if (!mImporting) finish();
        });
        mFinishButton.setOnClickListener(v -> startImport());

        loadBackupAsync();
    }

    @Override
    protected void onDestroy() {
        mLoadExecutor.shutdownNow();
        dismissProgress();
        if (isFinishing()) {
            deleteRestoreCache();
        }
        super.onDestroy();
    }

    /**
     * The restore writes settings and databases in the background; leaving the screen would not
     * stop it, so back and up are ignored until the import reports back.
     */
    @Override
    public void onBackPressed() {
        if (mImporting) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mImporting && item.getItemId() == android.R.id.home) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static boolean isRestoreCacheFileName(String fileName) {
        return fileName != null
                && fileName.startsWith(BackupUtils.RESTORE_CACHE_FILE_PREFIX)
                && fileName.endsWith(".json")
                && fileName.equals(new File(fileName).getName());
    }

    private void loadBackupAsync() {
        if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
            return;
        }
        showProgress();
        mLoadExecutor.execute(() -> {
            JSONObject jsonObject = null;
            boolean readFailed = false;
            String content = null;
            try {
                content = readRestoreCache();
            } catch (Exception | OutOfMemoryError e) {
                e.printStackTrace();
                readFailed = true;
            }
            if (!readFailed) {
                if (content == null) {
                    readFailed = true;
                } else {
                    try {
                        jsonObject = new JSONObject(content);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    } catch (OutOfMemoryError e) {
                        e.printStackTrace();
                        readFailed = true;
                    }
                }
            }
            final JSONObject parsed = jsonObject;
            final boolean failed = readFailed;
            final ArrayList<HashMap<String, String>> titleAndCategoriesArrayList =
                    new ArrayList<>();
            final boolean hasRestorableEntries = parsed != null
                    && generateGroupList(parsed, titleAndCategoriesArrayList) > 0;
            runOnUiThread(() -> {
                dismissProgress();
                if (isFinishing() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                    return;
                }
                if (failed) {
                    showPlaceholder(getString(R.string.failed));
                } else if (parsed == null) {
                    showPlaceholder(getString(R.string.parseFailed));
                } else {
                    showContents(parsed, titleAndCategoriesArrayList, hasRestorableEntries);
                }
            });
        });
    }

    private void showContents(JSONObject jsonObject,
                              ArrayList<HashMap<String, String>> titleAndCategoriesArrayList,
                              boolean hasRestorableEntries) {
        final ListView mainListView = findViewById(R.id.bica_main_listView);
        if (titleAndCategoriesArrayList.isEmpty()) {
            addPlaceholderRow(titleAndCategoriesArrayList, getString(R.string.nothing));
        }
        mainListView.setAdapter(new BackupImportChooserActivitySwitchSimpleAdapter(
                this,
                jsonObject,
                titleAndCategoriesArrayList,
                R.layout.bica_list_item,
                new String[]{"title"},
                new int[]{R.id.bica_list_item_switch}));
        mFinishButton.setEnabled(hasRestorableEntries);
    }

    private void showPlaceholder(String title) {
        final ArrayList<HashMap<String, String>> rows = new ArrayList<>();
        addPlaceholderRow(rows, title);
        ((ListView) findViewById(R.id.bica_main_listView)).setAdapter(
                new BackupImportChooserActivitySwitchSimpleAdapter(
                        this,
                        null,
                        rows,
                        R.layout.bica_list_item,
                        new String[]{"title"},
                        new int[]{R.id.bica_list_item_switch}));
        mFinishButton.setEnabled(false);
    }

    private void addPlaceholderRow(ArrayList<HashMap<String, String>> list, String title) {
        HashMap<String, String> keyValuePair = new HashMap<>();
        keyValuePair.put("title", title);
        keyValuePair.put("categories", "Failed!");
        keyValuePair.put("category", "Failed!");
        list.add(keyValuePair);
    }

    private void startImport() {
        if (mImporting) {
            return;
        }
        final ListView mainListView = findViewById(R.id.bica_main_listView);
        final BackupImportChooserActivitySwitchSimpleAdapter adapter =
                (BackupImportChooserActivitySwitchSimpleAdapter) mainListView.getAdapter();
        if (adapter == null) {
            return;
        }
        final JSONObject selectedContents = adapter.getFinalList();
        final Context appContext = getApplicationContext();
        mImporting = true;
        mFinishButton.setEnabled(false);
        mCancelButton.setEnabled(false);
        new Thread(() -> {
            final boolean imported =
                    BackupUtils.importContents(appContext, this, selectedContents);
            final boolean changesAppearance = imported
                    && BackupUtils.importChangesAppearance(appContext, selectedContents);
            runOnUiThread(() -> {
                if (isFinishing()
                        || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                    return;
                }
                mImporting = false;
                ToastUtils.showToast(
                        BackupImportChooserActivity.this,
                        imported ? R.string.finish : R.string.failed);
                // This screen reports its own result, so it finishes before anything is recreated:
                // the helper below skips a finishing activity, and being recreated here would take
                // the report away from the screen that just gave it.
                finish();
                if (changesAppearance) {
                    // The restored theme and language are already in storage, but a live screen
                    // keeps the appearance it was created with. Without this the user reads a
                    // successful restore as one that did nothing until the app is restarted.
                    recreateAllActivities(appContext);
                }
            });
        }, "backup-restore").start();
    }

    private String readRestoreCache() throws IOException {
        if (mCacheFileName == null) {
            return null;
        }
        File file = new File(getCacheDir(), mCacheFileName);
        if (!file.isFile()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = new FileInputStream(file)) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, "UTF-8"));
            char[] buffer = new char[8192];
            int n;
            while ((n = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, n);
                if (builder.length() > BackupUtils.MAX_RESTORE_JSON_CHARS) {
                    throw new IOException("Restore cache is too large");
                }
            }
        }
        return builder.toString();
    }

    private void deleteRestoreCache() {
        if (mCacheFileName != null) {
            new File(getCacheDir(), mCacheFileName).delete();
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

    /**
     * @return the total number of entries the importers would apply across all groups; zero means
     * there is nothing to restore, even if every known category is present but unusable.
     */
    private int generateGroupList(JSONObject jsonObject, ArrayList<HashMap<String, String>> list) {
        int totalEntries = 0;
        for (ImportGroup group : IMPORT_GROUPS) {
            int itemCount = 0;
            StringBuilder presentCategories = new StringBuilder();
            for (String category : group.categories) {
                int count = BackupUtils.countImportableEntries(this, jsonObject, category);
                if (count < 0) continue;
                itemCount += count;
                if (presentCategories.length() > 0) presentCategories.append(",");
                presentCategories.append(category);
            }

            if (presentCategories.length() == 0) continue;

            totalEntries += itemCount;
            HashMap<String, String> keyValuePair = new HashMap<>();
            keyValuePair.put(
                    "title",
                    getString(R.string.backupGroupWithCount,
                            getString(group.titleStringId), itemCount)
            );
            keyValuePair.put("categories", presentCategories.toString());
            list.add(keyValuePair);
        }
        return totalEntries;
    }

}
