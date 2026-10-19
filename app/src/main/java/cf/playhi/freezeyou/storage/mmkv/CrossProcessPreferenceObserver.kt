package cf.playhi.freezeyou.storage.mmkv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Tells the app's other processes that a preference key was written, and lets them wait for it.
 *
 * MMKV keeps every process on the same mmap, so a write is not lost - but nothing tells a reader
 * that the value it cached, or a list it is about to freeze, has just changed. The broadcast is
 * that notification: the sender names the key, whoever is listening re-reads it.
 */
object CrossProcessPreferenceObserver {

    const val ACTION_PREF_CHANGED = "cf.playhi.freezeyou.PREFERENCE_CHANGED"
    const val EXTRA_KEY = "extra_key"
    const val EXTRA_TIMESTAMP = "extra_timestamp"
    const val PERMISSION_SYNC = "cf.playhi.freezeyou.permission.CROSS_PROCESS_PREF_SYNC"

    /**
     * Buffered and lossy on purpose: the receiver must not block on a slow collector, and a
     * collector that was not listening at the moment of the write has nothing to catch up with -
     * it reads the stored value when it starts.
     */
    private val changedKeys = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val key = intent.getStringExtra(EXTRA_KEY) ?: return
            changedKeys.tryEmit(key)
        }
    }

    private var registered = false

    /**
     * Call from [android.app.Application.onCreate], which every process of the app runs.
     */
    @JvmStatic
    @Synchronized
    fun register(context: Context) {
        if (registered) {
            return
        }
        val appContext = context.applicationContext ?: context
        // The receiver permission is the other half of the pair with notifyKeyChanged(): a process
        // that does not hold PERMISSION_SYNC can neither send to this receiver nor get past it.
        // On API 33+, specify RECEIVER_NOT_EXPORTED for forward-compatibility when targetSdk is bumped.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(
                receiver,
                IntentFilter(ACTION_PREF_CHANGED),
                PERMISSION_SYNC,
                null,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            appContext.registerReceiver(
                receiver,
                IntentFilter(ACTION_PREF_CHANGED),
                PERMISSION_SYNC,
                null
            )
        }
        registered = true
    }

    @JvmStatic
    fun notifyKeyChanged(context: Context, key: String) {
        val intent = Intent(ACTION_PREF_CHANGED)
            .setPackage(context.packageName)
            .putExtra(EXTRA_KEY, key)
            .putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())
        // Registered-only: the action is not in any manifest, so an idle process would be started
        // just to be told a list changed, and that is exactly what a freeze shortcut should never
        // pay for.
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY)
        context.sendBroadcast(intent, PERMISSION_SYNC)
    }

    /**
     * Emits once per change of [key], made by this or any other process of the app.
     */
    @JvmStatic
    fun observeKey(key: String): Flow<Unit> =
        changedKeys.filter { it == key }.map { }
}
