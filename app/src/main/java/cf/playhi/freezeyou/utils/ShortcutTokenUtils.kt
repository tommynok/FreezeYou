package cf.playhi.freezeyou.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import cf.playhi.freezeyou.ForceStop
import cf.playhi.freezeyou.Freeze
import cf.playhi.freezeyou.OneKeyFreeze
import cf.playhi.freezeyou.OneKeyUF
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage
import cf.playhi.freezeyou.ui.OneKeyScreenLockImmediatelyActivity
import cf.playhi.freezeyou.ui.ScheduledTasksManageActivity
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

object ShortcutTokenUtils {

    /**
     * One worker for every republish: two language changes in a row must not race, and the task
     * reads the saved language when it runs, so the last publication always carries the latest
     * choice even if an earlier task is still queued.
     */
    private val publishExecutor = Executors.newSingleThreadExecutor()

    const val EXTRA_AUTH_TOKEN = "shortcutToken"
    internal const val KEY_AUTH_TOKEN_PREFIX = "shortcutAuthToken_"

    const val ACTION_FREEZE = "freeze"
    const val ACTION_FORCE_STOP = "force_stop"
    const val ACTION_ONE_KEY_FREEZE = "one_key_freeze"
    const val ACTION_ONE_KEY_UF = "one_key_uf"
    const val ACTION_LOCK_SCREEN = "lock_screen"

    val ALL_ACTIONS: List<String> = listOf(
        ACTION_FREEZE,
        ACTION_FORCE_STOP,
        ACTION_ONE_KEY_FREEZE,
        ACTION_ONE_KEY_UF,
        ACTION_LOCK_SCREEN
    )

    /**
     * Ids the launcher knows the dynamic shortcuts by. They are literals on purpose: an id
     * built from a translated label would change with the language and leave the old pin behind.
     */
    const val SHORTCUT_ID_ONE_KEY_FREEZE = "onekeyFreeze"
    const val SHORTCUT_ID_ONE_KEY_UF = "onekeyUF"

    /**
     * Reuses the id of the shortcut block removed from res/xml/shortcuts.xml. A launcher reads a
     * static label straight from the APK with the system locale, so it never followed the language
     * picked inside the app; publishing it here, with a localized label, is what fixes that.
     */
    const val SHORTCUT_ID_SCHEDULED_TASKS = "scheduledTasks"

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
    fun exportTokensToJson(): JSONObject {
        val storage = DefaultMultiProcessMMKVStorage()
        val json = JSONObject()
        for (action in ALL_ACTIONS) {
            val key = KEY_AUTH_TOKEN_PREFIX + action
            val token = storage.getString(key, null)
            if (!token.isNullOrEmpty()) {
                json.put(action, token)
            }
        }
        return json
    }

    @JvmStatic
    fun importTokensFromJson(json: JSONObject?) {
        if (json == null) return
        val storage = DefaultMultiProcessMMKVStorage()
        storage.lock()
        try {
            for (action in ALL_ACTIONS) {
                if (json.has(action) && !json.isNull(action)) {
                    val token = json.optString(action, "")
                    if (token.isNotEmpty()) {
                        val key = KEY_AUTH_TOKEN_PREFIX + action
                        // Import-if-absent: do not overwrite live valid tokens for existing home screen shortcuts
                        if (storage.getString(key, null).isNullOrEmpty()) {
                            storage.putString(key, token)
                        }
                    }
                }
            }
            storage.sync()
        } finally {
            storage.unlock()
        }
    }

    @JvmStatic
    fun resolveAction(className: String?): String? = when (className) {
        "cf.playhi.freezeyou.Freeze",
        Freeze::class.java.name -> ACTION_FREEZE
        "cf.playhi.freezeyou.ForceStop",
        ForceStop::class.java.name -> ACTION_FORCE_STOP
        "cf.playhi.freezeyou.OneKeyFreeze",
        OneKeyFreeze::class.java.name -> ACTION_ONE_KEY_FREEZE
        "cf.playhi.freezeyou.OneKeyUF",
        OneKeyUF::class.java.name -> ACTION_ONE_KEY_UF
        "cf.playhi.freezeyou.ui.OneKeyScreenLockImmediatelyActivity",
        "cf.playhi.freezeyou.OneKeyScreenLockImmediatelyActivity",
        OneKeyScreenLockImmediatelyActivity::class.java.name -> ACTION_LOCK_SCREEN
        else -> null
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

    /**
     * The device locale, taken from the system resources rather than from the process default:
     * on Android 13+ the default already carries whatever per-app language the system has for this
     * app, so Locale.getDefault() would publish "Default" labels in that language instead of the
     * device one.
     */
    private fun deviceLocale(): Locale {
        val configuration = android.content.res.Resources.getSystem().configuration
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val locales = configuration.locales
            if (!locales.isEmpty) {
                return locales.get(0)
            }
        }
        @Suppress("DEPRECATION")
        return configuration.locale
    }

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
                deviceLocale()
            } else {
                Locale.forLanguageTag(localeTag)
            }
        )
        return context.createConfigurationContext(configuration)
    }

    @JvmStatic
    fun initDynamicShortcutsAsync(context: Context) {
        val applicationContext = context.applicationContext
        publishExecutor.execute { initDynamicShortcuts(applicationContext) }
    }

    @JvmStatic
    fun initDynamicShortcuts(context: Context) {
        publishDynamicShortcuts(context)
    }

    private fun publishDynamicShortcuts(context: Context) {
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

                val scheduledTasksIntent =
                    Intent(context, ScheduledTasksManageActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                    }

                val scheduledTasksShortcut = ShortcutInfo.Builder(context, SHORTCUT_ID_SCHEDULED_TASKS)
                    .setShortLabel(localized.getString(R.string.scheduledTasks))
                    .setLongLabel(localized.getString(R.string.scheduledTasks))
                    .setIcon(Icon.createWithResource(localized, R.drawable.ic_shortcut_access_alarm))
                    .setIntent(scheduledTasksIntent)
                    .build()

                val dynamicSet = shortcutManager.setDynamicShortcuts(
                    listOf(freezeShortcut, ufShortcut, scheduledTasksShortcut)
                )
                if (!dynamicSet) {
                    Log.w("ShortcutTokenUtils", "setDynamicShortcuts returned false (possible rate limit)")
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val pinned = shortcutManager.pinnedShortcuts
                    val toEnable = mutableListOf<String>()
                    val toUpdate = mutableListOf<ShortcutInfo>()
                    for (shortcut in pinned) {
                        if (!shortcut.isEnabled && !shortcut.isImmutable) {
                            toEnable.add(shortcut.id)
                        }
                        val sIntent = shortcut.intent ?: continue
                        val component = sIntent.component ?: continue
                        if (component.packageName == context.packageName) {
                            val actionType = resolveAction(component.className)
                            if (actionType != null && !verifyToken(sIntent, actionType)) {
                                val updatedIntent = Intent(sIntent)
                                attachToken(updatedIntent, actionType)
                                val label = shortcut.shortLabel?.takeIf { it.isNotBlank() }
                                    ?: shortcut.longLabel?.takeIf { it.isNotBlank() }
                                    ?: "FreezeYou"
                                val builder = ShortcutInfo.Builder(context, shortcut.id)
                                    .setShortLabel(label)
                                    .setIntent(updatedIntent)
                                // F1 fix: do NOT call builder.setActivity(). Omitting it preserves
                                // the existing activity and skips the main-activity assertion that
                                // throws if an old icon alias was disabled.
                                toUpdate.add(builder.build())
                            }
                        }
                    }
                    if (toEnable.isNotEmpty()) {
                        for (id in toEnable) {
                            try {
                                shortcutManager.enableShortcuts(listOf(id))
                            } catch (e: Exception) {
                                Log.w("ShortcutTokenUtils", "Failed to enable pinned shortcut $id", e)
                            }
                        }
                    }
                    if (toUpdate.isNotEmpty()) {
                        try {
                            val updated = shortcutManager.updateShortcuts(toUpdate)
                            if (!updated) {
                                Log.w("ShortcutTokenUtils", "updateShortcuts returned false (possible rate limit)")
                            }
                        } catch (e: Exception) {
                            Log.w("ShortcutTokenUtils", "Failed to update pinned shortcuts", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("ShortcutTokenUtils", "Failed to publish or update shortcuts", e)
            }
        }
    }
}
