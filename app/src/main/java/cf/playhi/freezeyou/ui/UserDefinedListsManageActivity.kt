package cf.playhi.freezeyou.ui

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SimpleAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouAlertDialogBuilder
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.GZipUtils
import cf.playhi.freezeyou.utils.ClipboardUtils.copyToClipboard
import cf.playhi.freezeyou.utils.ThemeUtils.processActionBar
import cf.playhi.freezeyou.utils.ThemeUtils.processSetTheme
import cf.playhi.freezeyou.utils.ToastUtils.showToast
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

class UserDefinedListsManageActivity : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        processSetTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.udlma_main)
        processActionBar(supportActionBar)
        loadUserDefinedLists()
    }

    private fun loadUserDefinedLists() {
        val listsDataArrayList = ArrayList<MutableMap<String, Any?>>()
        val userDefinedListsDb =
            openOrCreateDatabase("userDefinedCategories", MODE_PRIVATE, null)
        userDefinedListsDb.execSQL(
            "create table if not exists categories(_id integer primary key autoincrement,label varchar,packages varchar)"
        )
        val cursor = userDefinedListsDb.query(
            "categories",
            arrayOf("label", "_id", "packages"),
            null,
            null,
            null,
            null,
            null
        )
        if (cursor.moveToFirst()) {
            for (i in 0 until cursor.count) {
                val hm: MutableMap<String, Any?> = HashMap()
                val id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"))
                val title = String(
                    Base64.decode(
                        cursor.getString(
                            cursor.getColumnIndexOrThrow("label")
                        ), Base64.DEFAULT
                    )
                )
                val packages = cursor.getString(cursor.getColumnIndexOrThrow("packages"))
                hm["id"] = id
                hm["title"] = title
                hm["packages"] = packages
                listsDataArrayList.add(hm)
                cursor.moveToNext()
            }
        }
        cursor.close()
        userDefinedListsDb.close()
        displayUserDefinedLists(listsDataArrayList)
    }

    private fun displayUserDefinedLists(listsDataArrayList: ArrayList<MutableMap<String, Any?>>) {
        val progressBar = findViewById<ProgressBar>(R.id.udlmam_progressBar)
        val listView = findViewById<ListView>(R.id.udlmam_listView)
        val simpleAdapter = SimpleAdapter(
            this,
            listsDataArrayList,
            R.layout.udlma_list_item,
            arrayOf("title", "packages"),
            intArrayOf(R.id.udlmali_title_textView, R.id.udlmali_subTitle_textView)
        )
        listView.adapter = simpleAdapter
        listView.onItemClickListener =
            AdapterView.OnItemClickListener { _: AdapterView<*>?, view: View?, position: Int, _: Long ->
                val itemDataHashMap = simpleAdapter.getItem(position)
                if (itemDataHashMap is MutableMap<*, *>) {
                    val title = itemDataHashMap["title"] as String?
                    if (title != null) {
                        showListViewOnItemClickPopupMenu(
                            view!!,
                            title,
                            itemDataHashMap as MutableMap<String, Any?>
                        )
                    }
                }
            }
        progressBar.visibility = View.GONE
    }

    private fun showListViewOnItemClickPopupMenu(
        view: View,
        title: String,
        itemDataHashMap: MutableMap<String, Any?>
    ) {
        val popup = PopupMenu(this@UserDefinedListsManageActivity, view)
        popup.inflate(R.menu.udlmna_single_choose_action)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.udlmna_sca_menu_copyId -> if (copyToClipboard(
                        applicationContext,
                        Base64.encodeToString(title.toByteArray(), Base64.DEFAULT)
                    )
                ) {
                    showToast(applicationContext, R.string.success)
                } else {
                    showToast(applicationContext, R.string.failed)
                }
                R.id.udlmna_sca_menu_share -> try {
                    val finalOutputJsonObject = JSONObject()
                    val userDefinedCategoriesJSONArray = JSONArray()
                    val oneUserDefinedCategoriesJSONObject = JSONObject()
                    oneUserDefinedCategoriesJSONObject.put(
                        "label",
                        Base64.encodeToString(title.toByteArray(), Base64.DEFAULT)
                    )
                    oneUserDefinedCategoriesJSONObject.put(
                        "packages",
                        itemDataHashMap["packages"]
                    )
                    userDefinedCategoriesJSONArray.put(oneUserDefinedCategoriesJSONObject)
                    finalOutputJsonObject.put(
                        "userDefinedCategories",
                        userDefinedCategoriesJSONArray
                    )
                    var shareIntent = Intent()
                    shareIntent.action = Intent.ACTION_SEND
                    shareIntent.type = "text/plain"
                    shareIntent.putExtra(
                        Intent.EXTRA_TEXT,
                        GZipUtils.gzipCompress(finalOutputJsonObject.toString())
                    )
                    shareIntent = Intent.createChooser(shareIntent, getString(R.string.share))
                    startActivity(shareIntent)
                } catch (e: JSONException) {
                    e.printStackTrace()
                    showToast(this@UserDefinedListsManageActivity, R.string.failed)
                }
                R.id.udlmna_sca_menu_delete -> {
                    val builder =
                        FreezeYouAlertDialogBuilder(this@UserDefinedListsManageActivity)
                    builder.setTitle(R.string.plsConfirm)
                    builder.setMessage(R.string.askIfDel)
                    builder.setPositiveButton(
                        R.string.yes
                    ) { _, _ -> deleteUserDefinedListById(itemDataHashMap["id"] as Int) }
                    builder.setNegativeButton(R.string.no, null)
                    builder.show()
                }
                else -> {}
            }
            true
        }
        popup.show()
    }

    private fun deleteUserDefinedListById(id: Int) {
        val userDefinedListsDb =
            openOrCreateDatabase("userDefinedCategories", MODE_PRIVATE, null)
        userDefinedListsDb.execSQL(
            "create table if not exists categories(_id integer primary key autoincrement,label varchar,packages varchar)"
        )
        userDefinedListsDb.delete("categories", "_id = $id", null)
        userDefinedListsDb.close()
        loadUserDefinedLists()
    }
}
