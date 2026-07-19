package cf.playhi.freezeyou.adapter

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.SimpleAdapter
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.utils.TasksUtils
import com.google.android.material.switchmaterial.SwitchMaterial

class ScheduledTasksManageSimpleAdapter(
    private val mContext: Context,
    private val mTasksList: MutableList<MutableMap<String, Any?>>,
    private val mIdIndexArrayList: List<Int>,
    resource: Int,
    from: Array<String>,
    to: IntArray
) : SimpleAdapter(mContext, mTasksList, resource, from, to) {

    fun replaceAllInFormerArrayList(list: List<MutableMap<String, Any?>>): Boolean {
        mTasksList.clear()
        val b = mTasksList.addAll(list)
        notifyDataSetChanged()
        return b
    }

    fun getStoredArrayList(): MutableList<MutableMap<String, Any?>> {
        return mTasksList
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val sc = view.findViewById<SwitchMaterial>(R.id.stma_switch)
        sc.setOnCheckedChangeListener { _, isChecked ->
            val timeS = mTasksList[position]["time"] as String?
            mTasksList[position]["enabled"] = isChecked
            val db = mContext.openOrCreateDatabase(
                if (timeS != null && timeS.contains(":")) "scheduledTasks" else "scheduledTriggerTasks",
                Context.MODE_PRIVATE,
                null
            )
            db.execSQL(
                "create table if not exists tasks(_id integer primary key autoincrement,hour integer(2),minutes integer(2),repeat varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
            )
            db.execSQL(
                "UPDATE tasks SET enabled = " + (if (isChecked) 1 else 0) +
                        " WHERE _id = " + mIdIndexArrayList[position] + ";"
            )
            db.close()
            if (timeS != null && timeS.contains(":")) {
                TasksUtils.checkTimeTasks(mContext)
            }
            notifyDataSetChanged()
        }
        return view
    }
}
