package cf.playhi.freezeyou.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import cf.playhi.freezeyou.ForceStop
import cf.playhi.freezeyou.Freeze
import cf.playhi.freezeyou.OneKeyFreeze
import cf.playhi.freezeyou.OneKeyUF
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage
import cf.playhi.freezeyou.ui.OneKeyScreenLockImmediatelyActivity
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID

object ShortcutTokenUtils {

    const val EXTRA_AUTH_TOKEN = "shortcutToken"
    private const val KEY_AUTH_TOKEN_PREFIX = "shortcutAuthToken_"

    const val ACTION_FREEZE = "freeze"
    const val ACTION_FORCE_STOP = "force_stop"
    const val ACTION_ONE_KEY_FREEZE = "one_key_freeze"
    const val ACTION_ONE_KEY_UF = "one_key_uf"
    const val ACTION_LOCK_SCREEN = "lock_screen"

    /**
     * Ids the launcher knows the two dynamic shortcuts by. They are literals on purpose: an id
     * built from a translated label would change with the language and leave the old pin behind.
     */
    const val SHORTCUT_ID_ONE_KEY_FREEZE = "onekeyFreeze"
    const val SHORTCUT_ID_ONE_KEY_UF = "onekeyUF"

    @JvmStatic
    fun getOrCreateAuthToken(action: String): String {
        val storage = DefaultMultiProcessMMKVStorage()
        storage.lock()
        try {
            val key = KEY_AUTH_TOKEN_PREFIX + action
            var token = storage.getString(key, null)
            if (token.isNullOrEmpty()) {
                token = UUID.randomUUID().toString()
                storage.putString(key, token).sync()
            }
            return token
        } finally {
            storage.unlock()
        }
    }

    @JvmStatic
    fun attachToken(intent: Intent, action: String): Intent {
        intent.putExtra(EXTRA_AUTH_TOKEN, getOrCreateAuthToken(action))
        return intent
    }

    @JvmStatic
    fun verifyToken(intent: Intent?, action: String): Boolean {
        if (intent == null) return false
        val token = intent.getStringExtra(EXTRA_AUTH_TOKEN) ?: return false
        val expected = getOrCreateAuthToken(action)
        return MessageDigest.isEqual(
            token.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * A context whose resources resolve in the language the user picked in the app settings.
     * The launcher renders shortcut labels itself, from this process' configuration, so the
     * labels have to be read through a context carrying the chosen locale, not the system one.
     */
    @JvmStatic
    fun localizedContext(context: Context): Context =
        localizedContext(context, Support.getLocalString(context))

    @JvmStatic
    fun localizedContext(context: Context, localeTag: String): Context {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return context
        }
        val configuration = Configuration()
        configuration.setLocale(
            if (DefaultMultiProcessMMKVStorageStringKeys.languagePref.defaultValue()
                    .equals(localeTag, ignoreCase = true)
            ) {
                Locale.getDefault()
            } else {
                Locale.forLanguageTag(localeTag)
            }
        )
        return context.createConfigurationContext(configuration)
    }

    /**
     * On Android 13+ the launcher also reads the *static* shortcuts of `res/xml/shortcuts.xml`
     * in the per-app locale, which only the system's LocaleManager can tell it. "Default" means
     * follow the device, which is what an empty locale list asks for. Setting the same locales
     * again is a no-op on the service side, so this is safe to run on every app start.
     */
    private fun syncApplicationLocales(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }
        val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
            ?: return
        val localeTag = Support.getLocalString(context)
        localeManager.applicationLocales =
            if (DefaultMultiProcessMMKVStorageStringKeys.languagePref.defaultValue()
                    .equals(localeTag, ignoreCase = true)
            ) {
                android.os.LocaleList.getEmptyLocaleList()
            } else {
                android.os.LocaleList.forLanguageTags(localeTag)
            }
    }

    @JvmStatic
    fun initDynamicShortcuts(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
                val localized = localizedContext(context)
                val freezeIntent = Intent(context, OneKeyFreeze::class.java).apply {
                    action = Intent.ACTION_VIEW
                }
                attachToken(freezeIntent, ACTION_ONE_KEY_FREEZE)

                val ufIntent = Intent(context, OneKeyUF::class.java).apply {
                    action = Intent.ACTION_VIEW
                }
                attachToken(ufIntent, ACTION_ONE_KEY_UF)

                val freezeShortcut = ShortcutInfo.Builder(context, SHORTCUT_ID_ONE_KEY_FREEZE)
                    .setShortLabel(localized.getString(R.string.oneKeyFreeze))
                    .setLongLabel(localized.getString(R.string.oneKeyFreeze))
                    .setIcon(Icon.createWithResource(localized, R.drawable.ic_shortcut_logo))
                    .setIntent(freezeIntent)
                    .build()

                val ufShortcut = ShortcutInfo.Builder(context, SHORTCUT_ID_ONE_KEY_UF)
                    .setShortLabel(localized.getString(R.string.oneKeyUF))
                    .setLongLabel(localized.getString(R.string.oneKeyUF))
                    .setIcon(Icon.createWithResource(localized, R.drawable.ic_shortcut_logo))
                    .setIntent(ufIntent)
                    .build()

                shortcutManager.dynamicShortcuts = listOf(freezeShortcut, ufShortcut)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pinned = shortcutManager.pinnedShortcuts
                    val toUpdate = mutableListOf<ShortcutInfo>()
                    for (shortcut in pinned) {
                        val sIntent = shortcut.intent ?: continue
                        val component = sIntent.component ?: continue
                        if (component.packageName == context.packageName) {
                            val actionType = when (component.className) {
                                Freeze::class.java.name -> ACTION_FREEZE
                                OneKeyFreeze::class.java.name -> ACTION_ONE_KEY_FREEZE
                                OneKeyUF::class.java.name -> ACTION_ONE_KEY_UF
                                ForceStop::class.java.name -> ACTION_FORCE_STOP
                                OneKeyScreenLockImmediatelyActivity::class.java.name,
                                cf.playhi.freezeyou.OneKeyScreenLockImmediatelyActivity::class.java.name -> ACTION_LOCK_SCREEN
                                else -> null
                            }
                            if (actionType != null && !verifyToken(sIntent, actionType)) {
                                val updatedIntent = Intent(sIntent)
                                attachToken(updatedIntent, actionType)
                                toUpdate.add(
                                    ShortcutInfo.Builder(context, shortcut.id)
                                        .setIntent(updatedIntent)
                                        .build()
                                )
                            }
                        }
                    }
                    if (toUpdate.isNotEmpty()) {
                        shortcutManager.updateShortcuts(toUpdate)
                    }
                }

                // Last: changing the per-app locale can make the system relaunch the process,
                // and the shortcuts published above must survive that handover.
                syncApplicationLocales(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
