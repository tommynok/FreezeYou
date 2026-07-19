package cf.playhi.freezeyou.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cf.playhi.freezeyou.utils.TasksUtils

class TasksNeedExecuteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", -5)
        val hour = intent.getIntExtra("hour", -1)
        val minute = intent.getIntExtra("minute", -1)
        val task = intent.getStringExtra("task")
        val repeat = intent.getStringExtra("repeat")
        if (id != -6) { //-6为延时任务
            if ("0" == repeat && id != -5) {
                val db = context.openOrCreateDatabase(
                    "scheduledTasks",
                    Context.MODE_PRIVATE, null
                )
                db.execSQL("UPDATE tasks SET enabled = 0 WHERE _id = $id;")
                db.close()
            } else {
                TasksUtils.publishTask(context, id, hour, minute, repeat ?: "0", task)
            }
        }
        if (!task.isNullOrEmpty()) {
            TasksUtils.runTask(task, context.applicationContext, null)
        }
    }
}
