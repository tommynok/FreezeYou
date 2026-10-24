package cf.playhi.freezeyou.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import cf.playhi.freezeyou.ForceStop
import cf.playhi.freezeyou.Freeze
import cf.playhi.freezeyou.OneKeyFreeze
import cf.playhi.freezeyou.OneKeyUF
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage
import cf.playhi.freezeyou.ui.OneKeyScreenLockImmediatelyActivity
import java.security.MessageDigest
import java.util.UUID

object ShortcutTokenUtils {

    const val EXTRA_AUTH_TOKEN = "shortcutToken"
    private const val KEY_AUTH_TOKEN_PREFIX = "shortcutAuthToken_"

    const val ACTION_FREEZE = "freeze"
    const val ACTION_FORCE_STOP = "force_stop"
    const val ACTION_ONE_KEY_FREEZE = "one_key_freeze"
    const val ACTION_ONE_KEY_UF = "one_key_uf"
    const val ACTION_LOCK_SCREEN = "lock_screen"

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

    @JvmStatic
    fun initDynamicShortcuts(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            try {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
                val freezeIntent = Intent(context, OneKeyFreeze::class.java).apply {
                    action = Intent.ACTION_VIEW
                }
                attachToken(freezeIntent, ACTION_ONE_KEY_FREEZE)

                val ufIntent = Intent(context, OneKeyUF::class.java).apply {
                    action = Intent.ACTION_VIEW
                }
                attachToken(ufIntent, ACTION_ONE_KEY_UF)

                val freezeShortcut = ShortcutInfo.Builder(context, "onekeyFreeze")
                    .setShortLabel(context.getString(R.string.oneKeyFreeze))
                    .setLongLabel(context.getString(R.string.oneKeyFreeze))
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_logo))
                    .setIntent(freezeIntent)
                    .build()

                val ufShortcut = ShortcutInfo.Builder(context, "onekeyUF")
                    .setShortLabel(context.getString(R.string.oneKeyUF))
                    .setLongLabel(context.getString(R.string.oneKeyUF))
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_logo))
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
