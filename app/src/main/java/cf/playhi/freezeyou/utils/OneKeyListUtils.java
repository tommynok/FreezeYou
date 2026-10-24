package cf.playhi.freezeyou.utils;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Base64;

import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public final class OneKeyListUtils {

    private static final Object LIST_LOCK = new Object();
    private static final String USER_DEFINED_DB = "userDefinedCategories";
    private static final String CATEGORIES_TABLE = "categories";
    private static final String CREATE_CATEGORIES_TABLE =
            "create table if not exists categories(_id integer primary key autoincrement,label varchar,packages varchar)";

    public static boolean addToOneKeyList(Context context, String key, String pkgName) {
        if (pkgName == null || pkgName.trim().isEmpty()) {
            return true;
        }
        return addToOneKeyList(context, key, Collections.singletonList(pkgName));
    }

    public static boolean addToOneKeyList(Context context, String key, Collection<String> pkgNamesToAdd) {
        if (pkgNamesToAdd == null || pkgNamesToAdd.isEmpty()) {
            return true;
        }
        synchronized (LIST_LOCK) {
            final DefaultMultiProcessMMKVStorage storage = new DefaultMultiProcessMMKVStorage();
            storage.lock();
            try {
                final String currentPkgNames = storage.getString(key, "");
                String updatedPkgNames = currentPkgNames;
                boolean changed = false;
                for (String pkgName : pkgNamesToAdd) {
                    if (pkgName != null && !pkgName.trim().isEmpty() && !existsInOneKeyList(updatedPkgNames, pkgName)) {
                        updatedPkgNames = addPkgToPkgNames(updatedPkgNames, pkgName);
                        changed = true;
                    }
                }
                boolean success = true;
                if (changed) {
                    success = storage.encode(key, updatedPkgNames);
                    storage.sync();
                }
                return success;
            } finally {
                storage.unlock();
            }
        }
    }

    public static boolean removeFromOneKeyList(Context context, String key, String pkgName) {
        if (pkgName == null || pkgName.trim().isEmpty()) {
            return true;
        }
        return removeFromOneKeyList(context, key, Collections.singletonList(pkgName));
    }

    public static boolean removeFromOneKeyList(Context context, String key, Collection<String> pkgNamesToRemove) {
        if (pkgNamesToRemove == null || pkgNamesToRemove.isEmpty()) {
            return true;
        }
        synchronized (LIST_LOCK) {
            final DefaultMultiProcessMMKVStorage storage = new DefaultMultiProcessMMKVStorage();
            storage.lock();
            try {
                final String currentPkgNames = storage.getString(key, "");
                String updatedPkgNames = currentPkgNames;
                boolean changed = false;
                for (String pkgName : pkgNamesToRemove) {
                    if (pkgName != null && existsInOneKeyList(updatedPkgNames, pkgName)) {
                        updatedPkgNames = removePkgFromPkgNames(updatedPkgNames, pkgName);
                        changed = true;
                    }
                }
                boolean success = true;
                if (changed) {
                    success = storage.encode(key, updatedPkgNames);
                    storage.sync();
                }
                return success;
            } finally {
                storage.unlock();
            }
        }
    }

    /**
     * Add one package to a stored OneKEY list and return the list rebuilt.
     *
     * The list used to grow by plain concatenation, {@code pkgNames + pkgName + ","}, which only
     * held together while every stored list ended in a comma. A list edited by hand into
     * "com.a" turned adding "com.b" into "com.acom.b," - one package name that belongs to neither
     * app. Parsing first also makes the add idempotent: an app already on the list stays on it
     * once, in the place it was.
     *
     * @return every name on the list, each followed by a comma, with {@code pkgName} appended last
     *         unless it is already there or blank.
     */
    public static String addPkgToPkgNames(String pkgNames, String pkgName) {
        String name = pkgName == null ? "" : pkgName.trim();
        ArrayList<String> result = new ArrayList<>();
        for (String candidate : PackageListUtils.parse(pkgNames)) {
            result.add(candidate);
        }
        if (!name.isEmpty() && !result.contains(name)) {
            result.add(name);
        }
        return joinWithCommas(result);
    }

    /**
     * Drop one package from a stored OneKEY list and return the list rebuilt.
     *
     * This used to be {@code pkgNames.replace(pkgName + ",", "")}, which quietly kept the last
     * entry of a list stored without a trailing comma and, worse, matched a substring: removing
     * "com.a" out of "xcom.a," left "x" behind as a package name. Splitting the list, comparing
     * whole entries and joining again is what makes both shapes safe.
     *
     * @return the remaining names, each followed by a comma - the shape addToOneKeyList stores.
     */
    public static String removePkgFromPkgNames(String pkgNames, String pkgName) {
        ArrayList<String> result = new ArrayList<>();
        for (String candidate : PackageListUtils.parse(pkgNames)) {
            if (!candidate.equals(pkgName)) {
                result.add(candidate);
            }
        }
        return joinWithCommas(result);
    }

    private static String joinWithCommas(Iterable<String> names) {
        StringBuilder result = new StringBuilder();
        for (String name : names) {
            result.append(name).append(",");
        }
        return result.toString();
    }

    public static boolean existsInOneKeyList(String pkgNames, String pkgName) {
        for (String candidate : PackageListUtils.parse(pkgNames)) {
            if (candidate.equals(pkgName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean existsInOneKeyList(Context context, String onekeyName, String pkgName) {
        return existsInOneKeyList(
                new DefaultMultiProcessMMKVStorage().getString(onekeyName, ""), pkgName);
    }

    public static boolean removeUninstalledFromOneKeyList(Context context, String oneKeyName) {
        String s = new DefaultMultiProcessMMKVStorage().getString(oneKeyName, "");

        if (s == null) {
            return false;
        }

        // Parsed rather than split on the comma: a padded entry would be looked up under a name
        // the package manager does not know, and then removed under a name the list does not hold.
        for (String pkgName : PackageListUtils.parse(s)) {
            if (!ApplicationInfoUtils.isAppInstalled(context, pkgName)) {
                OneKeyListUtils.removeFromOneKeyList(
                        context,
                        oneKeyName,
                        pkgName
                );
            }
        }
        return true;
    }

    public static String[] decodeUserListsInPackageNames(Context context, String[] pkgs) {
        StringBuilder result = new StringBuilder();
        // One connection for the whole list, closed after the loop. It used to be closed inside
        // the loop, so a second "@Category" in the same command ran against a pool that was
        // already shut down and the task threw "Cannot perform this operation because the
        // connection pool has been closed" instead of freezing the apps of that category.
        SQLiteDatabase userDefinedDb = null;
        try {
            userDefinedDb = context.openOrCreateDatabase(USER_DEFINED_DB, Context.MODE_PRIVATE, null);
            for (String pkg : pkgs) {
                if ("".equals(pkg.trim())) {
                    continue;
                }
                if (pkg.startsWith("@")) {
                    try {
                        String labelBase64 =
                                Base64.encodeToString(
                                        Base64.decode(pkg.substring(1), Base64.DEFAULT),
                                        Base64.DEFAULT
                                );

                        Cursor cursor = null;
                        try {
                            cursor =
                                    userDefinedDb.query(
                                            CATEGORIES_TABLE,
                                            new String[]{"packages"},
                                            "label = ?",
                                            new String[]{labelBase64},
                                            null,
                                            null, null
                                    );

                            if (cursor != null && cursor.moveToFirst()) {
                                String packages =
                                        cursor.getString(cursor.getColumnIndexOrThrow("packages"));
                                // Null, not "null": a category with nothing written to the column
                                // yet names no package, and appending the word would hand the
                                // freeze command a package called "null".
                                if (packages != null) {
                                    result.append(packages);
                                }
                            }
                        } finally {
                            if (cursor != null) {
                                cursor.close();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    result.append(pkg);
                }
                if (result.length() != 0 && result.charAt(result.length() - 1) != ',') {
                    result.append(",");
                }
            }
        } catch (SQLiteException e) {
            e.printStackTrace();
        } finally {
            if (userDefinedDb != null) {
                userDefinedDb.close();
            }
        }
        // "".split(",") is a one-element array holding an empty string, not an empty one, so an
        // empty list used to arrive downstream as a single blank package name — reported to the
        // user as "package name is empty" for a command that simply named nothing.
        if (result.length() == 0) {
            return new String[]{};
        }
        String[] decoded = result.toString().split(",");
        ArrayList<String> nonBlank = new ArrayList<>(decoded.length);
        for (String pkg : decoded) {
            if (!pkg.trim().isEmpty()) {
                nonBlank.add(pkg.trim());
            }
        }
        return nonBlank.toArray(new String[0]);
    }

    /**
     * Take one app out of every custom category, what removeFromOneKeyList is for the stored lists.
     *
     * A category keeps its members as package names of its own, so an uninstall left the name
     * behind: the category still listed an app the device no longer has, and every command built
     * from it asked the package manager for a package that cannot be found.
     */
    public static void removePackageFromUserDefinedCategories(Context context, String pkgName) {
        String name = pkgName == null ? "" : pkgName.trim();
        if (name.isEmpty()) {
            return;
        }
        SQLiteDatabase userDefinedDb = null;
        try {
            userDefinedDb = context.openOrCreateDatabase(USER_DEFINED_DB, Context.MODE_PRIVATE, null);
            userDefinedDb.execSQL(CREATE_CATEGORIES_TABLE);
            for (CategoryRow row : readUserDefinedCategories(userDefinedDb)) {
                if (existsInOneKeyList(row.packages, name)) {
                    writeUserDefinedCategory(
                            userDefinedDb,
                            row.id,
                            removePkgFromPkgNames(row.packages, name)
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (userDefinedDb != null) {
                userDefinedDb.close();
            }
        }
    }

    /**
     * Drop from every custom category the apps that are no longer installed - the category twin of
     * removeUninstalledFromOneKeyList, which only reaches one stored list.
     *
     * @return whether the pass ran to the end. A database that could not be opened is a failure
     *         reported to the caller rather than an exception thrown at the settings row.
     */
    public static boolean removeUninstalledFromUserDefinedCategories(Context context) {
        SQLiteDatabase userDefinedDb = null;
        try {
            userDefinedDb = context.openOrCreateDatabase(USER_DEFINED_DB, Context.MODE_PRIVATE, null);
            userDefinedDb.execSQL(CREATE_CATEGORIES_TABLE);
            for (CategoryRow row : readUserDefinedCategories(userDefinedDb)) {
                String packages = row.packages;
                // Parsed once, up front: the rebuilt string is shorter than the names it was made
                // from, so walking it while removing would skip entries.
                for (String pkgName : PackageListUtils.parse(packages)) {
                    if (!ApplicationInfoUtils.isAppInstalled(context, pkgName)) {
                        packages = removePkgFromPkgNames(packages, pkgName);
                    }
                }
                if (!packages.equals(row.packages)) {
                    writeUserDefinedCategory(userDefinedDb, row.id, packages);
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            if (userDefinedDb != null) {
                userDefinedDb.close();
            }
        }
    }

    private static ArrayList<CategoryRow> readUserDefinedCategories(SQLiteDatabase userDefinedDb) {
        ArrayList<CategoryRow> rows = new ArrayList<>();
        Cursor cursor = null;
        try {
            cursor = userDefinedDb.query(
                    CATEGORIES_TABLE, new String[]{"_id", "packages"},
                    null, null, null, null, null
            );
            while (cursor.moveToNext()) {
                rows.add(new CategoryRow(
                        cursor.getInt(cursor.getColumnIndexOrThrow("_id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("packages"))
                ));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return rows;
    }

    private static void writeUserDefinedCategory(SQLiteDatabase userDefinedDb, int id, String packages) {
        ContentValues values = new ContentValues();
        values.put("packages", packages);
        userDefinedDb.update(CATEGORIES_TABLE, values, "_id = ?", new String[]{String.valueOf(id)});
    }

    /**
     * A row read apart from the write that follows it. Android fills a cursor's window as it walks
     * the table, so updating "categories" while its rows are still being read is a thing that only
     * misbehaves on some devices.
     */
    private static final class CategoryRow {
        final int id;
        final String packages;

        CategoryRow(int id, String packages) {
            this.id = id;
            this.packages = packages == null ? "" : packages;
        }
    }
}
