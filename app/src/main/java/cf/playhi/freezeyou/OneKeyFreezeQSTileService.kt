package cf.playhi.freezeyou

import android.annotation.TargetApi
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import cf.playhi.freezeyou.service.OneKeyFreezeService
import cf.playhi.freezeyou.utils.ServiceUtils

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
@TargetApi(Build.VERSION_CODES.N)
class OneKeyFreezeQSTileService : TileService() {
    override fun onClick() {
        super.onClick()
        ServiceUtils.startService(
            this,
            Intent(applicationContext, OneKeyFreezeService::class.java)
        )
    }
}
