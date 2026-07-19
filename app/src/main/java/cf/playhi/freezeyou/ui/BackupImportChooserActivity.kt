package cf.playhi.freezeyou.ui

import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.ListView
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.adapter.BackupImportChooserActivitySwitchSimpleAdapter
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageStringKeys
import cf.playhi.freezeyou.utils.BackupUtils
import cf.playhi.freezeyou.utils.ThemeUtils.processActionBar
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import cf.playhi.freezeyou.utils.ToastUtils
import org.json.JSONException
import org.json.JSONObject

class BackupImportChooserActivity : FreezeYouBaseActivity() {
    private val keyToStringIdValuePair = HashMap<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        processActionBar(supportActionBar)
        setContentView(R.layout.bica_main)
        onCreateInit()
    }

    private fun onCreateInit() {
        val intent = intent
        if (intent == null) {
            finish()
            return
        }
        val mainListView = findViewById<ListView>(R.id.bica_main_listView)
        val titleAndSpKeyArrayList = ArrayList<MutableMap<String, String>>()
        generateKeyToStringIdValuePair()
        val jsonContentString = intent.getStringExtra("jsonObjectString")
        var jsonObject: JSONObject? = null
        if (jsonContentString == null) {
            val keyValuePair = HashMap<String, String>()
            keyValuePair["title"] = getString(R.string.failed)
            keyValuePair["spKey"] = "Failed!"
            keyValuePair["category"] = "Failed!"
            titleAndSpKeyArrayList.add(keyValuePair)
        } else {
            try {
                jsonObject = JSONObject(jsonContentString)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            if (jsonObject == null) {
                val keyValuePair = HashMap<String, String>()
                keyValuePair["title"] = getString(R.string.parseFailed)
                keyValuePair["spKey"] = "Failed!"
                keyValuePair["category"] = "Failed!"
                titleAndSpKeyArrayList.add(keyValuePair)
            } else {
                generateList(jsonObject, titleAndSpKeyArrayList)
                if (titleAndSpKeyArrayList.size == 0) {
                    val keyValuePair = HashMap<String, String>()
                    keyValuePair["title"] = getString(R.string.nothing)
                    keyValuePair["spKey"] = "Failed!"
                    keyValuePair["category"] = "Failed!"
                    titleAndSpKeyArrayList.add(keyValuePair)
                }
            }
        }
        val adapter = BackupImportChooserActivitySwitchSimpleAdapter(
            this,
            jsonObject,
            titleAndSpKeyArrayList,
            R.layout.bica_list_item,
            arrayOf("title"),
            intArrayOf(R.id.bica_list_item_switch)
        )
        mainListView.adapter = adapter
        processButtons()
    }

    private fun processButtons() {
        val bicaFinishButton = findViewById<Button>(R.id.bica_finish_button)
        val bicaCancelButton = findViewById<Button>(R.id.bica_cancel_button)
        bicaCancelButton.setOnClickListener { finish() }
        bicaFinishButton.setOnClickListener {
            val mainListView = findViewById<ListView>(R.id.bica_main_listView)
            val adapter =
                mainListView.adapter as BackupImportChooserActivitySwitchSimpleAdapter
            BackupUtils.importContents(
                applicationContext,
                this@BackupImportChooserActivity, adapter.getFinalList()
            )
            ToastUtils.showToast(this@BackupImportChooserActivity, R.string.finish)
            finish()
        }
    }

    private fun generateKeyToStringIdValuePair() {
        // Int 开始
        keyToStringIdValuePair["onClickFunctionStatus"] = getString(R.string.onClickFunctionStatus)
        keyToStringIdValuePair["sortMethodStatus"] = getString(R.string.sortMethodStatus)
        // Int 结束
        // 一键冻结、一键解冻、离开冻结列表 开始
        keyToStringIdValuePair["okff"] = getString(R.string.oneKeyFreezeList)
        keyToStringIdValuePair["okuf"] = getString(R.string.oneKeyUFList)
        keyToStringIdValuePair["foq"] = getString(R.string.freezeOnceQuitList)
        // 一键冻结、一键解冻、离开冻结列表 结束
        // 安装应用请求、URI 请求白名单 开始
        keyToStringIdValuePair["uriAutoAllowPkgs_allows"] = getString(R.string.uriAutoAllowList)
        keyToStringIdValuePair["installPkgs_autoAllowPkgs_allows"] =
            getString(R.string.ipaAutoAllowList)
        // 安装应用请求、URI 请求白名单 结束
    }

    private fun generateList(
        jsonObject: JSONObject,
        list: ArrayList<MutableMap<String, String>>
    ) {
        val jsonKeysIterator = jsonObject.keys()
        while (jsonKeysIterator.hasNext()) {
            when (jsonKeysIterator.next()) {
                "generalSettings_boolean" -> generateGeneralSettingsBooleanList(jsonObject, list)
                "generalSettings_string" -> generateGeneralSettingsStringList(jsonObject, list)
                "generalSettings_int" -> generateGeneralSettingsIntList(jsonObject, list)
                "oneKeyList" -> generateOneKeyList(jsonObject, list)
                "userTimeScheduledTasks" -> generateUserTimeScheduledTasksList(jsonObject, list)
                "userTriggerScheduledTasks" -> generateUserTriggerScheduledTasksList(
                    jsonObject,
                    list
                )
                "userDefinedCategories" -> generateUserDefinedCategoriesList(jsonObject, list)
                "uriAutoAllowPkgs_allows" -> generateUriAutoAllowPkgsList(jsonObject, list)
                "installPkgs_autoAllowPkgs_allows" -> generateInstallPkgsAutoAllowPkgsList(
                    jsonObject,
                    list
                )
                else -> {}
            }
        }
    }

    private fun generateGeneralSettingsBooleanList(
        jsonObject: JSONObject,
        list: ArrayList<MutableMap<String, String>>
    ) {
        val array = jsonObject.optJSONArray("generalSettings_boolean") ?: return
        val generalSettingsBooleanJSONObject = array.optJSONObject(0) ?: return
        val it = generalSettingsBooleanJSONObject.keys()
        val moreSettingsDashLineLabel = getString(R.string.moreSettingsDashLineLabel)
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
            val keyValuePair = HashMap<String, String>()
            keyValuePair["title"] = String.format(moreSettingsDashLineLabel, getString(key.titleTextStringId()))
            keyValuePair["spKey"] = s
            keyValuePair["category"] = "generalSettings_boolean"
            list.add(keyValuePair)
        }
    }

    private fun generateGeneralSettingsIntList(
        jsonObject: JSONObject,
        list: ArrayList<MutableMap<String, String>>
    ) {
        val array = jsonObject.optJSONArray("generalSettings_int") ?: return
        val generalSettingsIntJSONObject = array.optJSONObject(0) ?: return
        val it = generalSettingsIntJSONObject.keys()
        var s: String
        while (it.hasNext()) {
            s = it.next()
            when (s) {
                "onClickFunctionStatus", "sortMethodStatus" -> {
                    val keyValuePair = HashMap<String, String>()
                    keyValuePair["title"] =
                        if (keyToStringIdValuePair.containsKey(s)) keyToStringIdValuePair[s]!! else s
                    keyValuePair["spKey"] = s
                    keyValuePair["category"] = "generalSettings_int"
                    list.add(keyValuePair)
                }
                else -> {}
            }
        }
    }

    private fun generateGeneralSettingsStringList(
        jsonObject: JSONObject,
        list: ArrayList<MutableMap<String, String>>
    ) {
        val array = jsonObject.optJSONArray("generalSettings_string") ?: return
        val generalSettingsStringJSONObject = array.optJSONObject(0) ?: return
        val moreSettingsDashLineLabel = getString(R.string.moreSettingsDashLineLabel)
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
            val keyValuePair = HashMap<String, String>()
            keyValuePair["title"] = String.format(moreSettingsDashLineLabel, getString(key.titleTextStringId()))
            keyValuePair["spKey"] = s
            keyValuePair["category"] = "generalSettings_string"
            list.add(keyValuePair)
        }
    }

    private fun generateOneKeyList(
        jsonObject: JSONObject,
        list: ArrayList<MutableMap<String, String>>
    ) {
        val array = jsonObject.optJSONArray("oneKeyList") ?: return
        val generalOneKeyJSONObject = array.optJSONObject(0) ?: return
        val it = generalOneKeyJSONObject.keys()
        var s: String
        while (it.hasNext()) {
            s = it.next()
            when (s) {
                "okff", "okuf", "foq" -> {
                    val keyValuePair = HashMap<String, String>()
                    keyValuePair["title"] =
                        if (keyToStringIdValuePair.containsKey(s)) keyToStringIdValuePair[s]!! else s
                    keyValuePair["spKey"] = s
                    keyValuePair["category"] = "oneKeyList"
                    list.add(keyValuePair)
                }
                else -> {}
            }
        }
    }

    private fun generateUserTimeScheduledTasksList(
        jsonObject: JSONObject,
        list: ArrayList<MutableMap<String, String>>
    ) {
        val array = jsonObject.optJSONArray("userTimeScheduledTasks") ?: return
        var oneUserTimeScheduledTaskJSONObject: JSONObject?
        val scheduledTaskDashLineLabel = getString(R.string.scheduledTaskDashLineLabel)
        val defaultLabel = getString(R.string.label)
        for (i in 0 until array.length()) {
            oneUserTimeScheduledTaskJSONObject = array.optJSONObject(i)
            if (oneUserTimeScheduledTaskJSONObject == null) {
                continue
            }
            val keyValuePair = HashMap<String, String>()
            keyValuePair["title"] = String.format(
                scheduledTaskDashLineLabel,
                oneUserTimeScheduledTaskJSONObject.optString("label", defaultLabel)
            )
            try {
                oneUserTimeScheduledTaskJSONObject.put("i", i.toString())
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            keyValuePair["spKey"] = i.toString()
            keyValuePair["category"] = "userTimeScheduledTasks"
            list.add(keyValuePair)
        }
    }

    private fun generateUserTriggerScheduledTasksList(
        jsonObject: JSONObject,
        list: ArrayList<MutableMap<String, String>>
    ) {
        val array = jsonObject.optJSONArray("userTriggerScheduledTasks") ?: return
        var oneUserTriggerScheduledTaskJSONObject: JSONObject?
        val scheduledTaskDashLineLabel = getString(R.string.scheduledTaskDashLineLabel)
        val defaultLabel = getString(R.string.label)
        for (i in 0 until array.length()) {
            oneUserTriggerScheduledTaskJSONObject = array.optJSONObject(i)
            if (oneUserTriggerScheduledTaskJSONObject == null) {
                continue
            }
            val keyValuePair = HashMap<String, String>()
            keyValuePair["title"] = String.format(
                scheduledTaskDashLineLabel,
                oneUserTriggerScheduledTaskJSONObject.optString("label", defaultLabel)
            )
            try {
                oneUserTriggerScheduledTaskJSONObject.put("i", i.toString())
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            keyValuePair["spKey"] = i.toString()
            keyValuePair["category"] = "userTriggerScheduledTasks"
            list.add(keyValuePair)
        }
    }

    private fun generateUserDefinedCategoriesList(
        jsonObject: JSONObject,
        list: ArrayList<MutableMap<String, String>>
    ) {
        val array = jsonObject.optJSONArray("userDefinedCategories") ?: return
        var oneUserDefinedCategoriesJSONObject: JSONObject?
        val myCustomizationDashLineLabel = getString(R.string.myCustomizationDashLineLabel)
        val defaultLabel = ""
        for (i in 0 until array.length()) {
            oneUserDefinedCategoriesJSONObject = array.optJSONObject(i)
            if (oneUserDefinedCategoriesJSONObject == null) {
                continue
            }
            val keyValuePair = HashMap<String, String>()
            keyValuePair["title"] = String.format(
                myCustomizationDashLineLabel,
                String(
                    Base64.decode(
                        oneUserDefinedCategoriesJSONObject.optString(
                            "label", defaultLabel
                        ),
                        Base64.DEFAULT
                    )
                )
            )
            try {
                oneUserDefinedCategoriesJSONObject.put("i", i.toString())
            } catch (e: JSONException) {
                e.printStackTrace()
            }
            keyValuePair["spKey"] = i.toString()
            keyValuePair["category"] = "userDefinedCategories"
            list.add(keyValuePair)
        }
    }

    private fun generateUriAutoAllowPkgsList(
        jsonObject: JSONObject,
        list: ArrayList<MutableMap<String, String>>
    ) {
        val array = jsonObject.optJSONArray("uriAutoAllowPkgs_allows") ?: return
        val jObj = array.optJSONObject(0) ?: return
        val keyValuePair = HashMap<String, String>()
        keyValuePair["title"] = if (keyToStringIdValuePair.containsKey("uriAutoAllowPkgs_allows"))
            keyToStringIdValuePair["uriAutoAllowPkgs_allows"]!!
        else
            "uriAutoAllowPkgs_allows"
        keyValuePair["spKey"] = "uriAutoAllowPkgs_allows"
        keyValuePair["category"] = "uriAutoAllowPkgs_allows"
        list.add(keyValuePair)
    }

    private fun generateInstallPkgsAutoAllowPkgsList(
        jsonObject: JSONObject,
        list: ArrayList<MutableMap<String, String>>
    ) {
        val array = jsonObject.optJSONArray("installPkgs_autoAllowPkgs_allows") ?: return
        val jObj = array.optJSONObject(0) ?: return
        val keyValuePair = HashMap<String, String>()
        keyValuePair["title"] =
            if (keyToStringIdValuePair.containsKey("installPkgs_autoAllowPkgs_allows"))
                keyToStringIdValuePair["installPkgs_autoAllowPkgs_allows"]!!
            else
                "installPkgs_autoAllowPkgs_allows"
        keyValuePair["spKey"] = "installPkgs_autoAllowPkgs_allows"
        keyValuePair["category"] = "installPkgs_autoAllowPkgs_allows"
        list.add(keyValuePair)
    }
}
