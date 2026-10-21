package cf.playhi.freezeyou.storage.mmkv

import android.os.Parcelable
import com.tencent.mmkv.MMKV

class DefaultMultiProcessMMKVStorage : FreezeYouMMKVStorage {
    override val mMMKV: MMKV
        get() = defaultMMKV

    fun lock() {
        mMMKV.lock()
    }

    fun unlock() {
        mMMKV.unlock()
    }

    companion object {
        const val MMKV_ID = "DefaultMultiProcessKV"
        val defaultMMKV: MMKV by lazy {
            MMKV.mmkvWithID(MMKV_ID, MMKV.MULTI_PROCESS_MODE)
        }
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        return mMMKV.decodeBool(key, defValue)
    }

    override fun putBoolean(key: String, value: Boolean): DefaultMultiProcessMMKVStorage {
        mMMKV.encode(key, value)
        return this
    }

    override fun getString(key: String, defValue: String?): String? {
        return mMMKV.decodeString(key, defValue)
    }

    override fun encode(key: String, value: String?): Boolean = mMMKV.encode(key, value)
    override fun encode(key: String, value: Boolean): Boolean = mMMKV.encode(key, value)
    override fun encode(key: String, value: Int): Boolean = mMMKV.encode(key, value)
    override fun encode(key: String, value: Long): Boolean = mMMKV.encode(key, value)
    override fun encode(key: String, value: Float): Boolean = mMMKV.encode(key, value)
    override fun encode(key: String, values: Set<String>?): Boolean =
        if (values == null) { mMMKV.removeValueForKey(key); true } else mMMKV.encode(key, values)
    override fun putString(key: String, value: String?): DefaultMultiProcessMMKVStorage {
        mMMKV.encode(key, value)
        return this
    }

    override fun getInt(key: String, defValue: Int): Int {
        return mMMKV.decodeInt(key, defValue)
    }

    override fun putInt(key: String, value: Int): DefaultMultiProcessMMKVStorage {
        mMMKV.encode(key, value)
        return this
    }

    override fun getLong(key: String, defValue: Long): Long {
        return mMMKV.decodeLong(key, defValue)
    }

    override fun putLong(key: String, value: Long): DefaultMultiProcessMMKVStorage {
        mMMKV.encode(key, value)
        return this
    }

    override fun getFloat(key: String, defValue: Float): Float {
        return mMMKV.decodeFloat(key, defValue)
    }

    override fun putFloat(key: String, value: Float): DefaultMultiProcessMMKVStorage {
        mMMKV.encode(key, value)
        return this
    }

    // The one-argument reader plus a local fallback, rather than decodeStringSet(key, defValues):
    // what a caller passes as its default is its own business, and MMKV has no say in it.
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        return mMMKV.decodeStringSet(key) ?: defValues
    }

    override fun putStringSet(key: String, values: Set<String>?): DefaultMultiProcessMMKVStorage {
        if (values == null) {
            mMMKV.removeValueForKey(key)
        } else {
            mMMKV.encode(key, values)
        }
        return this
    }

    override fun <T : Parcelable> getParcelable(
        key: String,
        targetCls: Class<T>,
        defValue: T?
    ): T? {
        throw UnsupportedOperationException()
    }

    override fun remove(key: String): DefaultMultiProcessMMKVStorage {
        mMMKV.removeValueForKey(key)
        return this
    }

    // Null on an MMKV instance that has never had a key, so an empty set is the honest answer
    // rather than a null the caller has to remember.
    override fun allKeys(): Set<String> {
        return mMMKV.allKeys()?.toSet() ?: emptySet()
    }

    override fun putParcelable(key: String?, value: Parcelable?): FreezeYouMMKVStorage {
        throw UnsupportedOperationException()
    }

}