package cf.playhi.freezeyou.adapter

import android.content.Context
import android.widget.SimpleAdapter

class ReplaceableSimpleAdapter(
    context: Context,
    private val mAppList: MutableList<MutableMap<String, Any?>>,
    resource: Int,
    from: Array<String>,
    to: IntArray
) : SimpleAdapter(context, mAppList, resource, from, to) {

    fun replaceAllInFormerArrayList(list: List<MutableMap<String, Any?>>): Boolean {
        mAppList.clear()
        val b = mAppList.addAll(list)
        notifyDataSetChanged()
        return b
    }

    fun getStoredArrayList(): MutableList<MutableMap<String, Any?>> {
        return mAppList
    }
}
