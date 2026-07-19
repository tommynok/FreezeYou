package cf.playhi.freezeyou.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import androidx.preference.PreferenceManager
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageStringKeys
import net.grandcentrix.tray.AppPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

//导入导出整体结构复杂度高，处理流程复杂度高，待有可用的优化方案时需要进行优化（提示：后续导出时可修改导出时的 version，用以导入时区分方案）
object BackupUtils {
    @JvmStatic
    fun convertSharedPreference(
        appPreferences: AppPreferences, key: String, defValue: String?
    ): String? {
        return appPreferences.getString(key, defValue)
    }

    private fun importStringSharedPreference(
        context: Context, activity: Activity?, jsonObject: JSONObject,
        sp: SharedPreferences, key: String
    ) {
        sp.edit().putString(key, jsonObject.optString(key, "")).apply()
        if (activity != null) {
            SettingsUtils.checkPreferenceData(context, activity, sp, key)
        }
    }

    private fun importBooleanSharedPreference(
        context: Context, activity: Activity?, jsonObject: JSONObject,
        sp: SharedPreferences, key: String
    ) {
        sp.edit().putBoolean(key, jsonObject.optBoolean(key, false)).apply()
        if (activity != null) {
            SettingsUtils.checkPreferenceData(context, activity, sp, key)
        }
    }

    private fun importIntSharedPreference(
        context: Context, activity: Activity?, jsonObject: JSONObject,
        sp: SharedPreferences, key: String
    ) {
        sp.edit().putInt(key, jsonObject.optInt(key, 0)).apply()
        if (activity != null) {
            SettingsUtils.checkPreferenceData(context, activity, sp, key)
        }
    }

    private fun importUserTimeTasksJSONArray(context: Context, jsonObject: JSONObject): Boolean {
        val userTimeScheduledTasksJSONArray = jsonObject.optJSONArray("userTimeScheduledTasks")
            ?: return false
        val db = context.openOrCreateDatabase("scheduledTasks", Context.MODE_PRIVATE, null)
        db.execSQL(
            "create table if not exists tasks(_id integer primary key autoincrement,hour integer(2),minutes integer(2),repeat varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
        )
        var isCompletelySuccess = true
        var oneUserTimeScheduledTaskJSONObject: JSONObject?
        for (i in 0 until userTimeScheduledTasksJSONArray.length()) {
            try {
                oneUserTimeScheduledTaskJSONObject = userTimeScheduledTasksJSONArray.optJSONObject(i)
                if (oneUserTimeScheduledTaskJSONObject == null) {
                    isCompletelySuccess = false
                    continue
                }
                if (oneUserTimeScheduledTaskJSONObject.optBoolean("doNotImport", false)) {
                    continue
                }
                db.execSQL(
                    "insert into tasks(_id,hour,minutes,repeat,enabled,label,task,column1,column2) values(null,"
                            + oneUserTimeScheduledTaskJSONObject.getInt("hour") + ","
                            + oneUserTimeScheduledTaskJSONObject.getInt("minutes") + ","
                            + "'" + oneUserTimeScheduledTaskJSONObject.getString("repeat") + "'" + ","
                            + oneUserTimeScheduledTaskJSONObject.getInt("enabled") + ","
                            + "'" + oneUserTimeScheduledTaskJSONObject.getString("label") + "'" + ","
                            + "'" + oneUserTimeScheduledTaskJSONObject.getString("task") + "'" + ",'','')"
                )
            } catch (e: JSONException) {
                isCompletelySuccess = false
            }
        }
        db.close()
        TasksUtils.checkTimeTasks(context)
        return isCompletelySuccess
    }

    private fun importUserTriggerTasksJSONArray(context: Context, jsonObject: JSONObject): Boolean {
        val userTriggerScheduledTasksJSONArray = jsonObject.optJSONArray("userTriggerScheduledTasks")
            ?: return false
        val db = context.openOrCreateDatabase("scheduledTriggerTasks", Context.MODE_PRIVATE, null)
        db.execSQL(
            "create table if not exists tasks(_id integer primary key autoincrement,tg varchar,tgextra varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
        )
        var oneUserTriggerScheduledTaskJSONObject: JSONObject?
        var isCompletelySuccess = true
        for (i in 0 until userTriggerScheduledTasksJSONArray.length()) {
            try {
                oneUserTriggerScheduledTaskJSONObject =
                    userTriggerScheduledTasksJSONArray.optJSONObject(i)
                if (oneUserTriggerScheduledTaskJSONObject == null) {
                    isCompletelySuccess = false
                    continue
                }
                if (oneUserTriggerScheduledTaskJSONObject.optBoolean("doNotImport", false)) {
                    continue
                }
                db.execSQL(
                    "insert into tasks(_id,tg,tgextra,enabled,label,task,column1,column2) VALUES (null,"
                            + "'" + oneUserTriggerScheduledTaskJSONObject.getString("tg") + "'" + ","
                            + "'" + oneUserTriggerScheduledTaskJSONObject.getString("tgextra") + "'" + ","
                            + oneUserTriggerScheduledTaskJSONObject.getInt("enabled") + ","
                            + "'" + oneUserTriggerScheduledTaskJSONObject.getString("label") + "'" + ","
                            + "'" + oneUserTriggerScheduledTaskJSONObject.getString("task") + "'" + ",'','')"
                )
            } catch (e: JSONException) {
                isCompletelySuccess = false
            }
        }
        db.close()
        TasksUtils.checkTriggerTasks(context)
        return isCompletelySuccess
    }

    private fun importUserDefinedCategoriesJSONArray(
        context: Context,
        jsonObject: JSONObject
    ): Boolean {
        val userDefinedCategoriesJSONArray = jsonObject.optJSONArray("userDefinedCategories")
            ?: return false
        val db = context.openOrCreateDatabase("userDefinedCategories", Context.MODE_PRIVATE, null)
        db.execSQL(
            "create table if not exists categories(_id integer primary key autoincrement,label varchar,packages varchar)"
        )
        val existedLabels = ArrayList<String>()
        val cursor = db.query("categories", arrayOf("label"), null, null, null, null, null)
        if (cursor.moveToFirst()) {
            for (i in 0 until cursor.count) {
                existedLabels.add(cursor.getString(cursor.getColumnIndexOrThrow("label")))
                cursor.moveToNext()
            }
        }
        cursor.close()
        var oneUserDefinedCategoriesJSONObject: JSONObject?
        var isCompletelySuccess = true
        for (i in 0 until userDefinedCategoriesJSONArray.length()) {
            try {
                oneUserDefinedCategoriesJSONObject =
                    userDefinedCategoriesJSONArray.optJSONObject(i)
                if (oneUserDefinedCategoriesJSONObject == null) {
                    isCompletelySuccess = false
                    continue
                }
                if (oneUserDefinedCategoriesJSONObject.optBoolean("doNotImport", false)) {
                    continue
                }
                val label = oneUserDefinedCategoriesJSONObject.getString("label")
                if (existedLabels.contains(label)) {
                    db.execSQL(
                        "update categories set packages = '"
                                + oneUserDefinedCategoriesJSONObject.getString("packages")
                                + "' where label = '" + label + "';"
                    )
                } else {
                    db.execSQL(
                        "insert into categories(_id,label,packages) VALUES ( "
                                + null + ",'"
                                + label + "','"
                                + oneUserDefinedCategoriesJSONObject.getString("packages") + "');"
                    )
                }
            } catch (e: JSONException) {
                isCompletelySuccess = false
            }
        }
        db.close()
        return isCompletelySuccess
    }

    private fun importOneKeyLists(
        context: Context,
        jsonObject: JSONObject,
        appPreferences: AppPreferences
    ): Boolean {
        val array = jsonObject.optJSONArray("oneKeyList") ?: return false
        val oneKeyListJSONObject = array.optJSONObject(0) ?: return false
        importOneKeyListsFromProcessedJSONObject(
            context, oneKeyListJSONObject, appPreferences
        )
        return true
    }

    private fun importOneKeyListsFromProcessedJSONObject(
        context: Context, oneKeyListJSONObject: JSONObject, appPreferences: AppPreferences
    ) {
        val it = oneKeyListJSONObject.keys()
        var s: String
        while (it.hasNext()) {
            s = it.next()
            when (s) {
                "okff" -> appPreferences.put(
                    context.getString(R.string.sAutoFreezeApplicationList),
                    oneKeyListJSONObject.optString("okff")
                )
                "okuf" -> appPreferences.put(
                    context.getString(R.string.sOneKeyUFApplicationList),
                    oneKeyListJSONObject.optString("okuf")
                )
                "foq" -> appPreferences.put(
                    context.getString(R.string.sFreezeOnceQuit),
                    oneKeyListJSONObject.optString("foq")
                )
                else -> {}
            }
        }
    }

    private fun importIntSharedPreferences(
        context: Context, activity: Activity?,
        jsonObject: JSONObject, defSP: SharedPreferences
    ): Boolean {
        // Int 开始
        val array = jsonObject.optJSONArray("generalSettings_int") ?: return false
        val generalSettingsIntJSONObject = array.optJSONObject(0) ?: return false
        importIntSharedPreferencesFromProcessedJSONObject(
            context, activity, generalSettingsIntJSONObject, defSP
        )
        // Int 结束
        return true
    }

    private fun importIntSharedPreferencesFromProcessedJSONObject(
        context: Context, activity: Activity?,
        generalSettingsIntJSONObject: JSONObject, defSP: SharedPreferences
    ) {
        val it = generalSettingsIntJSONObject.keys()
        var s: String
        while (it.hasNext()) {
            s = it.next()
            when (s) {
                "onClickFunctionStatus", "sortMethodStatus" -> importIntSharedPreference(
                    context, activity, generalSettingsIntJSONObject, defSP, s
                )
                else -> {}
            }
        }
    }

    private fun importStringSharedPreferences(
        context: Context, activity: Activity?,
        jsonObject: JSONObject, defSP: SharedPreferences
    ): Boolean {
        // String 开始
        val array = jsonObject.optJSONArray("generalSettings_string") ?: return false
        val generalSettingsStringJSONObject = array.optJSONObject(0) ?: return false
        importStringSharedPreferencesFromProcessedJSONObject(
            context, activity, generalSettingsStringJSONObject, defSP
        )
        // String 结束
        return true
    }

    private fun importStringSharedPreferencesFromProcessedJSONObject(
        context: Context, activity: Activity?, generalSettingsStringJSONObject: JSONObject,
        defSP: SharedPreferences
    ) {
        val it = generalSettingsStringJSONObject.keys()
        var s: String?
        while (it.hasNext()) {
            s = it.next() ?: continue
            var key: cf.playhi.freezeyou.storage.key.AbstractKey<String?>? = null
            try {
                key = DefaultSharedPreferenceStorageStringKeys.valueOf(s)
            } catch (ignored: IllegalArgumentException) {
            }
            if (key == null) {
                try {
                    key = DefaultMultiProcessMMKVStorageStringKeys.valueOf(s)
                } catch (ignored: IllegalArgumentException) {
                }
            }
            if (key == null) continue
            importStringSharedPreference(
                context, activity, generalSettingsStringJSONObject, defSP, s
            )
        }
    }

    private fun importBooleanSharedPreferences(
        context: Context, activity: Activity?,
        jsonObject: JSONObject, defSP: SharedPreferences
    ): Boolean {
        // boolean 开始
        val array = jsonObject.optJSONArray("generalSettings_boolean") ?: return false
        val generalSettingsBooleanJSONObject = array.optJSONObject(0) ?: return false
        importBooleanSharedPreferencesFromProcessedJSONObject(
            context, activity, generalSettingsBooleanJSONObject, defSP
        )
        // boolean 结束
        return true
    }

    private fun importBooleanSharedPreferencesFromProcessedJSONObject(
        context: Context, activity: Activity?,
        generalSettingsBooleanJSONObject: JSONObject, defSP: SharedPreferences
    ) {
        val it = generalSettingsBooleanJSONObject.keys()
        var s: String?
        while (it.hasNext()) {
            s = it.next() ?: continue
            if (DefaultSharedPreferenceStorageBooleanKeys.firstIconEnabled.name == s || DefaultSharedPreferenceStorageBooleanKeys.secondIconEnabled.name == s || DefaultSharedPreferenceStorageBooleanKeys.thirdIconEnabled.name == s || DefaultMultiProcessMMKVStorageBooleanKeys.enableAuthentication.name == s) {
                continue
            }
            var key: cf.playhi.freezeyou.storage.key.AbstractKey<Boolean>? = null
            try {
                key = DefaultSharedPreferenceStorageBooleanKeys.valueOf(s)
            } catch (ignored: IllegalArgumentException) {
            }
            if (key == null) {
                try {
                    key = DefaultMultiProcessMMKVStorageBooleanKeys.valueOf(s)
                } catch (ignored: IllegalArgumentException) {
                }
            }
            if (key == null) continue
            importBooleanSharedPreference(
                context, activity, generalSettingsBooleanJSONObject, defSP, s
            )
        }
    }

    private fun importUriAutoAllowPkgsJSONArray(jsonObject: JSONObject, ap: AppPreferences): Boolean {
        val array = jsonObject.optJSONArray("uriAutoAllowPkgs_allows") ?: return false
        val jObj = array.optJSONObject(0) ?: return false
        return ap.put("uriAutoAllowPkgs_allows", jObj.optString("lists"))
    }

    private fun importInstallPkgsAutoAllowPkgsJSONArray(
        jsonObject: JSONObject,
        ap: AppPreferences
    ): Boolean {
        val array = jsonObject.optJSONArray("installPkgs_autoAllowPkgs_allows") ?: return false
        val jObj = array.optJSONObject(0) ?: return false
        return ap.put("installPkgs_autoAllowPkgs_allows", jObj.optString("lists"))
    }

    @JvmStatic
    fun importContents(context: Context, activity: Activity?, jsonObject: JSONObject) {
        val defSP = PreferenceManager.getDefaultSharedPreferences(context)
        val appPreferences = AppPreferences(context)
        val jsonKeysIterator = jsonObject.keys()
        while (jsonKeysIterator.hasNext()) {
            when (jsonKeysIterator.next()) {
                "generalSettings_boolean" -> importBooleanSharedPreferences(
                    context,
                    activity,
                    jsonObject,
                    defSP
                )
                "generalSettings_string" -> importStringSharedPreferences(
                    context,
                    activity,
                    jsonObject,
                    defSP
                )
                "generalSettings_int" -> importIntSharedPreferences(
                    context,
                    activity,
                    jsonObject,
                    defSP
                )
                "oneKeyList" -> importOneKeyLists(context, jsonObject, appPreferences)
                "userTimeScheduledTasks" -> importUserTimeTasksJSONArray(context, jsonObject)
                "userTriggerScheduledTasks" -> importUserTriggerTasksJSONArray(context, jsonObject)
                "userDefinedCategories" -> importUserDefinedCategoriesJSONArray(context, jsonObject)
                "uriAutoAllowPkgs_allows" -> importUriAutoAllowPkgsJSONArray(
                    jsonObject,
                    appPreferences
                )
                "installPkgs_autoAllowPkgs_allows" -> importInstallPkgsAutoAllowPkgsJSONArray(
                    jsonObject,
                    appPreferences
                )
                else -> {}
            }
        }
    }

    @JvmStatic
    fun getExportContent(context: Context): String {
        val appPreferences = AppPreferences(context)
        val defSP = PreferenceManager.getDefaultSharedPreferences(context)
        val finalOutputJsonObject = JSONObject()
        try {
            // 标记转出格式版本 开始
            val formatVersionJSONArray = JSONArray()
            val formatVersionJSONObject = JSONObject()
            formatVersionJSONObject.put("version", 1) // 标记备份文件格式版本
            formatVersionJSONObject.put("generateTime", Date().time) // 标记备份文件格式版本
            formatVersionJSONArray.put(formatVersionJSONObject)
            finalOutputJsonObject.put("format_version", formatVersionJSONArray)
            // 标记转出格式版本 结束

            // 通用设置转出（更多设置 中的选项，不转移图标选择相关设置） 开始

            // boolean 开始
            val generalSettingsBooleanJSONArray = JSONArray()
            val generalSettingsBooleanJSONObject = JSONObject()
            for (key in DefaultSharedPreferenceStorageBooleanKeys.values()) {
                generalSettingsBooleanJSONObject.put(key.name, key.getValue(context))
            }
            for (key in DefaultMultiProcessMMKVStorageBooleanKeys.values()) {
                generalSettingsBooleanJSONObject.put(key.name, key.getValue(context))
            }
            generalSettingsBooleanJSONArray.put(generalSettingsBooleanJSONObject)
            finalOutputJsonObject.put("generalSettings_boolean", generalSettingsBooleanJSONArray)
            // boolean 结束

            // String 开始
            val generalSettingsStringJSONArray = JSONArray()
            val generalSettingsStringJSONObject = JSONObject()
            for (key in DefaultSharedPreferenceStorageStringKeys.values()) {
                generalSettingsStringJSONObject.put(key.name, key.getValue(context))
            }
            for (key in DefaultMultiProcessMMKVStorageStringKeys.values()) {
                generalSettingsStringJSONObject.put(key.name, key.getValue(context))
            }
            generalSettingsStringJSONArray.put(generalSettingsStringJSONObject)
            finalOutputJsonObject.put("generalSettings_string", generalSettingsStringJSONArray)
            // String 结束

            // Int 开始
            val generalSettingsIntJSONArray = JSONArray()
            val generalSettingsIntJSONObject = JSONObject()
            generalSettingsIntJSONObject.put(
                "onClickFunctionStatus",
                defSP.getInt("onClickFunctionStatus", 0)
            )
            generalSettingsIntJSONObject.put(
                "sortMethodStatus",
                defSP.getInt("sortMethodStatus", 0)
            )
            generalSettingsIntJSONArray.put(generalSettingsIntJSONObject)
            finalOutputJsonObject.put("generalSettings_int", generalSettingsIntJSONArray)
            // Int 结束

            // 通用设置转出 结束

            // 一键冻结、一键解冻、离开冻结列表 开始
            val oneKeyListJSONArray = JSONArray()
            val oneKeyListJSONObject = JSONObject()
            oneKeyListJSONObject.put(
                "okff",
                appPreferences.getString(context.getString(R.string.sAutoFreezeApplicationList), "")
            )
            oneKeyListJSONObject.put(
                "okuf",
                appPreferences.getString(context.getString(R.string.sOneKeyUFApplicationList), "")
            )
            oneKeyListJSONObject.put(
                "foq",
                appPreferences.getString(context.getString(R.string.sFreezeOnceQuit), "")
            )
            oneKeyListJSONArray.put(oneKeyListJSONObject)
            finalOutputJsonObject.put("oneKeyList", oneKeyListJSONArray)
            // 一键冻结、一键解冻、离开冻结列表 结束

            // 安装应用请求、URI 请求白名单 开始
            finalOutputJsonObject.put(
                "uriAutoAllowPkgs_allows",
                generateUriAutoAllowPkgsJSONArray(context)
            )
            finalOutputJsonObject.put(
                "installPkgs_autoAllowPkgs_allows",
                generateInstallPkgsAutoAllowPkgsJSONArray(context)
            )
            // 安装应用请求、URI 请求白名单 结束

            // 计划任务 - 时间 开始
            finalOutputJsonObject.put("userTimeScheduledTasks", generateUserTimeTasksJSONArray(context))
            // 计划任务 - 时间 结束

            // 计划任务 - 触发器 开始
            finalOutputJsonObject.put(
                "userTriggerScheduledTasks",
                generateUserTriggerTasksJSONArray(context)
            )
            // 计划任务 - 触发器 结束

            // 用户自定分类（我的列表） 开始
            finalOutputJsonObject.put(
                "userDefinedCategories",
                generateUserDefinedCategoriesJSONArray(context)
            )
            // 用户自定分类（我的列表） 结束
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return finalOutputJsonObject.toString()
    }

    @Throws(JSONException::class)
    private fun generateUserTimeTasksJSONArray(context: Context): JSONArray {
        val userTimeScheduledTasksJSONArray = JSONArray()
        val db = context.openOrCreateDatabase("scheduledTasks", Context.MODE_PRIVATE, null)
        db.execSQL(
            "create table if not exists tasks(_id integer primary key autoincrement,hour integer(2),minutes integer(2),repeat varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
        )
        val cursor = db.query(
            "tasks", null, null, null,
            null, null, null
        )
        if (cursor.moveToFirst()) {
            var ifContinue = true
            while (ifContinue) {
                val oneUserTimeScheduledTaskJSONObject = JSONObject()
                oneUserTimeScheduledTaskJSONObject.put(
                    "hour",
                    cursor.getInt(cursor.getColumnIndexOrThrow("hour"))
                )
                oneUserTimeScheduledTaskJSONObject.put(
                    "minutes",
                    cursor.getInt(cursor.getColumnIndexOrThrow("minutes"))
                )
                oneUserTimeScheduledTaskJSONObject.put(
                    "enabled",
                    cursor.getInt(cursor.getColumnIndexOrThrow("enabled"))
                )
                oneUserTimeScheduledTaskJSONObject.put(
                    "label",
                    cursor.getString(cursor.getColumnIndexOrThrow("label"))
                )
                oneUserTimeScheduledTaskJSONObject.put(
                    "task",
                    cursor.getString(cursor.getColumnIndexOrThrow("task"))
                )
                oneUserTimeScheduledTaskJSONObject.put(
                    "repeat",
                    cursor.getString(cursor.getColumnIndexOrThrow("repeat"))
                )
                userTimeScheduledTasksJSONArray.put(oneUserTimeScheduledTaskJSONObject)
                ifContinue = cursor.moveToNext()
            }
        }
        cursor.close()
        db.close()
        return userTimeScheduledTasksJSONArray
    }

    @Throws(JSONException::class)
    private fun generateUserTriggerTasksJSONArray(context: Context): JSONArray {
        val userTriggerScheduledTasksJSONArray = JSONArray()
        val db = context.openOrCreateDatabase("scheduledTriggerTasks", Context.MODE_PRIVATE, null)
        db.execSQL(
            "create table if not exists tasks(_id integer primary key autoincrement,tg varchar,tgextra varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
        )
        val cursor = db.query(
            "tasks", null, null, null,
            null, null, null
        )
        if (cursor.moveToFirst()) {
            var ifContinue = true
            while (ifContinue) {
                val oneUserTriggerScheduledTaskJSONObject = JSONObject()
                oneUserTriggerScheduledTaskJSONObject.put(
                    "tgextra",
                    cursor.getString(cursor.getColumnIndexOrThrow("tgextra"))
                )
                oneUserTriggerScheduledTaskJSONObject.put(
                    "enabled",
                    cursor.getInt(cursor.getColumnIndexOrThrow("enabled"))
                )
                oneUserTriggerScheduledTaskJSONObject.put(
                    "label",
                    cursor.getString(cursor.getColumnIndexOrThrow("label"))
                )
                oneUserTriggerScheduledTaskJSONObject.put(
                    "task",
                    cursor.getString(cursor.getColumnIndexOrThrow("task"))
                )
                oneUserTriggerScheduledTaskJSONObject.put(
                    "tg",
                    cursor.getString(cursor.getColumnIndexOrThrow("tg"))
                )
                userTriggerScheduledTasksJSONArray.put(oneUserTriggerScheduledTaskJSONObject)
                ifContinue = cursor.moveToNext()
            }
        }
        cursor.close()
        db.close()
        return userTriggerScheduledTasksJSONArray
    }

    @Throws(JSONException::class)
    private fun generateUserDefinedCategoriesJSONArray(context: Context): JSONArray {
        val userDefinedCategoriesJSONArray = JSONArray()
        val db = context.openOrCreateDatabase("userDefinedCategories", Context.MODE_PRIVATE, null)
        db.execSQL(
            "create table if not exists categories(_id integer primary key autoincrement,label varchar,packages varchar)"
        )
        val cursor = db.query(
            "categories", arrayOf("label", "packages"),
            null, null, null,
            null, null
        )
        if (cursor.moveToFirst()) {
            var ifContinue = true
            while (ifContinue) {
                val oneUserDefinedCategoriesJSONObject = JSONObject()
                oneUserDefinedCategoriesJSONObject.put(
                    "label",
                    cursor.getString(cursor.getColumnIndexOrThrow("label"))
                )
                oneUserDefinedCategoriesJSONObject.put(
                    "packages",
                    cursor.getString(cursor.getColumnIndexOrThrow("packages"))
                )
                userDefinedCategoriesJSONArray.put(oneUserDefinedCategoriesJSONObject)
                ifContinue = cursor.moveToNext()
            }
        }
        cursor.close()
        db.close()
        return userDefinedCategoriesJSONArray
    }

    @Throws(JSONException::class)
    private fun generateUriAutoAllowPkgsJSONArray(context: Context): JSONArray {
        val userDefinedCategoriesJSONArray = JSONArray()
        val oneUserDefinedCategoriesJSONObject = JSONObject()
        val ap = AppPreferences(context)
        oneUserDefinedCategoriesJSONObject.put(
            "lists",
            convertSharedPreference(ap, "uriAutoAllowPkgs_allows", "")
        )
        userDefinedCategoriesJSONArray.put(oneUserDefinedCategoriesJSONObject)
        return userDefinedCategoriesJSONArray
    }

    @Throws(JSONException::class)
    private fun generateInstallPkgsAutoAllowPkgsJSONArray(context: Context): JSONArray {
        val userDefinedCategoriesJSONArray = JSONArray()
        val oneUserDefinedCategoriesJSONObject = JSONObject()
        val ap = AppPreferences(context)
        oneUserDefinedCategoriesJSONObject.put(
            "lists",
            convertSharedPreference(ap, "installPkgs_autoAllowPkgs_allows", "")
        )
        userDefinedCategoriesJSONArray.put(oneUserDefinedCategoriesJSONObject)
        return userDefinedCategoriesJSONArray
    }
}
