package cf.playhi.freezeyou.utils;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;

import net.grandcentrix.tray.AppPreferences;

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

import static android.content.Context.MODE_PRIVATE;

// Note: Backup/restore schema and process is complex. Version flag in backup format can be used to distinguish schemas.
public final class BackupUtils {

    public static String convertSharedPreference(
            AppPreferences appPreferences, String key, String defValue) {
        return appPreferences.getString(key, defValue);
    }

    private static void importStringSharedPreference(
            Context context, Activity activity, JSONObject jsonObject,
            SharedPreferences sp, String key) {
        sp.edit().putString(key, jsonObject.optString(key, "")).apply();
        SettingsUtils.checkPreferenceData(context, activity, sp, key);
    }

    private static void importBooleanSharedPreference(
            Context context, Activity activity, JSONObject jsonObject,
            SharedPreferences sp, String key) {
        sp.edit().putBoolean(key, jsonObject.optBoolean(key, false)).apply();
        SettingsUtils.checkPreferenceData(context, activity, sp, key);
    }

    private static void importIntSharedPreference(
            Context context, Activity activity, JSONObject jsonObject,
            SharedPreferences sp, String key) {
        sp.edit().putInt(key, jsonObject.optInt(key, 0)).apply();
        SettingsUtils.checkPreferenceData(context, activity, sp, key);
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

        boolean isCompletelySuccess = true;
        JSONObject oneUserTimeScheduledTaskJSONObject;
        for (int i = 0; i < userTimeScheduledTasksJSONArray.length(); ++i) {
            try {
                oneUserTimeScheduledTaskJSONObject = userTimeScheduledTasksJSONArray.optJSONObject(i);
                if (oneUserTimeScheduledTaskJSONObject == null) {
                    isCompletelySuccess = false;
                    continue;
                }
                if (oneUserTimeScheduledTaskJSONObject.optBoolean("doNotImport", false)) {
                    continue;
                }
                // Read back out of a file someone can edit, and here these were pasted between
                // quotes into SQL: a stray apostrophe ended the literal and the restore threw.
                ContentValues values = new ContentValues();
                values.put("hour", oneUserTimeScheduledTaskJSONObject.getInt("hour"));
                values.put("minutes", oneUserTimeScheduledTaskJSONObject.getInt("minutes"));
                values.put("repeat", oneUserTimeScheduledTaskJSONObject.getString("repeat"));
                values.put("enabled", oneUserTimeScheduledTaskJSONObject.getInt("enabled"));
                values.put("label", oneUserTimeScheduledTaskJSONObject.getString("label"));
                values.put("task", oneUserTimeScheduledTaskJSONObject.getString("task"));
                values.put("column1", "");
                values.put("column2", "");
                db.insert("tasks", null, values);
            } catch (JSONException e) {
                isCompletelySuccess = false;
            }
        }

        db.close();
        TasksUtils.checkTimeTasks(context);

        return isCompletelySuccess;
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
        boolean isCompletelySuccess = true;
        for (int i = 0; i < userTriggerScheduledTasksJSONArray.length(); ++i) {
            try {
                oneUserTriggerScheduledTaskJSONObject = userTriggerScheduledTasksJSONArray.optJSONObject(i);
                if (oneUserTriggerScheduledTaskJSONObject == null) {
                    isCompletelySuccess = false;
                    continue;
                }
                if (oneUserTriggerScheduledTaskJSONObject.optBoolean("doNotImport", false)) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("tg", oneUserTriggerScheduledTaskJSONObject.getString("tg"));
                values.put("tgextra", oneUserTriggerScheduledTaskJSONObject.getString("tgextra"));
                values.put("enabled", oneUserTriggerScheduledTaskJSONObject.getInt("enabled"));
                values.put("label", oneUserTriggerScheduledTaskJSONObject.getString("label"));
                values.put("task", oneUserTriggerScheduledTaskJSONObject.getString("task"));
                values.put("column1", "");
                values.put("column2", "");
                db.insert("tasks", null, values);
            } catch (JSONException e) {
                isCompletelySuccess = false;
            }
        }

        db.close();
        TasksUtils.checkTriggerTasks(context);

        return isCompletelySuccess;
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
        boolean isCompletelySuccess = true;
        for (int i = 0; i < userDefinedCategoriesJSONArray.length(); ++i) {
            try {
                oneUserDefinedCategoriesJSONObject = userDefinedCategoriesJSONArray.optJSONObject(i);
                if (oneUserDefinedCategoriesJSONObject == null) {
                    isCompletelySuccess = false;
                    continue;
                }
                if (oneUserDefinedCategoriesJSONObject.optBoolean("doNotImport", false)) {
                    continue;
                }
                String label = oneUserDefinedCategoriesJSONObject.getString("label");
                // The two values come from a file someone can edit, and here they were pasted
                // between quotes into SQL: a stray apostrophe ended the literal and the restore
                // threw. Bound, they stay the data they are.
                ContentValues values = new ContentValues();
                values.put("packages", oneUserDefinedCategoriesJSONObject.getString("packages"));
                if (existedLabels.contains(label)) {
                    db.update("categories", values, "label = ?", new String[]{label});
                } else {
                    values.put("label", label);
                    db.insert("categories", null, values);
                }
            } catch (JSONException e) {
                isCompletelySuccess = false;
            }
        }

        db.close();

        return isCompletelySuccess;
    }

    private static boolean importOneKeyLists(Context context, JSONObject jsonObject, AppPreferences appPreferences) {
        JSONArray array = jsonObject.optJSONArray("oneKeyList");
        if (array == null) {
            return false;
        }
        JSONObject oneKeyListJSONObject = array.optJSONObject(0);
        if (oneKeyListJSONObject == null) {
            return false;
        }

        importOneKeyListsFromProcessedJSONObject(
                context, oneKeyListJSONObject, appPreferences);

        return true;
    }

    private static void importOneKeyListsFromProcessedJSONObject(
            Context context, JSONObject oneKeyListJSONObject, AppPreferences appPreferences) {
        Iterator<String> it = oneKeyListJSONObject.keys();
        String s;
        while (it.hasNext()) {
            s = it.next();
            switch (s) {
                case "okff":
                    appPreferences.put(
                            context.getString(R.string.sAutoFreezeApplicationList),
                            oneKeyListJSONObject.optString("okff"));
                    break;
                case "okuf":
                    appPreferences.put(
                            context.getString(R.string.sOneKeyUFApplicationList),
                            oneKeyListJSONObject.optString("okuf"));
                    break;
                case "foq":
                    appPreferences.put(
                            context.getString(R.string.sFreezeOnceQuit),
                            oneKeyListJSONObject.optString("foq"));
                    break;
                default:
                    break;
            }
        }
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

        importIntSharedPreferencesFromProcessedJSONObject(
                context, activity, generalSettingsIntJSONObject, defSP);
        // Int end
        return true;
    }

    private static void importIntSharedPreferencesFromProcessedJSONObject(
            Context context, Activity activity,
            JSONObject generalSettingsIntJSONObject, SharedPreferences defSP) {
        Iterator<String> it = generalSettingsIntJSONObject.keys();
        String s;
        while (it.hasNext()) {
            s = it.next();
            switch (s) {
                case "onClickFunctionStatus":
                case "sortMethodStatus":
                    importIntSharedPreference(
                            context, activity, generalSettingsIntJSONObject, defSP, s);
                    break;
                default:
                    break;
            }
        }
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
        importStringSharedPreferencesFromProcessedJSONObject(
                context, activity, generalSettingsStringJSONObject, defSP);
        // String end
        return true;
    }

    private static void importStringSharedPreferencesFromProcessedJSONObject(
            Context context, Activity activity, JSONObject generalSettingsStringJSONObject,
            SharedPreferences defSP) {
        Iterator<String> it = generalSettingsStringJSONObject.keys();
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
        }
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

        importBooleanSharedPreferencesFromProcessedJSONObject(
                context, activity, generalSettingsBooleanJSONObject, defSP);
        // boolean end
        return true;
    }

    private static void importBooleanSharedPreferencesFromProcessedJSONObject(
            Context context, Activity activity,
            JSONObject generalSettingsBooleanJSONObject, SharedPreferences defSP) {
        Iterator<String> it = generalSettingsBooleanJSONObject.keys();
        String s;
        while (it.hasNext()) {
            s = it.next();

            if (s == null) continue;

            if (DefaultSharedPreferenceStorageBooleanKeys.firstIconEnabled.name().equals(s)
                    || DefaultSharedPreferenceStorageBooleanKeys.secondIconEnabled.name().equals(s)
                    || DefaultSharedPreferenceStorageBooleanKeys.thirdIconEnabled.name().equals(s)
                    || DefaultMultiProcessMMKVStorageBooleanKeys.enableAuthentication.name().equals(s)) {
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
        }
    }

    private static boolean importUriAutoAllowPkgsJSONArray(JSONObject jsonObject, AppPreferences ap) {
        JSONArray array = jsonObject.optJSONArray("uriAutoAllowPkgs_allows");
        if (array == null) {
            return false;
        }
        JSONObject jObj = array.optJSONObject(0);
        if (jObj == null) {
            return false;
        }
        return ap.put("uriAutoAllowPkgs_allows", jObj.optString("lists"));
    }

    private static boolean importInstallPkgsAutoAllowPkgsJSONArray(JSONObject jsonObject, AppPreferences ap) {
        JSONArray array = jsonObject.optJSONArray("installPkgs_autoAllowPkgs_allows");
        if (array == null) {
            return false;
        }
        JSONObject jObj = array.optJSONObject(0);
        if (jObj == null) {
            return false;
        }
        return ap.put("installPkgs_autoAllowPkgs_allows", jObj.optString("lists"));
    }

    public static void importContents(Context context, Activity activity, JSONObject jsonObject) {
        final SharedPreferences defSP = PreferenceManager.getDefaultSharedPreferences(context);
        final AppPreferences appPreferences = new AppPreferences(context);

        Iterator<String> jsonKeysIterator = jsonObject.keys();
        while (jsonKeysIterator.hasNext()) {
            switch (jsonKeysIterator.next()) {
                // General settings import (from More Settings, excluding icon selection) start
                case "generalSettings_boolean":
                    importBooleanSharedPreferences(context, activity, jsonObject, defSP);
                    break;
                case "generalSettings_string":
                    importStringSharedPreferences(context, activity, jsonObject, defSP);
                    break;
                case "generalSettings_int":
                    importIntSharedPreferences(context, activity, jsonObject, defSP);
                    break;
                // General settings import end
                // One-key freeze/unfreeze/leave lists start
                case "oneKeyList":
                    importOneKeyLists(context, jsonObject, appPreferences);
                    break;
                // One-key freeze/unfreeze/leave lists end
                // Scheduled tasks - Time start
                case "userTimeScheduledTasks":
                    importUserTimeTasksJSONArray(activity, jsonObject);
                    break;
                // Scheduled tasks - Time end
                // Scheduled tasks - Triggers start
                case "userTriggerScheduledTasks":
                    importUserTriggerTasksJSONArray(activity, jsonObject);
                    break;
                // Scheduled tasks - Triggers end
                // User defined categories start
                case "userDefinedCategories":
                    importUserDefinedCategoriesJSONArray(activity, jsonObject);
                    break;
                // User defined categories end
                // URI request whitelist start
                case "uriAutoAllowPkgs_allows":
                    importUriAutoAllowPkgsJSONArray(jsonObject, appPreferences);
                    break;
                // URI request whitelist end
                // Install request whitelist start
                case "installPkgs_autoAllowPkgs_allows":
                    importInstallPkgsAutoAllowPkgsJSONArray(jsonObject, appPreferences);
                    break;
                // Install request whitelist end
                default:
                    break;
            }
        }

    }

    public static String getExportContent(@NonNull Context context) {
        final AppPreferences appPreferences = new AppPreferences(context);
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
                    appPreferences.getString(context.getString(R.string.sAutoFreezeApplicationList), "")
            );
            oneKeyListJSONObject.put(
                    "okuf",
                    appPreferences.getString(context.getString(R.string.sOneKeyUFApplicationList), "")
            );
            oneKeyListJSONObject.put(
                    "foq",
                    appPreferences.getString(context.getString(R.string.sFreezeOnceQuit), "")
            );
            oneKeyListJSONArray.put(oneKeyListJSONObject);
            finalOutputJsonObject.put("oneKeyList", oneKeyListJSONArray);
            // One-key freeze/unfreeze/leave lists end

            // Install package request and URI request whitelist start
            finalOutputJsonObject.put("uriAutoAllowPkgs_allows", generateUriAutoAllowPkgsJSONArray(context));
            finalOutputJsonObject.put("installPkgs_autoAllowPkgs_allows", generateInstallPkgsAutoAllowPkgsJSONArray(context));
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

    private static JSONArray generateUriAutoAllowPkgsJSONArray(Context context) throws JSONException {
        JSONArray userDefinedCategoriesJSONArray = new JSONArray();
        JSONObject oneUserDefinedCategoriesJSONObject = new JSONObject();
        AppPreferences ap = new AppPreferences(context);
        oneUserDefinedCategoriesJSONObject.put(
                "lists",
                convertSharedPreference(ap, "uriAutoAllowPkgs_allows", "")
        );
        userDefinedCategoriesJSONArray.put(oneUserDefinedCategoriesJSONObject);
        return userDefinedCategoriesJSONArray;
    }

    private static JSONArray generateInstallPkgsAutoAllowPkgsJSONArray(Context context) throws JSONException {
        JSONArray userDefinedCategoriesJSONArray = new JSONArray();
        JSONObject oneUserDefinedCategoriesJSONObject = new JSONObject();
        AppPreferences ap = new AppPreferences(context);
        oneUserDefinedCategoriesJSONObject.put(
                "lists",
                convertSharedPreference(ap, "installPkgs_autoAllowPkgs_allows", "")
        );
        userDefinedCategoriesJSONArray.put(oneUserDefinedCategoriesJSONObject);
        return userDefinedCategoriesJSONArray;
    }
}
