package cf.playhi.freezeyou.utils

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Base64

object DataStatisticsUtils {
    @JvmStatic
    fun addFreezeTimes(context: Context, pkgNameString: String) {
        val db = context.openOrCreateDatabase("ApplicationsFreezeTimes", Context.MODE_PRIVATE, null)
        addTimes(db, pkgNameString)
        db.close()
    }

    @JvmStatic
    fun addUFreezeTimes(context: Context, pkgNameString: String) {
        val db = context.openOrCreateDatabase("ApplicationsUFreezeTimes", Context.MODE_PRIVATE, null)
        addTimes(db, pkgNameString)
        db.close()
    }

    @JvmStatic
    fun addUseTimes(context: Context, pkgNameString: String) {
        val db = context.openOrCreateDatabase("ApplicationsUseTimes", Context.MODE_PRIVATE, null)
        addTimes(db, pkgNameString)
        db.close()
    }

    private fun addTimes(db: SQLiteDatabase?, pkgNameString: String) {
        if (db == null) {
            return
        }
        db.execSQL(
            "create table if not exists TimesList(_id integer primary key autoincrement,pkg varchar,times int)"
        )
        val cursor = db.query(
            "TimesList", arrayOf("pkg", "times"), "pkg = '"
                    + Base64.encodeToString(pkgNameString.toByteArray(), Base64.DEFAULT)
                    + "'", null, null, null, null
        ) ?: return
        if (cursor.moveToFirst()) {
            db.execSQL(
                "UPDATE TimesList SET times = '"
                        + (cursor.getString(cursor.getColumnIndexOrThrow("times")).toInt() + 1)
                        + "' WHERE pkg = '" + Base64.encodeToString(
                    pkgNameString.toByteArray(),
                    Base64.DEFAULT
                ) + "';"
            )
        } else {
            db.execSQL(
                "insert into TimesList(pkg,times) values('"
                        + Base64.encodeToString(pkgNameString.toByteArray(), Base64.DEFAULT)
                        + "','0');"
            )
        }
        cursor.close()
    }

    @JvmStatic
    fun resetTimes(context: Context, dbName: String?) {
        val db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null) ?: return
        db.execSQL(
            "create table if not exists TimesList(_id integer primary key autoincrement,pkg varchar,times int)"
        )
        db.execSQL("UPDATE TimesList SET times = '0';")
        db.close()
    }
}
