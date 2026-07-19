package cf.playhi.freezeyou

import android.content.Intent
import android.os.Bundle
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.service.OneKeyFreezeService
import cf.playhi.freezeyou.utils.ServiceUtils

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
class OneKeyFreeze : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Intent.ACTION_CREATE_SHORTCUT == intent.action) {
            val intent = Intent()
            intent.putExtra(
                Intent.EXTRA_SHORTCUT_INTENT,
                Intent(this, OneKeyFreeze::class.java)
            )
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.oneKeyFreeze))
            @Suppress("DEPRECATION")
            intent.putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher_new_round)
            )
            setResult(RESULT_OK, intent)
        } else {
            ServiceUtils.startService(
                this,
                Intent(applicationContext, OneKeyFreezeService::class.java)
                    .putExtra("autoCheckAndLockScreen", getIntent().getBooleanExtra("autoCheckAndLockScreen", true))
            )
        }
        finish()
    }
}
