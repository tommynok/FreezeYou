package cf.playhi.freezeyou.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.SimpleAdapter
import cf.playhi.freezeyou.R
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class BackupImportChooserActivitySwitchSimpleAdapter(
    context: Context,
    jsonObject: JSONObject?,
    private val mData: List<MutableMap<String, String>>,
    resource: Int,
    from: Array<String>,
    to: IntArray
) : SimpleAdapter(context, mData, resource, from, to) {

    private val needExcludeData = mutableListOf<Map<String, String>>()
    private val isDisabledList = mutableListOf<Int>()
    private var mJsonObject: JSONObject? = null

    init {
        if (jsonObject != null) {
            try {
                mJsonObject = JSONObject(jsonObject.toString())
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val s = view.findViewById<SwitchMaterial>(R.id.bica_list_item_switch)

        if (s != null) {
            s.setOnCheckedChangeListener(null)
            val category = mData[position]["category"]
            s.isChecked = !isDisabledList.contains(position)
            if ("Failed!" == category) {
                s.isChecked = true
                s.isEnabled = false
            }
            s.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    isDisabledList.remove(position)
                    needExcludeData.remove(mData[position])
                } else {
                    if (!needExcludeData.contains(mData[position])) {
                        needExcludeData.add(mData[position])
                    }
                    if (!isDisabledList.contains(position)) {
                        isDisabledList.add(position)
                    }
                }
            }
        }
        return view
    }

    fun getFinalList(): JSONObject {
        val jsonObject = mJsonObject ?: return JSONObject()

        for (hm in needExcludeData) {
            val category = hm["category"] ?: continue
            val spKey = hm["spKey"] ?: continue
            val array = jsonObject.optJSONArray(category) ?: continue
            when (category) {
                "generalSettings_boolean", "generalSettings_string", "generalSettings_int", "oneKeyList" -> {
                    val jsonObj = array.optJSONObject(0) ?: continue
                    jsonObj.remove(spKey)
                }
                "userTimeScheduledTasks", "userTriggerScheduledTasks" -> {
                    for (j in 0 until array.length()) {
                        val item = array.optJSONObject(j)
                        if (item == null || spKey != item.optString("i", "-1")) continue
                        try {
                            item.put("doNotImport", true)
                        } catch (e: JSONException) {
                            e.printStackTrace()
                        }
                    }
                }
                else -> {}
            }
        }
        return jsonObject
    }
}
