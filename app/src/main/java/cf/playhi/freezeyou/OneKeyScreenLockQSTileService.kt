package cf.playhi.freezeyou

import android.annotation.TargetApi
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import cf.playhi.freezeyou.ui.OneKeyScreenLockImmediatelyActivity

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
@TargetApi(Build.VERSION_CODES.N)
class OneKeyScreenLockQSTileService : TileService() {
    override fun onClick() {
        super.onClick()
        startActivityAndCollapse(
            Intent(this, OneKeyScreenLockImmediatelyActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
