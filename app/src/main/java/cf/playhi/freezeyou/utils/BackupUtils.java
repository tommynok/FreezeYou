package cf.playhi.freezeyou.utils;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.storage.key.AbstractKey;
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys;
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys;
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys;
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageStringKeys;
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage;

import static android.content.Context.MODE_PRIVATE;

// Note: Backup/restore schema and process is complex. Version flag in backup format can be used to distinguish schemas.
public final class BackupUtils {

    /**
     * The restore source travels to the chooser through this app-private cache file: a backup can
     * be larger than the Binder transaction limit that caps Intent extras, and rejecting it would
     * break restoring backups this very app exported.
     */
    public static final String RESTORE_CACHE_FILE_NAME = "restore-backup.json";

    public static final int MAX_RESTORE_JSON_CHARS = 16 * 1024 * 1024;

    /**
     * Exported but never written back: the icons and the app lock are device-specific and are
     * restored through their own flows, so they must not be counted as restorable either.
     */
    private static final String[] NON_RESTORABLE_BOOLEAN_KEYS = new String[]{
            DefaultSharedPreferenceStorageBooleanKeys.firstIconEnabled.name(),
            DefaultSharedPreferenceStorageBooleanKeys.secondIconEnabled.name(),
            DefaultSharedPreferenceStorageBooleanKeys.thirdIconEnabled.name(),
            DefaultMultiProcessMMKVStorageBooleanKeys.enableAuthentication.name()
    };

    private static void importStringSharedPreference(
            Context context, Activity activity, JSONObject jsonObject,
            SharedPreferences sp, String key) {
        sp.edit().putString(key, jsonObject.optString(key, "")).apply();
        SettingsUtils.checkPreferenceData(context, activity, sp, key, true);
    }

    private static void importBooleanSharedPreference(
            Context context, Activity activity, JSONObject jsonObject,
            SharedPreferences sp, String key) {
        sp.edit().putBoolean(key, jsonObject.optBoolean(key, false)).apply();
        SettingsUtils.checkPreferenceData(context, activity, sp, key, true);
    }

    private static void importIntSharedPreference(
            Context context, Activity activity, JSONObject jsonObject,
            SharedPreferences sp, String key) {
        sp.edit().putInt(key, jsonObject.optInt(key, 0)).apply();
        SettingsUtils.checkPreferenceData(context, activity, sp, key, true);
    }

    private static boolean importUserTimeTasksJSONArray(Context context, JSONObject jsonObject) {
        JSONArray userTimeScheduledTasksJSONArray =
                jsonObject.optJSONArray("userTimeScheduledTasks");
        if (userTimeScheduledTasksJSONArray == null) {
            return false;
        }

        SQLiteDatabase db = context.openOrCreateDatabase("scheduledTasks", MODE_PRIVATE, null);
        db.execSQL(
                "create table if not exists tasks(_id integer primary key autoincrement,hour integer(2),minutes integer(2),repeat varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
        );

        boolean sawValidEntry = false;
        JSONObject oneUserTimeScheduledTaskJSONObject;
        for (int i = 0; i < userTimeScheduledTasksJSONArray.length(); ++i) {
            try {
                oneUserTimeScheduledTaskJSONObject = userTimeScheduledTasksJSONArray.optJSONObject(i);
                if (oneUserTimeScheduledTaskJSONObject == null) {
                    continue;
                }
                if (oneUserTimeScheduledTaskJSONObject.optBoolean("doNotImport", false)) {
                    continue;
                }
                // Read back out of a file someone can edit, and here these were pasted between
                // quotes into SQL: a stray apostrophe ended the literal and the restore threw.
                int hour = oneUserTimeScheduledTaskJSONObject.getInt("hour");
                int minutes = oneUserTimeScheduledTaskJSONObject.getInt("minutes");
                String repeat = oneUserTimeScheduledTaskJSONObject.getString("repeat");
                int enabled = oneUserTimeScheduledTaskJSONObject.getInt("enabled");
                String label = oneUserTimeScheduledTaskJSONObject.getString("label");
                String task = oneUserTimeScheduledTaskJSONObject.getString("task");

                if (!TasksUtils.isValidImportedTimeTask(hour, minutes, repeat, task, enabled)) {
                    continue;
                }
                if (repeat == null || repeat.isEmpty()) {
                    repeat = "0";
                }
                sawValidEntry = true;

                Cursor cursor = null;
                boolean exists = false;
                try {
                    cursor = db.query(
                            "tasks",
                            new String[]{"_id"},
                            "hour = ? AND minutes = ? AND COALESCE(repeat, '') = ? AND enabled = ? AND COALESCE(label, '') = ? AND COALESCE(task, '') = ?",
                            new String[]{
                                    String.valueOf(hour),
                                    String.valueOf(minutes),
                                    repeat == null ? "" : repeat,
                                    String.valueOf(enabled),
                                    label == null ? "" : label,
                                    task == null ? "" : task
                            },
                            null, null, null, "1"
                    );
                    exists = (cursor != null && cursor.moveToFirst());
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }

                if (exists) {
                    continue;
                }

                ContentValues values = new ContentValues();
                values.put("hour", hour);
                values.put("minutes", minutes);
                values.put("repeat", repeat);
                values.put("enabled", enabled);
                values.put("label", label);
                values.put("task", task);
                values.put("column1", "");
                values.put("column2", "");
                db.insert("tasks", null, values);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        db.close();
        TasksUtils.checkTimeTasks(context);

        return sawValidEntry;
    }

    private static boolean importUserTriggerTasksJSONArray(Context context, JSONObject jsonObject) {
        JSONArray userTriggerScheduledTasksJSONArray =
                jsonObject.optJSONArray("userTriggerScheduledTasks");
        if (userTriggerScheduledTasksJSONArray == null) {
            return false;
        }

        SQLiteDatabase db = context.openOrCreateDatabase("scheduledTriggerTasks", MODE_PRIVATE, null);
        db.execSQL(
                "create table if not exists tasks(_id integer primary key autoincrement,tg varchar,tgextra varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
        );

        JSONObject oneUserTriggerScheduledTaskJSONObject;
        boolean sawValidEntry = false;
        for (int i = 0; i < userTriggerScheduledTasksJSONArray.length(); ++i) {
            try {
                oneUserTriggerScheduledTaskJSONObject = userTriggerScheduledTasksJSONArray.optJSONObject(i);
                if (oneUserTriggerScheduledTaskJSONObject == null) {
                    continue;
                }
                if (oneUserTriggerScheduledTaskJSONObject.optBoolean("doNotImport", false)) {
                    continue;
                }
                String tg = oneUserTriggerScheduledTaskJSONObject.getString("tg");
                String tgextra = oneUserTriggerScheduledTaskJSONObject.getString("tgextra");
                int enabled = oneUserTriggerScheduledTaskJSONObject.getInt("enabled");
                String label = oneUserTriggerScheduledTaskJSONObject.getString("label");
                String task = oneUserTriggerScheduledTaskJSONObject.getString("task");

                if (!TasksUtils.isValidImportedTriggerTask(tg, task, enabled)) {
                    continue;
                }
                sawValidEntry = true;

                Cursor cursor = null;
                boolean exists = false;
                try {
                    cursor = db.query(
                            "tasks",
                            new String[]{"_id"},
                            "tg = ? AND COALESCE(tgextra, '') = ? AND enabled = ? AND COALESCE(label, '') = ? AND COALESCE(task, '') = ?",
                            new String[]{
                                    tg,
                                    tgextra == null ? "" : tgextra,
                                    String.valueOf(enabled),
                                    label == null ? "" : label,
                                    task == null ? "" : task
                            },
                            null, null, null, "1"
                    );
                    exists = (cursor != null && cursor.moveToFirst());
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }

                if (exists) {
                    continue;
                }

                ContentValues values = new ContentValues();
                values.put("tg", tg);
                values.put("tgextra", tgextra);
                values.put("enabled", enabled);
                values.put("label", label);
                values.put("task", task);
                values.put("column1", "");
                values.put("column2", "");
                db.insert("tasks", null, values);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        db.close();
        TasksUtils.checkTriggerTasks(context);

        return sawValidEntry;
    }

    private static boolean importUserDefinedCategoriesJSONArray(Context context, JSONObject jsonObject) {
        JSONArray userDefinedCategoriesJSONArray =
                jsonObject.optJSONArray("userDefinedCategories");
        if (userDefinedCategoriesJSONArray == null) {
            return false;
        }

        SQLiteDatabase db = context.openOrCreateDatabase("userDefinedCategories", MODE_PRIVATE, null);
        db.execSQL(
                "create table if not exists categories(_id integer primary key autoincrement,label varchar,packages varchar)"
        );

        ArrayList<String> existedLabels = new ArrayList<>();
        Cursor cursor = db.query("categories", new String[]{"label"}, null, null, null, null, null);
        if (cursor.moveToFirst()) {
            for (int i = 0; i < cursor.getCount(); i++) {
                existedLabels.add(cursor.getString(cursor.getColumnIndexOrThrow("label")));
                cursor.moveToNext();
            }
        }
        cursor.close();

        JSONObject oneUserDefinedCategoriesJSONObject;
        boolean sawValidEntry = false;
        for (int i = 0; i < userDefinedCategoriesJSONArray.length(); ++i) {
            try {
                oneUserDefinedCategoriesJSONObject = userDefinedCategoriesJSONArray.optJSONObject(i);
                if (oneUserDefinedCategoriesJSONObject == null) {
                    continue;
                }
                if (oneUserDefinedCategoriesJSONObject.optBoolean("doNotImport", false)) {
                    continue;
                }
                String label = oneUserDefinedCategoriesJSONObject.getString("label");
                try {
                    Base64.decode(label, Base64.DEFAULT);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                // The two values come from a file someone can edit, and here they were pasted
                // between quotes into SQL: a stray apostrophe ended the literal and the restore
                // threw. Bound, they stay the data they are.
                ContentValues values = new ContentValues();
                values.put("packages", oneUserDefinedCategoriesJSONObject.getString("packages"));
                boolean written;
                if (existedLabels.contains(label)) {
                    written = db.update("categories", values, "label = ?", new String[]{label}) > 0;
                } else {
                    values.put("label", label);
                    written = db.insert("categories", null, values) != -1;
                }
                if (written) {
                    sawValidEntry = true;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        db.close();

        return sawValidEntry;
    }

    private static boolean importOneKeyLists(
            Context context, JSONObject jsonObject, DefaultMultiProcessMMKVStorage storage) {
        JSONArray array = jsonObject.optJSONArray("oneKeyList");
        if (array == null) {
            return false;
        }
        JSONObject oneKeyListJSONObject = array.optJSONObject(0);
        if (oneKeyListJSONObject == null) {
            return false;
        }

        return importOneKeyListsFromProcessedJSONObject(
                context, oneKeyListJSONObject, storage);
    }

    private static boolean importOneKeyListsFromProcessedJSONObject(
            Context context, JSONObject oneKeyListJSONObject,
            DefaultMultiProcessMMKVStorage storage) {
        Iterator<String> it = oneKeyListJSONObject.keys();
        boolean importedAny = false;
        String s;
        while (it.hasNext()) {
            s = it.next();
            String listKey;
            String list;
            switch (s) {
                case "okff":
                    listKey = context.getString(R.string.sAutoFreezeApplicationList);
                    list = oneKeyListJSONObject.optString("okff");
                    break;
                case "okuf":
                    listKey = context.getString(R.string.sOneKeyUFApplicationList);
                    list = oneKeyListJSONObject.optString("okuf");
                    break;
                case "foq":
                    listKey = context.getString(R.string.sFreezeOnceQuit);
                    list = oneKeyListJSONObject.optString("foq");
                    break;
                default:
                    continue;
            }
            importedAny = true;
            // Restoring a whole list is a replacement, not an append, so the pair of calls that
            // used to write one Tray row now writes the store, flushes it and tells the other
            // process - a restore that left :backgroundService freezing yesterday's list would
            // be the worst possible surprise.
            storage.putString(listKey, list).sync();
        }
        return importedAny;
    }

    private static boolean importIntSharedPreferences(
            Context context, Activity activity,
            JSONObject jsonObject, SharedPreferences defSP) {
        // Int start
        JSONArray array = jsonObject.optJSONArray("generalSettings_int");
        if (array == null) {
            return false;
        }
        JSONObject generalSettingsIntJSONObject = array.optJSONObject(0);
        if (generalSettingsIntJSONObject == null) {
            return false;
        }

        // Int end
        return importIntSharedPreferencesFromProcessedJSONObject(
                context, activity, generalSettingsIntJSONObject, defSP);
    }

    private static boolean importIntSharedPreferencesFromProcessedJSONObject(
            Context context, Activity activity,
            JSONObject generalSettingsIntJSONObject, SharedPreferences defSP) {
        Iterator<String> it = generalSettingsIntJSONObject.keys();
        boolean importedAny = false;
        String s;
        while (it.hasNext()) {
            s = it.next();
            switch (s) {
                case "onClickFunctionStatus":
                case "sortMethodStatus":
                    importIntSharedPreference(
                            context, activity, generalSettingsIntJSONObject, defSP, s);
                    importedAny = true;
                    break;
                default:
                    break;
            }
        }
        return importedAny;
    }

    private static boolean importStringSharedPreferences(
            Context context, Activity activity,
            JSONObject jsonObject, SharedPreferences defSP) {
        // String start
        JSONArray array = jsonObject.optJSONArray("generalSettings_string");
        if (array == null) {
            return false;
        }
        JSONObject generalSettingsStringJSONObject = array.optJSONObject(0);
        if (generalSettingsStringJSONObject == null) {
            return false;
        }
        // String end
        return importStringSharedPreferencesFromProcessedJSONObject(
                context, activity, generalSettingsStringJSONObject, defSP);
    }

    private static boolean importStringSharedPreferencesFromProcessedJSONObject(
            Context context, Activity activity, JSONObject generalSettingsStringJSONObject,
            SharedPreferences defSP) {
        Iterator<String> it = generalSettingsStringJSONObject.keys();
        boolean importedAny = false;
        String s;
        while (it.hasNext()) {
            s = it.next();

            if (s == null) continue;

            AbstractKey<String> key = null;

            try {
                key = DefaultSharedPreferenceStorageStringKeys.valueOf(s);
            } catch (IllegalArgumentException ignored) {
            }

            if (key == null) {
                try {
                    key = DefaultMultiProcessMMKVStorageStringKeys.valueOf(s);
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (key == null) continue;

            importStringSharedPreference(
                    context, activity, generalSettingsStringJSONObject, defSP, s);
            importedAny = true;
        }
        return importedAny;
    }

    private static boolean importBooleanSharedPreferences(
            Context context, Activity activity,
            JSONObject jsonObject, SharedPreferences defSP) {
        // boolean start
        JSONArray array = jsonObject.optJSONArray("generalSettings_boolean");
        if (array == null) {
            return false;
        }
        JSONObject generalSettingsBooleanJSONObject = array.optJSONObject(0);
        if (generalSettingsBooleanJSONObject == null) {
            return false;
        }

        // boolean end
        return importBooleanSharedPreferencesFromProcessedJSONObject(
                context, activity, generalSettingsBooleanJSONObject, defSP);
    }

    private static boolean importBooleanSharedPreferencesFromProcessedJSONObject(
            Context context, Activity activity,
            JSONObject generalSettingsBooleanJSONObject, SharedPreferences defSP) {
        Iterator<String> it = generalSettingsBooleanJSONObject.keys();
        boolean importedAny = false;
        String s;
        while (it.hasNext()) {
            s = it.next();

            if (s == null) continue;

            if (isNonRestorableBooleanKey(s)) {
                continue;
            }

            AbstractKey<Boolean> key = null;

            try {
                key = DefaultSharedPreferenceStorageBooleanKeys.valueOf(s);
            } catch (IllegalArgumentException ignored) {
            }

            if (key == null) {
                try {
                    key = DefaultMultiProcessMMKVStorageBooleanKeys.valueOf(s);
                } catch (IllegalArgumentException ignored) {
                }
            }

            if (key == null) continue;

            importBooleanSharedPreference(
                    context, activity, generalSettingsBooleanJSONObject, defSP, s);
            importedAny = true;
        }
        return importedAny;
    }

    private static boolean importAutoAllowList(Context context, JSONObject jsonObject, String listKey) {
        JSONArray array = jsonObject.optJSONArray(listKey);
        if (array == null) {
            return false;
        }
        JSONObject jObj = array.optJSONObject(0);
        if (jObj == null) {
            return false;
        }
        // A whitelist in a backup is the whole list, not an edit to the one on the device, and
        // write() is what tells :backgroundService that it changed.
        AutoAllowListUtils.write(context, listKey, jObj.optString("lists"));
        return true;
    }

    private static boolean isNonRestorableBooleanKey(String key) {
        for (String skippedKey : NON_RESTORABLE_BOOLEAN_KEYS) {
            if (skippedKey.equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isImportableBooleanKey(String key) {
        if (isNonRestorableBooleanKey(key)) {
            return false;
        }
        try {
            DefaultSharedPreferenceStorageBooleanKeys.valueOf(key);
            return true;
        } catch (IllegalArgumentException ignored) {
        }
        try {
            DefaultMultiProcessMMKVStorageBooleanKeys.valueOf(key);
            return true;
        } catch (IllegalArgumentException ignored) {
        }
        return false;
    }

    private static boolean isImportableStringKey(String key) {
        try {
            DefaultSharedPreferenceStorageStringKeys.valueOf(key);
            return true;
        } catch (IllegalArgumentException ignored) {
        }
        try {
            DefaultMultiProcessMMKVStorageStringKeys.valueOf(key);
            return true;
        } catch (IllegalArgumentException ignored) {
        }
        return false;
    }

    /**
     * @return how many entries the importers would actually apply from the category, or -1 when
     * the category is missing from the backup. The skip rules mirror importContents(), so a backup
     * whose every entry would be skipped does not offer a restore that writes nothing.
     */
    public static int countImportableEntries(JSONObject jsonObject, String category) {
        JSONArray array = jsonObject.optJSONArray(category);
        if (array == null) {
            return -1;
        }
        JSONObject firstObject = array.optJSONObject(0);
        switch (category) {
            case "generalSettings_boolean": {
                if (firstObject == null) return 0;
                int count = 0;
                Iterator<String> it = firstObject.keys();
                while (it.hasNext()) {
                    if (isImportableBooleanKey(it.next())) count++;
                }
                return count;
            }
            case "generalSettings_string": {
                if (firstObject == null) return 0;
                int count = 0;
                Iterator<String> it = firstObject.keys();
                while (it.hasNext()) {
                    if (isImportableStringKey(it.next())) count++;
                }
                return count;
            }
            case "generalSettings_int": {
                if (firstObject == null) return 0;
                int count = 0;
                if (firstObject.has("onClickFunctionStatus")) count++;
                if (firstObject.has("sortMethodStatus")) count++;
                return count;
            }
            case "oneKeyList": {
                if (firstObject == null) return 0;
                int count = 0;
                if (firstObject.has("okff")) count++;
                if (firstObject.has("okuf")) count++;
                if (firstObject.has("foq")) count++;
                return count;
            }
            case "userTimeScheduledTasks":
                return countImportableTimeTasks(array);
            case "userTriggerScheduledTasks":
                return countImportableTriggerTasks(array);
            case "userDefinedCategories":
                return countImportableCategories(array);
            case "uriAutoAllowPkgs_allows":
            case "installPkgs_autoAllowPkgs_allows":
                return firstObject == null ? 0 : 1;
            default:
                return 0;
        }
    }

    private static int countImportableTimeTasks(JSONArray array) {
        int count = 0;
        for (int i = 0; i < array.length(); ++i) {
            JSONObject entry = array.optJSONObject(i);
            if (entry == null || entry.optBoolean("doNotImport", false)) {
                continue;
            }
            try {
                int hour = entry.getInt("hour");
                int minutes = entry.getInt("minutes");
                String repeat = entry.getString("repeat");
                int enabled = entry.getInt("enabled");
                entry.getString("label"); // The importer rejects entries without a label.
                String task = entry.getString("task");
                if (TasksUtils.isValidImportedTimeTask(hour, minutes, repeat, task, enabled)) {
                    count++;
                }
            } catch (JSONException ignored) {
            }
        }
        return count;
    }

    private static int countImportableTriggerTasks(JSONArray array) {
        int count = 0;
        for (int i = 0; i < array.length(); ++i) {
            JSONObject entry = array.optJSONObject(i);
            if (entry == null || entry.optBoolean("doNotImport", false)) {
                continue;
            }
            try {
                String tg = entry.getString("tg");
                entry.getString("tgextra"); // The importer rejects entries without tgextra.
                int enabled = entry.getInt("enabled");
                entry.getString("label"); // The importer rejects entries without a label.
                String task = entry.getString("task");
                if (TasksUtils.isValidImportedTriggerTask(tg, task, enabled)) {
                    count++;
                }
            } catch (JSONException ignored) {
            }
        }
        return count;
    }

    private static int countImportableCategories(JSONArray array) {
        int count = 0;
        for (int i = 0; i < array.length(); ++i) {
            JSONObject entry = array.optJSONObject(i);
            if (entry == null || entry.optBoolean("doNotImport", false)) {
                continue;
            }
            try {
                String label = entry.getString("label");
                Base64.decode(label, Base64.DEFAULT);
                entry.getString("packages");
                count++;
            } catch (JSONException | IllegalArgumentException ignored) {
            }
        }
        return count;
    }

    /**
     * @return true when at least one category contained entries that were applied; false when the
     * selection was empty or every category was unusable, so the caller can report a no-op restore.
     */
    public static boolean importContents(Context context, Activity activity, JSONObject jsonObject) {
        final SharedPreferences defSP = PreferenceManager.getDefaultSharedPreferences(context);
        final DefaultMultiProcessMMKVStorage listStorage = new DefaultMultiProcessMMKVStorage();

        boolean importedAny = false;
        Iterator<String> jsonKeysIterator = jsonObject.keys();
        while (jsonKeysIterator.hasNext()) {
            try {
                switch (jsonKeysIterator.next()) {
                    // General settings import (from More Settings, excluding icon selection) start
                    case "generalSettings_boolean":
                        importedAny |= importBooleanSharedPreferences(context, activity, jsonObject, defSP);
                        break;
                    case "generalSettings_string":
                        importedAny |= importStringSharedPreferences(context, activity, jsonObject, defSP);
                        break;
                    case "generalSettings_int":
                        importedAny |= importIntSharedPreferences(context, activity, jsonObject, defSP);
                        break;
                    // General settings import end
                    // One-key freeze/unfreeze/leave lists start
                    case "oneKeyList":
                        importedAny |= importOneKeyLists(context, jsonObject, listStorage);
                        break;
                    // One-key freeze/unfreeze/leave lists end
                    // Scheduled tasks - Time start
                    case "userTimeScheduledTasks":
                        importedAny |= importUserTimeTasksJSONArray(activity, jsonObject);
                        break;
                    // Scheduled tasks - Time end
                    // Scheduled tasks - Triggers start
                    case "userTriggerScheduledTasks":
                        importedAny |= importUserTriggerTasksJSONArray(activity, jsonObject);
                        break;
                    // Scheduled tasks - Triggers end
                    // User defined categories start
                    case "userDefinedCategories":
                        importedAny |= importUserDefinedCategoriesJSONArray(activity, jsonObject);
                        break;
                    // User defined categories end
                    // URI request whitelist start
                    case "uriAutoAllowPkgs_allows":
                        importedAny |= importAutoAllowList(context, jsonObject, AutoAllowListUtils.URI_ALLOW);
                        break;
                    // URI request whitelist end
                    // Install request whitelist start
                    case "installPkgs_autoAllowPkgs_allows":
                        importedAny |= importAutoAllowList(context, jsonObject, AutoAllowListUtils.INSTALL_ALLOW);
                        break;
                    // Install request whitelist end
                    default:
                        break;
                }
            } catch (Exception e) {
                // A backup is untrusted input: one broken category must not abort the others or
                // kill the restore thread before it reports back.
                e.printStackTrace();
            }
        }

        return importedAny;
    }

    public static String getExportContent(@NonNull Context context) {
        final DefaultMultiProcessMMKVStorage listStorage = new DefaultMultiProcessMMKVStorage();
        final SharedPreferences defSP = PreferenceManager.getDefaultSharedPreferences(context);

        JSONObject finalOutputJsonObject = new JSONObject();

        try {
            // Format version tag start
            JSONArray formatVersionJSONArray = new JSONArray();
            JSONObject formatVersionJSONObject = new JSONObject();
            formatVersionJSONObject.put("version", 1); // Backup format version
            formatVersionJSONObject.put("generateTime", new Date().getTime()); // Backup generation timestamp
            formatVersionJSONArray.put(formatVersionJSONObject);
            finalOutputJsonObject.put("format_version", formatVersionJSONArray);
            // Format version tag end

            // General settings export (from More Settings, excluding icon selection) start

            // boolean start
            JSONArray generalSettingsBooleanJSONArray = new JSONArray();
            JSONObject generalSettingsBooleanJSONObject = new JSONObject();

            for (DefaultSharedPreferenceStorageBooleanKeys key
                    : DefaultSharedPreferenceStorageBooleanKeys.values()) {
                generalSettingsBooleanJSONObject.put(key.name(), key.getValue(context));
            }
            for (DefaultMultiProcessMMKVStorageBooleanKeys key
                    : DefaultMultiProcessMMKVStorageBooleanKeys.values()) {
                generalSettingsBooleanJSONObject.put(key.name(), key.getValue(context));
            }

            generalSettingsBooleanJSONArray.put(generalSettingsBooleanJSONObject);
            finalOutputJsonObject.put("generalSettings_boolean", generalSettingsBooleanJSONArray);
            // boolean end

            // String start
            JSONArray generalSettingsStringJSONArray = new JSONArray();
            JSONObject generalSettingsStringJSONObject = new JSONObject();

            for (DefaultSharedPreferenceStorageStringKeys key
                    : DefaultSharedPreferenceStorageStringKeys.values()) {
                generalSettingsStringJSONObject.put(key.name(), key.getValue(context));
            }
            for (DefaultMultiProcessMMKVStorageStringKeys key
                    : DefaultMultiProcessMMKVStorageStringKeys.values()) {
                generalSettingsStringJSONObject.put(key.name(), key.getValue(context));
            }

            generalSettingsStringJSONArray.put(generalSettingsStringJSONObject);
            finalOutputJsonObject.put("generalSettings_string", generalSettingsStringJSONArray);
            // String end

            // Int start
            JSONArray generalSettingsIntJSONArray = new JSONArray();
            JSONObject generalSettingsIntJSONObject = new JSONObject();
            generalSettingsIntJSONObject.put(
                    "onClickFunctionStatus",
                    defSP.getInt("onClickFunctionStatus", 0)
            );
            generalSettingsIntJSONObject.put(
                    "sortMethodStatus",
                    defSP.getInt("sortMethodStatus", 0)
            );
            generalSettingsIntJSONArray.put(generalSettingsIntJSONObject);
            finalOutputJsonObject.put("generalSettings_int", generalSettingsIntJSONArray);
            // Int end

            // General settings export end

            // One-key freeze/unfreeze/leave lists start
            JSONArray oneKeyListJSONArray = new JSONArray();
            JSONObject oneKeyListJSONObject = new JSONObject();
            oneKeyListJSONObject.put(
                    "okff",
                    listStorage.getString(context.getString(R.string.sAutoFreezeApplicationList), "")
            );
            oneKeyListJSONObject.put(
                    "okuf",
                    listStorage.getString(context.getString(R.string.sOneKeyUFApplicationList), "")
            );
            oneKeyListJSONObject.put(
                    "foq",
                    listStorage.getString(context.getString(R.string.sFreezeOnceQuit), "")
            );
            oneKeyListJSONArray.put(oneKeyListJSONObject);
            finalOutputJsonObject.put("oneKeyList", oneKeyListJSONArray);
            // One-key freeze/unfreeze/leave lists end

            // Install package request and URI request whitelist start
            finalOutputJsonObject.put("uriAutoAllowPkgs_allows",
                    generateAutoAllowListJSONArray(AutoAllowListUtils.URI_ALLOW));
            finalOutputJsonObject.put("installPkgs_autoAllowPkgs_allows",
                    generateAutoAllowListJSONArray(AutoAllowListUtils.INSTALL_ALLOW));
            // Install package request and URI request whitelist end

            // Scheduled tasks - Time start
            finalOutputJsonObject.put("userTimeScheduledTasks", generateUserTimeTasksJSONArray(context));
            // Scheduled tasks - Time end

            // Scheduled tasks - Triggers start
            finalOutputJsonObject.put("userTriggerScheduledTasks", generateUserTriggerTasksJSONArray(context));
            // Scheduled tasks - Triggers end

            // User defined categories start
            finalOutputJsonObject.put("userDefinedCategories", generateUserDefinedCategoriesJSONArray(context));
            // User defined categories end

        } catch (JSONException e) {
            e.printStackTrace();
        }

        return finalOutputJsonObject.toString();
    }

    private static JSONArray generateUserTimeTasksJSONArray(Context context) throws JSONException {
        JSONArray userTimeScheduledTasksJSONArray = new JSONArray();
        SQLiteDatabase db = context.openOrCreateDatabase("scheduledTasks", MODE_PRIVATE, null);
        db.execSQL(
                "create table if not exists tasks(_id integer primary key autoincrement,hour integer(2),minutes integer(2),repeat varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
        );
        Cursor cursor =
                db.query("tasks", null, null, null,
                        null, null, null);
        if (cursor.moveToFirst()) {
            boolean ifContinue = true;
            while (ifContinue) {
                JSONObject oneUserTimeScheduledTaskJSONObject = new JSONObject();
                oneUserTimeScheduledTaskJSONObject.put("hour",
                        cursor.getInt(cursor.getColumnIndexOrThrow("hour")));
                oneUserTimeScheduledTaskJSONObject.put("minutes",
                        cursor.getInt(cursor.getColumnIndexOrThrow("minutes")));
                oneUserTimeScheduledTaskJSONObject.put("enabled",
                        cursor.getInt(cursor.getColumnIndexOrThrow("enabled")));
                oneUserTimeScheduledTaskJSONObject.put("label",
                        cursor.getString(cursor.getColumnIndexOrThrow("label")));
                oneUserTimeScheduledTaskJSONObject.put("task",
                        cursor.getString(cursor.getColumnIndexOrThrow("task")));
                oneUserTimeScheduledTaskJSONObject.put("repeat",
                        cursor.getString(cursor.getColumnIndexOrThrow("repeat")));
                userTimeScheduledTasksJSONArray.put(oneUserTimeScheduledTaskJSONObject);
                ifContinue = cursor.moveToNext();
            }
        }
        cursor.close();
        db.close();
        return userTimeScheduledTasksJSONArray;
    }

    private static JSONArray generateUserTriggerTasksJSONArray(Context context) throws JSONException {
        JSONArray userTriggerScheduledTasksJSONArray = new JSONArray();
        SQLiteDatabase db = context.openOrCreateDatabase("scheduledTriggerTasks", MODE_PRIVATE, null);
        db.execSQL(
                "create table if not exists tasks(_id integer primary key autoincrement,tg varchar,tgextra varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
        );
        Cursor cursor =
                db.query("tasks", null, null, null,
                        null, null, null);
        if (cursor.moveToFirst()) {
            boolean ifContinue = true;
            while (ifContinue) {
                JSONObject oneUserTriggerScheduledTaskJSONObject = new JSONObject();
                oneUserTriggerScheduledTaskJSONObject.put("tgextra",
                        cursor.getString(cursor.getColumnIndexOrThrow("tgextra")));
                oneUserTriggerScheduledTaskJSONObject.put("enabled",
                        cursor.getInt(cursor.getColumnIndexOrThrow("enabled")));
                oneUserTriggerScheduledTaskJSONObject.put("label",
                        cursor.getString(cursor.getColumnIndexOrThrow("label")));
                oneUserTriggerScheduledTaskJSONObject.put("task",
                        cursor.getString(cursor.getColumnIndexOrThrow("task")));
                oneUserTriggerScheduledTaskJSONObject.put("tg",
                        cursor.getString(cursor.getColumnIndexOrThrow("tg")));
                userTriggerScheduledTasksJSONArray.put(oneUserTriggerScheduledTaskJSONObject);
                ifContinue = cursor.moveToNext();
            }
        }
        cursor.close();
        db.close();
        return userTriggerScheduledTasksJSONArray;
    }

    private static JSONArray generateUserDefinedCategoriesJSONArray(Context context) throws JSONException {
        JSONArray userDefinedCategoriesJSONArray = new JSONArray();
        SQLiteDatabase db = context.openOrCreateDatabase("userDefinedCategories", MODE_PRIVATE, null);
        db.execSQL(
                "create table if not exists categories(_id integer primary key autoincrement,label varchar,packages varchar)"
        );
        Cursor cursor =
                db.query(
                        "categories", new String[]{"label", "packages"},
                        null, null, null,
                        null, null
                );
        if (cursor.moveToFirst()) {
            boolean ifContinue = true;
            while (ifContinue) {
                JSONObject oneUserDefinedCategoriesJSONObject = new JSONObject();
                oneUserDefinedCategoriesJSONObject.put("label",
                        cursor.getString(cursor.getColumnIndexOrThrow("label")));
                oneUserDefinedCategoriesJSONObject.put("packages",
                        cursor.getString(cursor.getColumnIndexOrThrow("packages")));
                userDefinedCategoriesJSONArray.put(oneUserDefinedCategoriesJSONObject);
                ifContinue = cursor.moveToNext();
            }
        }
        cursor.close();
        db.close();
        return userDefinedCategoriesJSONArray;
    }

    private static JSONArray generateAutoAllowListJSONArray(String listKey) throws JSONException {
        JSONArray whitelistJSONArray = new JSONArray();
        JSONObject whitelistJSONObject = new JSONObject();
        whitelistJSONObject.put("lists", AutoAllowListUtils.readRaw(listKey));
        whitelistJSONArray.put(whitelistJSONObject);
        return whitelistJSONArray;
    }
}
