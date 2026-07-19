package cf.playhi.freezeyou

import android.content.Context
import android.content.Intent
import android.os.Bundle
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.service.ForceStopService
import cf.playhi.freezeyou.utils.ServiceUtils
import cf.playhi.freezeyou.utils.ToastUtils.showToast

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
class ForceStop : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkgName = intent.getStringExtra("pkgName")
        if (pkgName.isNullOrEmpty()) {
            finish()
            return
        }
        var packages: Array<String>? = null
        if (pkgName.startsWith("FORCESTOPCATEGORY")) {
            val categoryLabel = pkgName.substring("FORCESTOPCATEGORY".length)
            val userDefinedDb = openOrCreateDatabase("userDefinedCategories", Context.MODE_PRIVATE, null)
            userDefinedDb.execSQL(
                "create table if not exists categories(_id integer primary key autoincrement,label varchar,packages varchar)"
            )
            val cursor = userDefinedDb.query(
                "categories",
                arrayOf("packages"),
                "label = '$categoryLabel'",
                null, null, null, null
            )
            if (cursor.moveToFirst()) {
                packages = cursor.getString(cursor.getColumnIndexOrThrow("packages")).split(",").toTypedArray()
            } else {
                showToast(this, R.string.failed)
            }
            cursor.close()
            userDefinedDb.close()
        } else {
            packages = arrayOf(pkgName)
        }
        ServiceUtils.startService(
            this,
            Intent(applicationContext, ForceStopService::class.java)
                .putExtra("packages", packages)
        )
        finish()
    }
}
