package cf.playhi.freezeyou.storage.legacy

import android.content.Context
import androidx.preference.PreferenceManager

/**
 * Reads settings from default SharedPreferences to migrate them into multi-process MMKV storage.
 *
 * Tray migration was dropped by owner decision D3; only SharedPreferences fallback remains.
 */
class LegacyPreferenceSource(private val context: Context) {

    // Read once: getAll() copies the whole preference file, and a migration asks it for dozens of
    // keys. Lazy so a process starting after all migrations are done does not pay for this copy.
    private val stored: Map<String, *> by lazy {
        PreferenceManager.getDefaultSharedPreferences(context).all
    }

    fun getString(key: String, defValue: String?): String? {
        return stored[key] as? String ?: defValue
    }

    fun getBoolean(key: String, defValue: Boolean): Boolean {
        return when (val value = stored[key]) {
            is Boolean -> value
            is String -> value.toBoolean()
            else -> defValue
        }
    }
}
