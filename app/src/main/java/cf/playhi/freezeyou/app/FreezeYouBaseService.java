package cf.playhi.freezeyou.app;

import android.app.Service;
import android.content.Context;
import android.content.res.Configuration;

import androidx.annotation.CallSuper;

import java.util.Locale;

import static cf.playhi.freezeyou.utils.Support.getLocalString;

public abstract class FreezeYouBaseService extends Service {

    @Override
    @CallSuper
    protected void attachBaseContext(Context newBase) {
        String locale = getLocalString(newBase);
        Configuration configuration = new Configuration();
        configuration.setLocale(
                "Default".equals(locale) ? Locale.getDefault() : Locale.forLanguageTag(locale)
        );
        Context context = newBase.createConfigurationContext(configuration);
        super.attachBaseContext(context);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}
