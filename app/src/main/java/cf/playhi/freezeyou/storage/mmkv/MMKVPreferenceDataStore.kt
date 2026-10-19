package cf.playhi.freezeyou.storage.mmkv

import android.content.Context
import androidx.preference.PreferenceDataStore

/**
 * The multi-process store handed to androidx Preference screens, so a checkbox written in the UI
 * process is the same value the `:backgroundService` process reads.
 *
 * Every write is followed by a sync and a broadcast: the sync is what gets the value out of this
 * process's copy of the map, the broadcast is what stops another process from going on answering
 * from the value it read before the write.
 */
class MMKVPreferenceDataStore(private val context: Context) : PreferenceDataStore() {

    private val storage = DefaultMultiProcessMMKVStorage()

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return storage.getBoolean(key, defValue)
    }

    override fun putBoolean(key: String, value: Boolean) {
        storage.putBoolean(key, value)
        commit(key)
    }

    override fun getString(key: String, defValue: String?): String? {
        return storage.getString(key, defValue)
    }

    override fun putString(key: String, value: String?) {
        storage.putString(key, value)
        commit(key)
    }

    override fun getInt(key: String, defValue: Int): Int {
        return storage.getInt(key, defValue)
    }

    override fun putInt(key: String, value: Int) {
        storage.putInt(key, value)
        commit(key)
    }

    override fun getLong(key: String, defValue: Long): Long {
        return storage.getLong(key, defValue)
    }

    override fun putLong(key: String, value: Long) {
        storage.putLong(key, value)
        commit(key)
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return storage.getFloat(key, defValue)
    }

    override fun putFloat(key: String, value: Float) {
        storage.putFloat(key, value)
        commit(key)
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        return storage.getStringSet(key, defValues)
    }

    override fun putStringSet(key: String, values: Set<String>?) {
        storage.putStringSet(key, values)
        commit(key)
    }

    private fun commit(key: String) {
        storage.sync()
        CrossProcessPreferenceObserver.notifyKeyChanged(context, key)
    }
}
