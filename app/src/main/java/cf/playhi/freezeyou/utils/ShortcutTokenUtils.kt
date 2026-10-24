package cf.playhi.freezeyou.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import cf.playhi.freezeyou.OneKeyFreeze
import cf.playhi.freezeyou.OneKeyUF
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.storage.mmkv.DefaultMultiProcessMMKVStorage
import java.security.MessageDigest
import java.util.UUID

object ShortcutTokenUtils {

    const val EXTRA_AUTH_TOKEN = "shortcutToken"
    private const val KEY_AUTH_TOKEN = "shortcutAuthToken"

    @JvmStatic
    @Synchronized
    fun getOrCreateAuthToken(): String {
        val storage = DefaultMultiProcessMMKVStorage()
        var token = storage.getString(KEY_AUTH_TOKEN, null)
        if (token.isNullOrEmpty()) {
            token = UUID.randomUUID().toString()
            storage.putString(KEY_AUTH_TOKEN, token).sync()
        }
        return token
    }

    @JvmStatic
    fun attachToken(intent: Intent): Intent {
        intent.putExtra(EXTRA_AUTH_TOKEN, getOrCreateAuthToken())
        return intent
    }

    @JvmStatic
    fun verifyToken(intent: Intent?): Boolean {
        if (intent == null) return false
        val token = intent.getStringExtra(EXTRA_AUTH_TOKEN) ?: return false
        val expected = getOrCreateAuthToken()
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
                attachToken(freezeIntent)

                val ufIntent = Intent(context, OneKeyUF::class.java).apply {
                    action = Intent.ACTION_VIEW
                }
                attachToken(ufIntent)

                val freezeShortcut = ShortcutInfo.Builder(context, "dynamic_onekeyFreeze")
                    .setShortLabel(context.getString(R.string.oneKeyFreeze))
                    .setLongLabel(context.getString(R.string.oneKeyFreeze))
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_logo))
                    .setIntent(freezeIntent)
                    .build()

                val ufShortcut = ShortcutInfo.Builder(context, "dynamic_onekeyUF")
                    .setShortLabel(context.getString(R.string.oneKeyUF))
                    .setLongLabel(context.getString(R.string.oneKeyUF))
                    .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_logo))
                    .setIntent(ufIntent)
                    .build()

                shortcutManager.dynamicShortcuts = listOf(freezeShortcut, ufShortcut)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
