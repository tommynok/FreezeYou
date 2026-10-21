package cf.playhi.freezeyou.storage.legacy

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.preference.PreferenceManager
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage
import java.io.File

/**
 * The last reader for the data the `net.grandcentrix.tray` library used to own.
 *
 * Tray is gone from the classpath, so its database is read as plain SQLite here. The layout below
 * is copied from the library itself rather than guessed at: `TrayDBHelper` names its file `tray.db`
 * (or `tray_backup_excluded.db` for the provider created with backup off) and puts every preference
 * in one table, `TrayPreferences`, with the columns `KEY`, `VALUE`, `MODULE`. `AppPreferences` -
 * the only module FreezeYou used - wrote the module as the package name.
 *
 * Reading stays deliberately loose. An install that never ran a Tray build has no file at all, one
 * that upgraded through several versions may have both files, and a table an old version created
 * without `MIGRATED_KEY` still has the three columns this reads. Anything else is a database we
 * cannot make sense of, and losing a preference beats failing to start.
 */
object TrayStorage {

    private const val TABLE_NAME = "TrayPreferences"
    private const val COLUMN_KEY = "KEY"
    private const val COLUMN_VALUE = "VALUE"
    private const val COLUMN_MODULE = "MODULE"

    /** Both files Tray's provider could have created; whichever exist are read. */
    private val DATABASE_NAMES = arrayOf("tray.db", "tray_backup_excluded.db")

    /** The side files SQLite keeps next to a database, which must go with it. */
    private val DATABASE_SUFFIXES = arrayOf("", "-journal", "-wal", "-shm")

    /**
     * Every preference Tray still holds, keyed by name.
     *
     * A name written under two modules resolves to the current package's row, because that is the
     * one `AppPreferences` - and so every reader this app ever had - was answering from. The
     * comparison is made in code rather than in SQL so that an install whose package was renamed
     * along the way migrates its old rows instead of dropping them.
     */
    @JvmStatic
    fun readAll(context: Context): Map<String, String> {
        val ownModule = context.packageName
        val own = HashMap<String, String>()
        val other = HashMap<String, String>()
        for (name in DATABASE_NAMES) {
            val file = context.getDatabasePath(name)
            if (!file.exists() || !file.isFile) {
                continue
            }
            var db: SQLiteDatabase? = null
            try {
                // READONLY, never openOrCreateDatabase: that one creates a missing file, and a
                // migration that writes an empty Tray database back onto the disk would be read as
                // "there is data here" by the next run.
                db = SQLiteDatabase.openDatabase(
                    file.absolutePath, null,
                    SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
                )
                readTable(db, ownModule, own, other)
            } catch (ignored: Exception) {
                // A database that cannot be opened is a database with nothing to migrate.
            } finally {
                try {
                    db?.close()
                } catch (ignored: Exception) {
                }
            }
        }
        // putIfAbsent is a Java 8 Map method and this app runs down to API 16, where it does not
        // exist; the check is written out instead.
        other.forEach { (key, value) -> if (!own.containsKey(key)) own[key] = value }
        return own
    }

    private fun readTable(
        db: SQLiteDatabase,
        ownModule: String,
        own: HashMap<String, String>,
        other: HashMap<String, String>
    ) {
        var cursor: Cursor? = null
        try {
            cursor = db.query(
                TABLE_NAME, arrayOf(COLUMN_KEY, COLUMN_VALUE, COLUMN_MODULE),
                null, null, null, null, null
            )
            if (cursor == null || cursor.getColumnIndex(COLUMN_KEY) < 0
                || cursor.getColumnIndex(COLUMN_VALUE) < 0
            ) {
                return
            }
            val keyColumn = cursor.getColumnIndex(COLUMN_KEY)
            val valueColumn = cursor.getColumnIndex(COLUMN_VALUE)
            val moduleColumn = cursor.getColumnIndex(COLUMN_MODULE)
            while (cursor.moveToNext()) {
                val key = cursor.getString(keyColumn) ?: continue
                val value = cursor.getString(valueColumn) ?: continue
                val module = if (moduleColumn < 0) ownModule else cursor.getString(moduleColumn)
                if (module == ownModule) own[key] = value else other[key] = value
            }
        } finally {
            try {
                cursor?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Remove the database and its side files. Called once, after every key has been copied out,
     * and never again: the lock file that guards the call is written before the delete, so an
     * install that dies between the two simply leaves an orphan file behind.
     */
    @JvmStatic
    fun delete(context: Context) {
        for (name in DATABASE_NAMES) {
            val base = context.getDatabasePath(name).absolutePath
            for (suffix in DATABASE_SUFFIXES) {
                val file = File(base + suffix)
                if (file.exists() && file.isFile) {
                    file.delete()
                }
            }
        }
    }
}

/**
 * Where a preference lived before the multi-process store: in Tray, or in the default
 * SharedPreferences, or in both.
 *
 * The two migrations this app used to run chained them - default SharedPreferences into Tray, then
 * Tray into MMKV - because Tray sat in the middle. With Tray gone the chain collapses into this one
 * reader, which keeps the order the old chain had: whatever Tray held wins, since that is what the
 * app was reading until now. Tray stored everything as text and parsed booleans with
 * `Boolean.parseBoolean`, so a value it held is converted the same way here.
 */
class LegacyPreferenceSource(context: Context) {

    private val tray: Map<String, String> = TrayStorage.readAll(context)

    // Read once: getAll() copies the whole preference file, and a migration asks it for dozens of
    // keys.
    private val stored: Map<String, *> =
        PreferenceManager.getDefaultSharedPreferences(context).all

    /** Names Tray holds anything under. */
    fun trayKeys(): Set<String> = tray.keys

    /** The row Tray holds for [key], or null. Nothing else is consulted. */
    fun trayValue(key: String): String? = tray[key]

    fun getString(key: String, defValue: String?): String? {
        tray[key]?.let { return it }
        return stored[key] as? String ?: defValue
    }

    fun getBoolean(key: String, defValue: Boolean): Boolean {
        tray[key]?.let { return it.toBoolean() }
        return when (val value = stored[key]) {
            is Boolean -> value
            is String -> value.toBoolean()
            else -> defValue
        }
    }

    /**
     * Copy Tray's own row for [key] into the multi-process store, unless the store already answers
     * for it. A migration that overwrote a value the user has since set would be worse than the
     * migration that never ran, so the destination gets the first say.
     *
     * Only Tray is consulted, and only as text. These are the keys that were never anything but a
     * string, and the default SharedPreferences share that store: copying one of its booleans as
     * text would leave the setting reading its own default forever.
     *
     * @return whether anything was written
     */
    fun copyTrayStringTo(storage: DefaultMultiProcessMMKVStorage, key: String): Boolean {
        val value = tray[key] ?: return false
        if (storage.getString(key, null) != null) {
            return false
        }
        storage.putString(key, value)
        return true
    }
}
