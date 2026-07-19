package cf.playhi.freezeyou.utils

import android.content.Context
import android.util.Base64
import net.grandcentrix.tray.AppPreferences

object OneKeyListUtils {
    @JvmStatic
    fun addToOneKeyList(context: Context, key: String, pkgName: String): Boolean {
        val sharedPreferences = AppPreferences(context)
        val pkgNames = sharedPreferences.getString(key, "")
        return existsInOneKeyList(pkgNames, pkgName) || sharedPreferences.put(
            key,
            pkgNames + pkgName + ","
        )
    }

    @JvmStatic
    fun removeFromOneKeyList(context: Context, key: String, pkgName: String): Boolean {
        val sharedPreferences = AppPreferences(context)
        val pkgNames = sharedPreferences.getString(key, "")
        return !existsInOneKeyList(pkgNames, pkgName) || sharedPreferences.put(
            key,
            pkgNames!!.replace("$pkgName,", "")
        )
    }

    @JvmStatic
    fun existsInOneKeyList(pkgNames: String?, pkgName: String?): Boolean {
        return pkgNames != null && listOf(*pkgNames.split(",").toTypedArray()).contains(pkgName)
    }

    @JvmStatic
    fun existsInOneKeyList(context: Context, onekeyName: String, pkgName: String?): Boolean {
        val pkgNames = AppPreferences(context).getString(onekeyName, "")
        return pkgNames != null && listOf(*pkgNames.split(",").toTypedArray()).contains(pkgName)
    }

    @JvmStatic
    fun removeUninstalledFromOneKeyList(context: Context, oneKeyName: String): Boolean {
        val s = AppPreferences(context).getString(oneKeyName, "") ?: return false
        val strings = s.split(",")
        for (pkgName in strings) {
            if (pkgName != "" &&
                ApplicationInfoUtils
                    .getApplicationInfoFromPkgName(pkgName, context) == null
            ) {
                removeFromOneKeyList(
                    context,
                    oneKeyName,
                    pkgName
                )
            }
        }
        return true
    }

    @JvmStatic
    fun decodeUserListsInPackageNames(context: Context, pkgs: Array<String>): Array<String> {
        val result = StringBuilder()
        val userDefinedDb =
            context.openOrCreateDatabase("userDefinedCategories", Context.MODE_PRIVATE, null)
        for (pkg in pkgs) {
            if ("" == pkg.trim { it <= ' ' }) {
                continue
            }
            if (pkg.startsWith("@")) {
                try {
                    val labelBase64 = Base64.encodeToString(
                        Base64.decode(pkg.substring(1), Base64.DEFAULT),
                        Base64.DEFAULT
                    )
                    userDefinedDb.execSQL(
                        "create table if not exists categories(_id integer primary key autoincrement,label varchar,packages varchar)"
                    )
                    val cursor = userDefinedDb.query(
                        "categories",
                        arrayOf("packages"),
                        "label = '$labelBase64'",
                        null, null,
                        null, null
                    )
                    if (cursor.moveToFirst()) {
                        result.append(cursor.getString(cursor.getColumnIndexOrThrow("packages")))
                    }
                    cursor.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                result.append(pkg)
            }
            if (result.isNotEmpty() && result[result.length - 1] != ',') {
                result.append(",")
            }
        }
        userDefinedDb.close()
        return result.toString().split(",").toTypedArray()
    }
}
