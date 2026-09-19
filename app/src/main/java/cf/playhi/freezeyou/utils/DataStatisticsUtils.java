package cf.playhi.freezeyou.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import java.util.Collections;
import java.util.List;

public final class DataStatisticsUtils {

    public static void addFreezeTimes(Context context, String pkgNameString) {
        addFreezeTimes(context, Collections.singletonList(pkgNameString));
    }

    public static void addUFreezeTimes(Context context, String pkgNameString) {
        addUFreezeTimes(context, Collections.singletonList(pkgNameString));
    }

    /**
     * Opening the database is the expensive part, so a batch pays for it once rather than once
     * per application.
     */
    public static void addFreezeTimes(Context context, List<String> pkgNameStrings) {
        addTimes(context, "ApplicationsFreezeTimes", pkgNameStrings);
    }

    public static void addUFreezeTimes(Context context, List<String> pkgNameStrings) {
        addTimes(context, "ApplicationsUFreezeTimes", pkgNameStrings);
    }

    private static void addTimes(Context context, String dbName, List<String> pkgNameStrings) {
        if (pkgNameStrings.isEmpty()) {
            return;
        }
        SQLiteDatabase db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null);
        try {
            for (String pkgNameString : pkgNameStrings) {
                addTimes(db, pkgNameString);
            }
        } finally {
            db.close();
        }
    }

    public static void addUseTimes(Context context, String pkgNameString) {
        SQLiteDatabase db = context.openOrCreateDatabase("ApplicationsUseTimes", Context.MODE_PRIVATE, null);
        addTimes(db, pkgNameString);
        db.close();
    }

    private static void addTimes(SQLiteDatabase db, String pkgNameString) {

        if (db == null) {
            return;
        }

        db.execSQL(
                "create table if not exists TimesList(_id integer primary key autoincrement,pkg varchar,times int)"
        );
        Cursor cursor =
                db.query("TimesList", new String[]{"pkg", "times"}, "pkg = '"
                        + Base64.encodeToString(pkgNameString.getBytes(), Base64.DEFAULT)
                        + "'", null, null, null, null);

        if (cursor == null) {
            return;
        }

        if (cursor.moveToFirst()) {
            db.execSQL("UPDATE TimesList SET times = '"
                    + (Integer.parseInt(cursor.getString(cursor.getColumnIndex("times"))) + 1)
                    + "' WHERE pkg = '" + Base64.encodeToString(pkgNameString.getBytes(), Base64.DEFAULT) + "';");
        } else {
            db.execSQL("insert into TimesList(pkg,times) values('"
                    + Base64.encodeToString(pkgNameString.getBytes(), Base64.DEFAULT)
                    + "','0');");
        }
        cursor.close();

    }

    public static void resetTimes(Context context, String dbName) {
        SQLiteDatabase db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null);

        if (db == null) {
            return;
        }

        db.execSQL(
                "create table if not exists TimesList(_id integer primary key autoincrement,pkg varchar,times int)"
        );

        db.execSQL("UPDATE TimesList SET times = '0';");

        db.close();
    }
}
