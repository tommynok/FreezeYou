package cf.playhi.freezeyou.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Base64;

import java.util.ArrayList;
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
        SQLiteDatabase db = null;
        try {
            db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null);
            if (db == null) {
                return;
            }
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
        } catch (SQLiteException e) {
            e.printStackTrace();
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }

    public static void addUseTimes(Context context, String pkgNameString) {
        SQLiteDatabase db = null;
        try {
            db = context.openOrCreateDatabase("ApplicationsUseTimes", Context.MODE_PRIVATE, null);
            if (db == null) {
                return;
            }
            createTableIfNeeded(db);
            addTimes(db, pkgNameString);
        } catch (SQLiteException e) {
            e.printStackTrace();
        } finally {
            if (db != null) {
                db.close();
            }
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

        // Once, and bound into every statement below: the name used to be re-encoded and pasted
        // between quotes into the SQL each time it was needed.
        String encodedPkg = Base64.encodeToString(pkgNameString.getBytes(), Base64.DEFAULT);
        String[] pkgSelectionArgs = new String[]{encodedPkg};

        Cursor cursor =
                db.query("TimesList", new String[]{"pkg", "times"},
                        "pkg = ?", pkgSelectionArgs, null, null, null);

        if (cursor == null) {
            return;
        }

        try {
            if (cursor.moveToFirst()) {
                ContentValues values = new ContentValues();
                values.put("times",
                        Integer.parseInt(cursor.getString(cursor.getColumnIndexOrThrow("times"))) + 1);
                db.update("TimesList", values, "pkg = ?", pkgSelectionArgs);
            } else {
                // The row is created because this application was just acted on, so the count starts
                // at one. It used to start at zero, which meant every application's first freeze went
                // uncounted and the statistics were short by one for ever after.
                ContentValues values = new ContentValues();
                values.put("pkg", encodedPkg);
                values.put("times", 1);
                db.insert("TimesList", null, values);
            }
        } finally {
            cursor.close();
        }

    }

    public static void resetTimes(Context context, String dbName) {
        SQLiteDatabase db = null;
        try {
            db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null);
            if (db == null) {
                return;
            }
            createTableIfNeeded(db);
            db.execSQL("UPDATE TimesList SET times = '0';");
        } catch (SQLiteException e) {
            e.printStackTrace();
        } finally {
            if (db != null) {
                db.close();
            }
        }
    }

    /**
     * Delete statistics rows for an uninstalled package from all three statistics databases.
     *
     * A counter row was written when an app was frozen or opened and never taken back out, so the
     * device kept counting applications it no longer had.
     */
    public static void deletePackageTimes(Context context, String pkgName) {

        if (pkgName == null || pkgName.trim().isEmpty()) {
            return;
        }

        String encodedPkg = Base64.encodeToString(pkgName.getBytes(), Base64.DEFAULT);
        String[] dbNames = new String[]{
                "ApplicationsFreezeTimes", "ApplicationsUFreezeTimes", "ApplicationsUseTimes"
        };
        for (String dbName : dbNames) {
            SQLiteDatabase db = null;
            try {
                db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null);
                if (db != null) {
                    createTableIfNeeded(db);
                    db.delete("TimesList", "pkg = ?", new String[]{encodedPkg});
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (db != null) {
                    db.close();
                }
            }
        }
    }

    /**
     * Purges statistics rows for any packages that are no longer installed on the device.
     *
     * Deleting an application now takes its own counters out, but rows counted before that was so
     * stay behind, so this sweeps what the per-package delete could not reach.
     */
    public static boolean purgeUninstalledPackageTimes(Context context) {
        String[] dbNames = new String[]{
                "ApplicationsFreezeTimes", "ApplicationsUFreezeTimes", "ApplicationsUseTimes"
        };
        boolean allSuccess = true;
        for (String dbName : dbNames) {
            SQLiteDatabase db = null;
            Cursor cursor = null;
            try {
                db = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null);
                if (db != null) {
                    createTableIfNeeded(db);
                    cursor = db.query("TimesList", new String[]{"pkg"}, null, null, null, null, null);
                    List<String> pkgsToRemove = new ArrayList<>();
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            String encodedPkg = cursor.getString(cursor.getColumnIndexOrThrow("pkg"));
                            try {
                                String pkgName = new String(Base64.decode(encodedPkg, Base64.DEFAULT));
                                if (!ApplicationInfoUtils.isAppInstalled(context, pkgName)) {
                                    pkgsToRemove.add(encodedPkg);
                                }
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    for (String encoded : pkgsToRemove) {
                        db.delete("TimesList", "pkg = ?", new String[]{encoded});
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                allSuccess = false;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
                if (db != null) {
                    db.close();
                }
            }
        }
        return allSuccess;
    }
}
