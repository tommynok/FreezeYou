package cf.playhi.freezeyou.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;

import cf.playhi.freezeyou.utils.TasksUtils;

public class TasksNeedExecuteReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        int id = intent.getIntExtra("id", -5);
        int hour = intent.getIntExtra("hour", -1);
        int minute = intent.getIntExtra("minute", -1);
        String task = intent.getStringExtra("task");
        String repeat = intent.getStringExtra("repeat");
        if (id != -6) { // -6 is delayed task
            if ("0".equals(repeat)) {
                if (id != -5) {
                    SQLiteDatabase db =
                            context.openOrCreateDatabase("scheduledTasks",
                                    Context.MODE_PRIVATE, null);
                    db.execSQL("UPDATE tasks SET enabled = 0 WHERE _id = " + id + ";");
                    db.close();
                }
            } else {
                TasksUtils.publishTask(context, id, hour, minute, repeat, task);
            }
        } else {
            int requestCode = intent.getIntExtra("requestCode", -1);
            String taskTrigger = intent.getStringExtra("taskTrigger");
            if (requestCode != -1 && taskTrigger != null) {
                TasksUtils.forgetDelayTaskId(taskTrigger, requestCode);
            }
        }
        if (task != null && !"".equals(task)) {
            String trigger = (id != -6 && id != -5) ? ("timedTask_" + id) : null;
            TasksUtils.runTask(task, context.getApplicationContext(), trigger);
        }
    }
}
