package cf.playhi.freezeyou.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import cf.playhi.freezeyou.Main

class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startActivity(
            Intent(
                context,
                Main::class.java
            ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
