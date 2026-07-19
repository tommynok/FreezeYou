package cf.playhi.freezeyou.export

object FUFMode {
    /**
     * API v1
     */
    @Suppress("DeprecatedIsStillUsed")
    @Deprecated("API v1")
    const val MODE_AUTO = "MODE_AUTO"

    /**
     * 不建议使用自动切换模式
     */
    @Suppress("DeprecatedIsStillUsed")
    @Deprecated("不建议使用自动切换模式")
    const val MODE_LEGACY_AUTO = "MODE_LEGACY_AUTO"

    /**
     * API v1
     */
    @Suppress("DeprecatedIsStillUsed")
    @Deprecated("API v1")
    const val MODE_ROOT = "MODE_ROOT"
    const val MODE_ROOT_DISABLE_ENABLE = "MODE_ROOT_DISABLE_ENABLE"
    const val MODE_ROOT_HIDE_UNHIDE = "MODE_ROOT_HIDE_UNHIDE"

    /**
     * API v1
     */
    @Suppress("DeprecatedIsStillUsed")
    @Deprecated("API v1")
    const val MODE_MROOT = "MODE_MROOT"
    const val MODE_DPM = "MODE_DPM"
    const val MODE_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED = "MODE_SYSTEM_APP_ENABLE_DISABLE_UNTIL_USED"
    const val MODE_SYSTEM_APP_ENABLE_DISABLE_USER = "MODE_SYSTEM_APP_ENABLE_DISABLE_USER"
    const val MODE_SYSTEM_APP_ENABLE_DISABLE = "MODE_SYSTEM_APP_ENABLE_DISABLE"
    const val MODE_PROFILE_OWNER = "MODE_PROFILE_OWNER"
    const val MODE_UNKNOWN = "MODE_UNKNOWN"
}
