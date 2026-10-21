package cf.playhi.freezeyou.storage.mmkv

import android.os.Parcelable
import com.tencent.mmkv.MMKV

interface FreezeYouMMKVStorage {
    val mMMKV: MMKV
    fun getBoolean(key: String, defValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean): FreezeYouMMKVStorage
    fun getString(key: String, defValue: String?): String?
    fun putString(key: String, value: String?): FreezeYouMMKVStorage
    fun encode(key: String, value: String?): Boolean = mMMKV.encode(key, value)
    fun encode(key: String, value: Boolean): Boolean = mMMKV.encode(key, value)
    fun encode(key: String, value: Int): Boolean = mMMKV.encode(key, value)
    fun encode(key: String, value: Long): Boolean = mMMKV.encode(key, value)
    fun encode(key: String, value: Float): Boolean = mMMKV.encode(key, value)
    fun encode(key: String, values: Set<String>?): Boolean =
        if (values == null) { mMMKV.removeValueForKey(key); true } else mMMKV.encode(key, values)

    fun getInt(key: String, defValue: Int): Int
    fun putInt(key: String, value: Int): FreezeYouMMKVStorage
    fun getLong(key: String, defValue: Long): Long
    fun putLong(key: String, value: Long): FreezeYouMMKVStorage
    fun getFloat(key: String, defValue: Float): Float
    fun putFloat(key: String, value: Float): FreezeYouMMKVStorage
    fun getStringSet(key: String, defValues: Set<String>?): Set<String>?
    fun putStringSet(key: String, values: Set<String>?): FreezeYouMMKVStorage
    fun remove(key: String): FreezeYouMMKVStorage

    /**
     * Every key currently stored. Replaces the `getAll()` that Tray offered, which callers used
     * to find the keys they had written under a prefix rather than a fixed name.
     */
    fun allKeys(): Set<String>
    fun <T : Parcelable> getParcelable(key: String, targetCls: Class<T>, defValue: T?): T?
    fun putParcelable(key: String?, value: Parcelable?): FreezeYouMMKVStorage
    fun sync(): FreezeYouMMKVStorage {
        mMMKV.sync()
        return this
    }
}