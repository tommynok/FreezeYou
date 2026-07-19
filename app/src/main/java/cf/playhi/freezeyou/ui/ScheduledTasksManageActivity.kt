package cf.playhi.freezeyou.ui

import android.content.Intent
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.ListView
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.adapter.ScheduledTasksManageSimpleAdapter
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.AlertDialogUtils
import cf.playhi.freezeyou.utils.TasksUtils
import cf.playhi.freezeyou.utils.ThemeUtils
import cf.playhi.freezeyou.utils.ThemeUtils.processActionBar
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import java.util.*

open class ScheduledTasksManageActivity : FreezeYouBaseActivity() {
    private val integerArrayList = ArrayList<Int>()
    private val selectedTasksPositions = ArrayList<Int>()
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.stma_main)
        processActionBar(supportActionBar)
        init()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            1 -> {
                if (findViewById<View>(R.id.stma_addTimeButton).visibility == View.VISIBLE) changeFloatButtonsStatus(
                    false
                )
                if (resultCode == RESULT_OK) updateTasksList()
            }
            2 -> {
                if (findViewById<View>(R.id.stma_addTriggerButton).visibility == View.VISIBLE) changeFloatButtonsStatus(
                    false
                )
                if (resultCode == RESULT_OK) updateTasksList()
            }
            else -> {}
        }
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun init() {
        if (Intent.ACTION_CREATE_SHORTCUT == intent.action) {
            val intent = Intent()
            intent.putExtra(
                Intent.EXTRA_SHORTCUT_INTENT,
                Intent(this, ScheduledTasksManageActivity::class.java)
            )
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.scheduledTasks))
            @Suppress("DEPRECATION")
            intent.putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher_new_round)
            )
            setResult(RESULT_OK, intent)
            finish()
        } else {
            generateTasksList()
            processAddButton()
        }
    }

    private fun generateTasksList() {
        val tasksListView = findViewById<ListView>(R.id.stma_tasksListview)
        val tasksData = ArrayList<MutableMap<String, Any?>>()
        generateTimeTaskList(integerArrayList, tasksData)
        generateTriggerTaskList(integerArrayList, tasksData)
        val adapter = ScheduledTasksManageSimpleAdapter(
            this, tasksData, integerArrayList,
            R.layout.stma_item, arrayOf("label", "time", "enabled"), intArrayOf(
                R.id.stma_label,
                R.id.stma_time,
                R.id.stma_switch
            )
        )
        tasksListView.setMultiChoiceModeListener(object : AbsListView.MultiChoiceModeListener {
            override fun onItemCheckedStateChanged(
                mode: ActionMode,
                position: Int,
                id: Long,
                checked: Boolean
            ) {
                if (checked) {
                    if (!selectedTasksPositions.contains(position)) {
                        selectedTasksPositions.add(position)
                    }
                } else {
                    selectedTasksPositions.remove(position)
                }
                mode.title = selectedTasksPositions.size.toString()
            }

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                this@ScheduledTasksManageActivity.menuInflater.inflate(
                    R.menu.stma_multichoicemenu,
                    menu
                )
                val cTheme = ThemeUtils.getUiTheme(this@ScheduledTasksManageActivity)
                if ("white" == cTheme || "default" == cTheme) menu.findItem(R.id.stma_menu_mc_delete)
                    .setIcon(R.drawable.ic_action_delete_light)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                return false
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.stma_menu_mc_delete -> {
                        AlertDialogUtils
                            .buildAlertDialog(
                                this@ScheduledTasksManageActivity,
                                R.drawable.ic_warning, R.string.askIfDel, R.string.notice
                            )
                            .setPositiveButton(R.string.yes) { _, _ ->
                                for (aSelectedTaskPosition in selectedTasksPositions) {
                                    val isTimeTask =
                                        ((tasksListView.adapter as ScheduledTasksManageSimpleAdapter)
                                            .getStoredArrayList()[aSelectedTaskPosition])["isTimeTask"] as Boolean
                                    val id = integerArrayList[aSelectedTaskPosition]
                                    val db = openOrCreateDatabase(
                                        if (isTimeTask) "scheduledTasks" else "scheduledTriggerTasks",
                                        MODE_PRIVATE,
                                        null
                                    )
                                    if (isTimeTask) {
                                        TasksUtils.cancelTheTask(
                                            this@ScheduledTasksManageActivity,
                                            id
                                        )
                                    }
                                    db.execSQL("DELETE FROM tasks WHERE _id = $id")
                                    db.close()
                                }
                                mode.finish()
                            }
                            .setNegativeButton(R.string.no, null)
                            .create().show()
                        true
                    }
                    R.id.stma_menu_mc_selectAll -> {
                        for (i in 0 until tasksListView.adapter.count) {
                            tasksListView.setItemChecked(i, true)
                        }
                        true
                    }
                    R.id.stma_menu_mc_selectUnselected -> {
                        for (i in 0 until tasksListView.adapter.count) {
                            tasksListView.setItemChecked(i, !tasksListView.isItemChecked(i))
                        }
                        true
                    }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                selectedTasksPositions.clear()
                updateTasksList()
            }
        })
        tasksListView.onItemClickListener =
            AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, i: Int, _: Long ->
                val map =
                    (tasksListView.adapter as ScheduledTasksManageSimpleAdapter)
                        .getStoredArrayList()[i]
                val label = map["label"] as String?
                val s = map["time"] as String?
                val isTimeTask = s != null && s.contains(":")
                @Suppress("DEPRECATION")
                startActivityForResult(
                    Intent(
                        this@ScheduledTasksManageActivity,
                        ScheduledTasksAddActivity::class.java
                    )
                        .putExtra("label", label)
                        .putExtra("time", isTimeTask)
                        .putExtra("id", integerArrayList[i]),
                    if (isTimeTask) 1 else 2
                )
            }
        tasksListView.adapter = adapter
    }

    private fun processAddButton() {
        val addButton = findViewById<ImageButton>(R.id.stma_addButton)
        val addTimeButton = findViewById<ImageButton>(R.id.stma_addTimeButton)
        val addTriggerButton = findViewById<ImageButton>(R.id.stma_addTriggerButton)
        addButton.setBackgroundResource(R.drawable.oval_ripple)
        addTriggerButton.setBackgroundResource(R.drawable.oval_ripple_almost_white)
        addTimeButton.setBackgroundResource(R.drawable.oval_ripple_almost_white)
        addButton.setOnClickListener {
            changeFloatButtonsStatus(addTimeButton.visibility != View.VISIBLE)
        }
        addTimeButton.setOnClickListener {
            changeFloatButtonsStatus(false)
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(this@ScheduledTasksManageActivity, ScheduledTasksAddActivity::class.java)
                    .putExtra("label", getString(R.string.add))
                    .putExtra("time", true),
                1
            )
        }
        addTriggerButton.setOnClickListener {
            changeFloatButtonsStatus(false)
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(this@ScheduledTasksManageActivity, ScheduledTasksAddActivity::class.java)
                    .putExtra("label", getString(R.string.add))
                    .putExtra("time", false),
                2
            )
        }
    }

    private fun changeFloatButtonsStatus(showSmallButton: Boolean) {
        val addButton = findViewById<ImageButton>(R.id.stma_addButton)
        val addTimeButton = findViewById<ImageButton>(R.id.stma_addTimeButton)
        val addTriggerButton = findViewById<ImageButton>(R.id.stma_addTriggerButton)
        if (showSmallButton) {
            val animation = RotateAnimation(
                0f,
                45f,
                Animation.RELATIVE_TO_SELF,
                0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f
            )
            animation.duration = 300
            animation.repeatMode = RotateAnimation.REVERSE
            animation.fillAfter = true
            addButton.startAnimation(animation)
            val alphaAnimation = AlphaAnimation(0.2f, 1.0f)
            alphaAnimation.duration = 150
            addTimeButton.startAnimation(alphaAnimation)
            addTriggerButton.startAnimation(alphaAnimation)
            addTimeButton.visibility = View.VISIBLE
            addTriggerButton.visibility = View.VISIBLE
        } else {
            val animation = RotateAnimation(
                45f,
                0f,
                Animation.RELATIVE_TO_SELF,
                0.5f,
                Animation.RELATIVE_TO_SELF,
                0.5f
            )
            animation.duration = 300
            animation.repeatMode = RotateAnimation.REVERSE
            animation.fillAfter = true
            addButton.startAnimation(animation)
            val alphaAnimation = AlphaAnimation(1.0f, 0.2f)
            alphaAnimation.duration = 150
            addTimeButton.startAnimation(alphaAnimation)
            addTriggerButton.startAnimation(alphaAnimation)
            addTimeButton.visibility = View.GONE
            addTriggerButton.visibility = View.GONE
        }
    }

    private fun generateTimeTaskList(
        integerArrayList: ArrayList<Int>,
        tasksData: ArrayList<MutableMap<String, Any?>>
    ) {
        //时间触发
        val db =
            this@ScheduledTasksManageActivity.openOrCreateDatabase("scheduledTasks", MODE_PRIVATE, null)
        db.execSQL(
            "create table if not exists tasks(_id integer primary key autoincrement,hour integer(2),minutes integer(2),repeat varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
        )
        val cursor = db.query("tasks", null, null, null, null, null, null)
        if (cursor.moveToFirst()) {
            for (i in 0 until cursor.count) {
                val label = cursor.getString(cursor.getColumnIndexOrThrow("label"))
                val hour = cursor.getInt(cursor.getColumnIndexOrThrow("hour")).toString()
                val minutes = cursor.getInt(cursor.getColumnIndexOrThrow("minutes")).toString()
                val time =
                    (if (hour.length == 1) "0$hour" else hour) + ":" + (if (minutes.length == 1) "0$minutes" else minutes)
                val enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled"))
                val keyValuePair: MutableMap<String, Any?> = HashMap()
                keyValuePair["label"] = label
                keyValuePair["time"] = time
                keyValuePair["isTimeTask"] = true
                keyValuePair["enabled"] = enabled == 1
                tasksData.add(keyValuePair)
                integerArrayList.add(cursor.getInt(cursor.getColumnIndexOrThrow("_id")))
                cursor.moveToNext()
            }
        }
        cursor.close()
        db.close()
    }

    private fun generateTriggerTaskList(
        integerArrayList: ArrayList<Int>,
        tasksData: ArrayList<MutableMap<String, Any?>>
    ) {
        //事件触发器触发
        val db = openOrCreateDatabase("scheduledTriggerTasks", MODE_PRIVATE, null)
        db.execSQL(
            "create table if not exists tasks(_id integer primary key autoincrement,tg varchar,tgextra varchar,enabled integer(1),label varchar,task varchar,column1 varchar,column2 varchar)"
        )
        val cursor = db.query("tasks", null, null, null, null, null, null)
        if (cursor.moveToFirst()) {
            for (i in 0 until cursor.count) {
                val tg = cursor.getString(cursor.getColumnIndexOrThrow("tg"))
                val enabled = cursor.getInt(cursor.getColumnIndexOrThrow("enabled"))
                val label = cursor.getString(cursor.getColumnIndexOrThrow("label"))
                val keyValuePair: MutableMap<String, Any?> = HashMap()
                keyValuePair["label"] = label
                val indexOf = listOf(*resources.getStringArray(R.array.triggersValues)).indexOf(tg)
                keyValuePair["time"] = listOf(*resources.getStringArray(R.array.triggers))
                    .get(if (indexOf == -1) 0 else indexOf)
                keyValuePair["isTimeTask"] = false
                keyValuePair["enabled"] = enabled == 1
                tasksData.add(keyValuePair)
                integerArrayList.add(cursor.getInt(cursor.getColumnIndexOrThrow("_id")))
                cursor.moveToNext()
            }
        }
        cursor.close()
        db.close()
    }

    private fun updateTasksList() {
        val tasksListView = findViewById<ListView>(R.id.stma_tasksListview)
        val adapter = tasksListView.adapter as ScheduledTasksManageSimpleAdapter?
        if (adapter != null) {
            val tasksData = ArrayList<MutableMap<String, Any?>>()
            integerArrayList.clear()
            generateTimeTaskList(integerArrayList, tasksData)
            generateTriggerTaskList(integerArrayList, tasksData)
            adapter.replaceAllInFormerArrayList(tasksData)
        }
    }
}
