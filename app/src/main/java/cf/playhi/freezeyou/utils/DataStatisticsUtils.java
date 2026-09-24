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

    /**
     * One transaction for the whole batch, and the table statement once rather than per row.
     *
     * Outside a transaction every statement commits on its own, and a commit means flushing the
     * journal to storage. A batch of 25 applications therefore paid for 25 of those, which
     * measured at roughly a second on a real device — eight times as long as the freezing itself,
     * which took 121 ms for the same 25. Inside one transaction there is a single flush at the end.
     */
    private static void addTimes(Context context, String dbName, List<String> pkgNameStrings) {
        if (pkgNameStrings.isEmpty()) {
            return;
        }
        SQLiteDatabase db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null);
        if (db == null) {
            return;
        }
        try {
            createTableIfNeeded(db);
            db.beginTransaction();
            try {
                for (String pkgNameString : pkgNameStrings) {
                    addTimes(db, pkgNameString);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } finally {
            db.close();
        }
    }

    public static void addUseTimes(Context context, String pkgNameString) {
        SQLiteDatabase db = context.openOrCreateDatabase("ApplicationsUseTimes", Context.MODE_PRIVATE, null);
        if (db == null) {
            return;
        }
        try {
            createTableIfNeeded(db);
            addTimes(db, pkgNameString);
        } finally {
            db.close();
        }
    }

    private static void createTableIfNeeded(SQLiteDatabase db) {
        db.execSQL(
                "create table if not exists TimesList(_id integer primary key autoincrement,pkg varchar,times int)"
        );
    }

    /**
     * Expects the table to exist and, for a batch, to be inside a transaction — see the caller.
     */
    private static void addTimes(SQLiteDatabase db, String pkgNameString) {

        if (db == null) {
            return;
        }

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
            // The row is created because this application was just acted on, so the count starts
            // at one. It used to start at zero, which meant every application's first freeze went
            // uncounted and the statistics were short by one for ever after.
            db.execSQL("insert into TimesList(pkg,times) values('"
                    + Base64.encodeToString(pkgNameString.getBytes(), Base64.DEFAULT)
                    + "','1');");
        }
        cursor.close();

    }

    public static void resetTimes(Context context, String dbName) {
        SQLiteDatabase db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null);

        if (db == null) {
            return;
        }

        try {
            createTableIfNeeded(db);
            db.execSQL("UPDATE TimesList SET times = '0';");
        } finally {
            db.close();
        }
    }
}
