package cf.playhi.freezeyou.ui

import android.content.Intent
import android.os.Bundle
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.app.FreezeYouBaseActivity
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils

open class OneKeyScreenLockImmediatelyActivity : FreezeYouBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Intent.ACTION_CREATE_SHORTCUT == intent.action) {
            val intent = Intent()
            intent.putExtra(
                Intent.EXTRA_SHORTCUT_INTENT,
                Intent(this, OneKeyScreenLockImmediatelyActivity::class.java)
            )
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.oneKeyLockScreen))
            @Suppress("DEPRECATION")
            intent.putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this, R.drawable.screenlock)
            )
            setResult(RESULT_OK, intent)
        } else {
            DevicePolicyManagerUtils.doLockScreen(this)
        }
        finish()
    }

    override fun activityNeedCheckAppLock(): Boolean {
        return false
    }
}
