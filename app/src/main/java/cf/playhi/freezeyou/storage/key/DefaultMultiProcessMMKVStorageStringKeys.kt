package cf.playhi.freezeyou.storage.key

import android.content.Context
import androidx.annotation.StringRes
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.storage.key.KeyCategory.CATEGORY_SETTINGS
import cf.playhi.freezeyou.storage.key.KeyCategory.CATEGORY_SETTINGS_APPEARANCE
import cf.playhi.freezeyou.storage.key.KeyCategory.CATEGORY_SETTINGS_FREEZE_AND_UNFREEZE
import cf.playhi.freezeyou.storage.key.KeyCategory.CATEGORY_SETTINGS_SECURITY
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage
import cf.playhi.freezeyou.storage.mmkv.FreezeYouMMKVStorage

enum class DefaultMultiProcessMMKVStorageStringKeys : AbstractMMKVKey<String?> {

    uiStyleSelection {
        override fun defaultValue(): String = "default"
        override fun valueStringId(): Int = -1
        override fun titleTextStringId(): Int = R.string.uiStyle
        override fun category(): Int = CATEGORY_SETTINGS or CATEGORY_SETTINGS_APPEARANCE
    },

    themeOfAutoSwitchDarkMode {
        override fun defaultValue(): String = "default"
        override fun valueStringId(): Int = -1
        override fun titleTextStringId(): Int = R.string.themeOfAutoSwitchDarkMode
        override fun category(): Int = CATEGORY_SETTINGS or CATEGORY_SETTINGS_APPEARANCE
    },

    languagePref {
        override fun defaultValue() = "Default"
        override fun valueStringId(): Int = -1
        override fun titleTextStringId(): Int = R.string.displayLanguage
        override fun category(): Int = CATEGORY_SETTINGS or CATEGORY_SETTINGS_APPEARANCE
    },

    selectFUFMode {
        override fun defaultValue() = "0"
        override fun valueStringId(): Int = -1
        override fun titleTextStringId(): Int = R.string.selectFUFMode
        override fun category(): Int = CATEGORY_SETTINGS or CATEGORY_SETTINGS_FREEZE_AND_UNFREEZE
    },

    shortCutOneKeyFreezeAdditionalOptions {
        override fun defaultValue() = "nothing"
        override fun valueStringId(): Int = -1
        override fun titleTextStringId(): Int = R.string.shortCutOneKeyFreezeAdditionalOptions
        override fun category(): Int = CATEGORY_SETTINGS or CATEGORY_SETTINGS_FREEZE_AND_UNFREEZE
    },

    shortcutClickAction {
        // The same default the settings row declares, so an unset key never offers "nothing
        // chosen" as an answer. A backup does not export through this default: BackupUtils asks
        // the resolver, because an installation that inherited its action from the legacy
        // booleans stores nothing here and would otherwise be sent out as "ask for action".
        override fun defaultValue() = "dialog"
        override fun valueStringId(): Int = -1
        override fun titleTextStringId(): Int = R.string.shortcutClickAction
        override fun category(): Int = CATEGORY_SETTINGS or CATEGORY_SETTINGS_FREEZE_AND_UNFREEZE
    },

    appLockTimeout {
        // Milliseconds, as the settings list stores them. "0" locks as soon as the app leaves the
        // foreground, so the readers must not treat it as a time. "900000" is the timeout the lock
        // had hardcoded before it became a setting, and what a stored legacy "-1" reads as.
        override fun defaultValue() = "900000"
        override fun valueStringId(): Int = -1
        override fun titleTextStringId(): Int = R.string.appLockTimeout
        override fun category(): Int = CATEGORY_SETTINGS or CATEGORY_SETTINGS_SECURITY
    },

    appLockReLockPolicy {
        // "immediately" locks as soon as the protected app leaves the foreground.
        // "screen_off" keeps the protected app unlocked while the device screen remains on.
        override fun defaultValue() = "immediately"
        override fun valueStringId(): Int = -1
        override fun titleTextStringId(): Int = R.string.appLockReLockPolicy
        override fun category(): Int = CATEGORY_SETTINGS or CATEGORY_SETTINGS_SECURITY
    };

    /**
     * If null, use `stringId()` to get the String value.
     */
    abstract override fun defaultValue(): String?

    /**
     * When `defaultValue()` is null, use the value of `stringId()` to get the String value.
     * If `defaultValue()` is not null, this will return -1.
     */
    @StringRes
    abstract fun valueStringId(): Int

    @StringRes
    abstract override fun titleTextStringId(): Int
    abstract override fun category(): Int
    override fun getValue(context: Context?): String? {
        return DefaultMultiProcessMMKVStorage().getString(
            this.name,
            if (this.defaultValue() == null) context?.getString(this.valueStringId())
            else this.defaultValue()
        )
    }

    override fun setValue(context: Context?, value: String?) {
        DefaultMultiProcessMMKVStorage().putString(this.name, value)
    }

    override fun sync(): FreezeYouMMKVStorage {
        return DefaultMultiProcessMMKVStorage().sync()
    }
}
