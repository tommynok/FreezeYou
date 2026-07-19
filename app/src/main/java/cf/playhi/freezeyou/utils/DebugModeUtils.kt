package cf.playhi.freezeyou.utils

import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.debugModeEnabled
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage

object DebugModeUtils {
    @JvmStatic
    fun isDebugModeEnabled(): Boolean {
        return DefaultMultiProcessMMKVStorage()
            .getBoolean(debugModeEnabled.name, debugModeEnabled.defaultValue())
    }
}
