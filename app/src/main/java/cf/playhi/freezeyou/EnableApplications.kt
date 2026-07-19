package cf.playhi.freezeyou

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import cf.playhi.freezeyou.utils.DebugModeUtils.isDebugModeEnabled
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils.isDeviceOwner
import cf.playhi.freezeyou.utils.FUFUtils.oneKeyActionMRoot
import cf.playhi.freezeyou.utils.FUFUtils.oneKeyActionRoot

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
class EnableApplications : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        if (intent != null) {
            val packages = intent.getStringArrayExtra("packages")
            if (isDebugModeEnabled()) {
                Log.e("DebugModeLogcat", "Intent toString:$intent")
                if (packages != null) {
                    for (pkg in packages) {
                        Log.e("DebugModeLogcat", "Intent packages:$pkg")
                    }
                }
            }
            if (packages != null) {
                setResult(Activity.RESULT_OK)
                if (isDeviceOwner(this@EnableApplications)) {
                    @Suppress("DEPRECATION")
                    oneKeyActionMRoot(this@EnableApplications, false, packages)
                    finish()
                } else {
                    oneKeyActionRoot(this@EnableApplications, false, packages)
                    finish()
                }
            }
        } else {
            if (isDebugModeEnabled()) {
                Log.e("DebugModeLogcat", "Intent: null")
            }
        }
    }
}
