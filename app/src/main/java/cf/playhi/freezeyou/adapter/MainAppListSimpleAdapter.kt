package cf.playhi.freezeyou.adapter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SimpleAdapter
import cf.playhi.freezeyou.R

class MainAppListSimpleAdapter(
    context: Context,
    private val mAppList: MutableList<MutableMap<String, Any?>>,
    private val mIsCheckedPackageList: List<String>,
    resource: Int,
    from: Array<String>,
    to: IntArray
) : SimpleAdapter(context, mAppList, resource, from, to) {

    init {
        setViewBinder { view, data, _ ->
            if (view is ImageView) {
                when (data) {
                    is Drawable -> {
                        view.setImageDrawable(data)
                        true
                    }
                    is Bitmap -> {
                        view.setImageBitmap(data)
                        true
                    }
                    else -> false
                }
            } else false
        }
    }

    fun replaceAllInFormerArrayList(list: List<MutableMap<String, Any?>>): Boolean {
        mAppList.clear()
        val b = mAppList.addAll(list)
        notifyDataSetChanged()
        return b
    }

    fun getStoredArrayList(): MutableList<MutableMap<String, Any?>> {
        return mAppList
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        if (mIsCheckedPackageList.contains(mAppList[position]["PackageName"] as String?)) {
            view.setBackgroundResource(R.color.translucentGreyBackground)
        } else {
            view.setBackgroundResource(0)
        }
        return view
    }
}
