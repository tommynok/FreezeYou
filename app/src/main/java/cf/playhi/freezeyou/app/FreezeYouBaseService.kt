package cf.playhi.freezeyou.app

import android.app.Service
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.CallSuper
import cf.playhi.freezeyou.utils.Support.getLocalString
import java.util.*

abstract class FreezeYouBaseService : Service() {
    @CallSuper
    override fun attachBaseContext(newBase: Context) {
        val locale = getLocalString(newBase)
        val configuration = Configuration()
        configuration.setLocale(
            if ("Default" == locale) Locale.getDefault() else Locale.forLanguageTag(locale)
        )
        val context = newBase.createConfigurationContext(configuration)
        super.attachBaseContext(context)
    }

    override fun onCreate() {
        super.onCreate()
    }
}
