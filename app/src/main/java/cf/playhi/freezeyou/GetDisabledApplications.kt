package cf.playhi.freezeyou

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cf.playhi.freezeyou.utils.FUFUtils.checkMRootFrozen
import cf.playhi.freezeyou.utils.FUFUtils.checkRootFrozen

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
class GetDisabledApplications : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val applicationInfo = applicationContext.packageManager.getInstalledApplications(
            PackageManager.GET_UNINSTALLED_PACKAGES
        )
        val size = applicationInfo.size
        val appList = ArrayList<String>()
        for (i in 0 until size) {
            val packageName = applicationInfo[i].packageName
            if (checkRootFrozen(this@GetDisabledApplications, packageName, null) || checkMRootFrozen(
                    this@GetDisabledApplications,
                    packageName
                )
            ) {
                appList.add(packageName)
            }
        }
        setResult(Activity.RESULT_OK, Intent().putStringArrayListExtra("packages", appList))
        finish()
    }
}
