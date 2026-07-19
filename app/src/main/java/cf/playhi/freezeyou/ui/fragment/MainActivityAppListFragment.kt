package cf.playhi.freezeyou.ui.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.GridView
import android.widget.ListAdapter
import android.widget.ListView
import androidx.fragment.app.Fragment
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.adapter.MainAppListSimpleAdapter

class MainActivityAppListFragment : Fragment() {
    private var mUseGridMode = false
    private var mOnItemClickListener: AdapterView.OnItemClickListener? = null
    private var mOnItemLongClickListener: AdapterView.OnItemLongClickListener? = null
    private var mMultiChoiceModeListener: AbsListView.MultiChoiceModeListener? = null
    private var mAppListAdapter: ListAdapter? = null
    private var mAppListGridView: GridView? = null
    private var mAppListListView: ListView? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view: View
        if (mUseGridMode) {
            view = inflater.inflate(R.layout.main_app_grid_fragment, container, false)
            mAppListGridView = view.findViewById(R.id.main_appList_gridView)
            if (mOnItemClickListener != null) mAppListGridView!!.onItemClickListener =
                mOnItemClickListener
            if (mOnItemLongClickListener != null) mAppListGridView!!.onItemLongClickListener =
                mOnItemLongClickListener
            if (mMultiChoiceModeListener != null) mAppListGridView!!.setMultiChoiceModeListener(
                mMultiChoiceModeListener
            )
            if (mAppListAdapter != null) mAppListGridView!!.adapter = mAppListAdapter
            mAppListGridView!!.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE_MODAL
            mAppListGridView!!.columnWidth =
                (resources.getDimension(android.R.dimen.app_icon_size) * 1.6).toInt()
        } else {
            view = inflater.inflate(R.layout.main_app_list_fragment, container, false)
            mAppListListView = view.findViewById(R.id.main_appList_listView)
            if (mOnItemClickListener != null) mAppListListView!!.onItemClickListener =
                mOnItemClickListener
            if (mOnItemLongClickListener != null) mAppListListView!!.onItemLongClickListener =
                mOnItemLongClickListener
            if (mMultiChoiceModeListener != null) mAppListListView!!.setMultiChoiceModeListener(
                mMultiChoiceModeListener
            )
            if (mAppListAdapter != null) mAppListListView!!.adapter = mAppListAdapter
        }
        return view
    }

    fun setUseGridMode(b: Boolean) {
        mUseGridMode = b
    }

    fun setOnAppListItemClickListener(listener: AdapterView.OnItemClickListener?) {
        mOnItemClickListener = listener
        if (mUseGridMode) {
            if (mAppListGridView != null) {
                mAppListGridView!!.onItemClickListener = mOnItemClickListener
            }
        } else {
            if (mAppListListView != null) {
                mAppListListView!!.onItemClickListener = mOnItemClickListener
            }
        }
    }

    fun setOnAppListItemLongClickListener(listener: AdapterView.OnItemLongClickListener?) {
        mOnItemLongClickListener = listener
        if (mUseGridMode) {
            if (mAppListGridView != null) {
                mAppListGridView!!.onItemLongClickListener = mOnItemLongClickListener
            }
        } else {
            if (mAppListListView != null) {
                mAppListListView!!.onItemLongClickListener = mOnItemLongClickListener
            }
        }
    }

    fun setMultiChoiceModeListener(listener: AbsListView.MultiChoiceModeListener?) {
        mMultiChoiceModeListener = listener
        if (mUseGridMode) {
            if (mAppListGridView != null) {
                mAppListGridView!!.setMultiChoiceModeListener(mMultiChoiceModeListener)
            }
        } else {
            if (mAppListListView != null) {
                mAppListListView!!.setMultiChoiceModeListener(mMultiChoiceModeListener)
            }
        }
    }

    fun setAppListAdapter(
        context: Context, appList: ArrayList<MutableMap<String, Any?>>,
        selectedPackages: ArrayList<String>
    ): MainAppListSimpleAdapter? {
        if (mAppListAdapter is MainAppListSimpleAdapter) {
            (mAppListAdapter as MainAppListSimpleAdapter).replaceAllInFormerArrayList(appList)
        } else {
            mAppListAdapter = MainAppListSimpleAdapter(
                context,
                appList,
                selectedPackages,
                if (mUseGridMode) R.layout.main_grid_main_item else R.layout.app_list_1,
                arrayOf("Img", "Name", "PackageName", "isFrozen"),
                if (mUseGridMode) intArrayOf(
                    R.id.mgmi_imageView,
                    R.id.mgmi_textView
                ) else intArrayOf(R.id.img, R.id.name, R.id.pkgName, R.id.isFrozen)
            )
        }
        val activity = activity
        if (activity != null) {
            if (mUseGridMode) {
                if (mAppListGridView != null) {
                    activity.runOnUiThread { mAppListGridView!!.adapter = mAppListAdapter }
                }
            } else {
                if (mAppListListView != null) {
                    activity.runOnUiThread { mAppListListView!!.adapter = mAppListAdapter }
                }
            }
        }
        return mAppListAdapter as MainAppListSimpleAdapter?
    }

    fun getAppListAdapter(): ListAdapter? {
        return mAppListAdapter
    }

    fun setItemChecked(position: Int, value: Boolean) {
        if (mUseGridMode) {
            if (mAppListGridView != null) {
                mAppListGridView!!.setItemChecked(position, value)
            }
        } else {
            if (mAppListListView != null) {
                mAppListListView!!.setItemChecked(position, value)
            }
        }
    }
}
