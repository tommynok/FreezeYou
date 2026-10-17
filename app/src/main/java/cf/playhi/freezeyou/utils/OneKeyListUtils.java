package cf.playhi.freezeyou.utils;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import net.grandcentrix.tray.AppPreferences;

import java.util.ArrayList;

public final class OneKeyListUtils {

    public static boolean addToOneKeyList(Context context, String key, String pkgName) {
        final AppPreferences sharedPreferences = new AppPreferences(context);
        final String pkgNames = sharedPreferences.getString(key, "");
        return existsInOneKeyList(pkgNames, pkgName)
                || sharedPreferences.put(key, addPkgToPkgNames(pkgNames, pkgName));
    }

    public static boolean removeFromOneKeyList(Context context, String key, String pkgName) {
        final AppPreferences sharedPreferences = new AppPreferences(context);
        final String pkgNames = sharedPreferences.getString(key, "");
        return !existsInOneKeyList(pkgNames, pkgName)
                || sharedPreferences.put(key, removePkgFromPkgNames(pkgNames, pkgName));
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
        return existsInOneKeyList(new AppPreferences(context).getString(onekeyName, ""), pkgName);
    }

    public static boolean removeUninstalledFromOneKeyList(Context context, String oneKeyName) {
        String s = new AppPreferences(context).getString(oneKeyName, "");

        if (s == null) {
            return false;
        }

        // Parsed rather than split on the comma: a padded entry would be looked up under a name
        // the package manager does not know, and then removed under a name the list does not hold.
        for (String pkgName : PackageListUtils.parse(s)) {
            if (ApplicationInfoUtils
                    .getApplicationInfoFromPkgName(pkgName, context) == null) {
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
        SQLiteDatabase userDefinedDb = context.openOrCreateDatabase("userDefinedCategories", Context.MODE_PRIVATE, null);
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

                    userDefinedDb.execSQL(
                            "create table if not exists categories(_id integer primary key autoincrement,label varchar,packages varchar)"
                    );
                    Cursor cursor =
                            userDefinedDb.query(
                                    "categories",
                                    new String[]{"packages"},
                                    "label = '" + labelBase64 + "'",
                                    null, null,
                                    null, null
                            );

                    if (cursor.moveToFirst()) {
                        result.append(cursor.getString(cursor.getColumnIndex("packages")));
                    }
                    cursor.close();
                    userDefinedDb.close();
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
}
