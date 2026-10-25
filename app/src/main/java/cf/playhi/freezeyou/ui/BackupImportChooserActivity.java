package cf.playhi.freezeyou.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.adapter.BackupImportChooserActivitySwitchSimpleAdapter;
import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys;
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys;
import cf.playhi.freezeyou.utils.BackupUtils;
import cf.playhi.freezeyou.utils.ToastUtils;

import static cf.playhi.freezeyou.utils.ThemeUtils.processActionBar;
import static cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme;

public class BackupImportChooserActivity extends FreezeYouBaseActivity {

    /**
     * Exported but never written back: the icons and the app lock are device-specific and are
     * restored through their own flows, so counting them would overstate the restore.
     */
    private static final String[] NON_RESTORABLE_BOOLEAN_KEYS = new String[]{
            DefaultSharedPreferenceStorageBooleanKeys.firstIconEnabled.name(),
            DefaultSharedPreferenceStorageBooleanKeys.secondIconEnabled.name(),
            DefaultSharedPreferenceStorageBooleanKeys.thirdIconEnabled.name(),
            DefaultMultiProcessMMKVStorageBooleanKeys.enableAuthentication.name()
    };

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        processSetTheme(this);
        super.onCreate(savedInstanceState);
        processActionBar(getSupportActionBar());
        setContentView(R.layout.bica_main);

        onCreateInit();
    }

    private void onCreateInit() {

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        final ListView mainListView = findViewById(R.id.bica_main_listView);
        final ArrayList<HashMap<String, String>> titleAndCategoriesArrayList = new ArrayList<>();

        String jsonContentString = intent.getStringExtra("jsonObjectString");
        JSONObject jsonObject = null;
        boolean hasRestorableEntries = false;
        if (jsonContentString == null) {
            addPlaceholderRow(titleAndCategoriesArrayList, getString(R.string.failed));
        } else {
            try {
                jsonObject = new JSONObject(jsonContentString);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (jsonObject == null) {
                addPlaceholderRow(titleAndCategoriesArrayList, getString(R.string.parseFailed));
            } else {
                hasRestorableEntries = generateGroupList(jsonObject, titleAndCategoriesArrayList) > 0;
                if (titleAndCategoriesArrayList.size() == 0) {
                    addPlaceholderRow(titleAndCategoriesArrayList, getString(R.string.nothing));
                }
            }
        }

        final BackupImportChooserActivitySwitchSimpleAdapter adapter =
                new BackupImportChooserActivitySwitchSimpleAdapter(
                        this,
                        jsonObject,
                        titleAndCategoriesArrayList,
                        R.layout.bica_list_item,
                        new String[]{"title"},
                        new int[]{R.id.bica_list_item_switch});

        mainListView.setAdapter(adapter);

        processButtons(hasRestorableEntries);
    }

    private void addPlaceholderRow(ArrayList<HashMap<String, String>> list, String title) {
        HashMap<String, String> keyValuePair = new HashMap<>();
        keyValuePair.put("title", title);
        keyValuePair.put("categories", "Failed!");
        keyValuePair.put("category", "Failed!");
        list.add(keyValuePair);
    }

    private void processButtons(boolean hasRestorableEntries) {
        final Button bicaFinishButton = findViewById(R.id.bica_finish_button);
        final Button bicaCancelButton = findViewById(R.id.bica_cancel_button);
        bicaFinishButton.setEnabled(hasRestorableEntries);
        bicaCancelButton.setOnClickListener(v -> finish());
        bicaFinishButton.setOnClickListener(v -> {
            final ListView mainListView = findViewById(R.id.bica_main_listView);
            BackupImportChooserActivitySwitchSimpleAdapter adapter =
                    (BackupImportChooserActivitySwitchSimpleAdapter) mainListView.getAdapter();
            final JSONObject selectedContents = adapter.getFinalList();
            final Context appContext = getApplicationContext();
            bicaFinishButton.setEnabled(false);
            bicaCancelButton.setEnabled(false);
            new Thread(() -> {
                final boolean imported =
                        BackupUtils.importContents(appContext, this, selectedContents);
                runOnUiThread(() -> {
                    if (isFinishing()
                            || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed())) {
                        return;
                    }
                    ToastUtils.showToast(
                            BackupImportChooserActivity.this,
                            imported ? R.string.finish : R.string.failed);
                    finish();
                });
            }, "backup-restore").start();
        });
    }

    /**
     * @return the total number of entries listed across all groups; zero means there is nothing
     * the backup can restore, even if every known category is present but empty.
     */
    private int generateGroupList(JSONObject jsonObject, ArrayList<HashMap<String, String>> list) {
        int totalEntries = 0;
        for (ImportGroup group : IMPORT_GROUPS) {
            int itemCount = 0;
            StringBuilder presentCategories = new StringBuilder();
            for (String category : group.categories) {
                int count = countEntries(jsonObject, category);
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

    /**
     * @return how many restorable entries the category holds, or -1 when it isn't in the backup
     * at all. Settings-style categories store one object whose keys are the entries; task and
     * list categories store one array element per entry.
     */
    static int countEntries(JSONObject jsonObject, String category) {
        JSONArray array = jsonObject.optJSONArray(category);
        if (array == null) {
            return -1;
        }
        switch (category) {
            case "generalSettings_boolean": {
                JSONObject jsonObj = array.optJSONObject(0);
                if (jsonObj == null) {
                    return -1;
                }
                int count = jsonObj.length();
                for (String skippedKey : NON_RESTORABLE_BOOLEAN_KEYS) {
                    if (jsonObj.has(skippedKey)) {
                        count--;
                    }
                }
                return Math.max(count, 0);
            }
            case "generalSettings_string":
            case "generalSettings_int":
            case "oneKeyList":
            case "uriAutoAllowPkgs_allows":
            case "installPkgs_autoAllowPkgs_allows":
                JSONObject jsonObj = array.optJSONObject(0);
                return jsonObj == null ? -1 : jsonObj.length();
            default:
                return array.length();
        }
    }

}
