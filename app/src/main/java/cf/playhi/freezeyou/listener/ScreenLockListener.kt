package cf.playhi.freezeyou.listener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import cf.playhi.freezeyou.service.OneKeyFreezeService
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.onekeyFreezeWhenLockScreen
import cf.playhi.freezeyou.utils.ServiceUtils

class ScreenLockListener(private val mContext: Context) {
    private val mScreenLockReceiver: ScreenLockBroadcastReceiver = ScreenLockBroadcastReceiver()

    private inner class ScreenLockBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != null) {
                when (action) {
                    Intent.ACTION_SCREEN_OFF -> if (onekeyFreezeWhenLockScreen.getValue(null)) {
                        ServiceUtils.startService(
                            context,
                            Intent(context, OneKeyFreezeService::class.java)
                                .putExtra("autoCheckAndLockScreen", false)
                        )
                    }
                    else -> {}
                }
            }
        }
    }

    fun registerListener() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        ContextCompat.registerReceiver(
            mContext,
            mScreenLockReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun unregisterListener() {
        mContext.unregisterReceiver(mScreenLockReceiver)
    }
}
