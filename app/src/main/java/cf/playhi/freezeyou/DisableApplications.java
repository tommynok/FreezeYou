package cf.playhi.freezeyou;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import cf.playhi.freezeyou.app.FreezeYouBaseActivity;

import static cf.playhi.freezeyou.utils.DebugModeUtils.isDebugModeEnabled;
import static cf.playhi.freezeyou.utils.DevicePolicyManagerUtils.isDeviceOwner;
import static cf.playhi.freezeyou.utils.FUFUtils.oneKeyActionMRoot;
import static cf.playhi.freezeyou.utils.FUFUtils.oneKeyActionRootAsync;

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
public class DisableApplications extends FreezeYouBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent == null) {
            if (isDebugModeEnabled()) {
                Log.e("DebugModeLogcat", "Intent: null");
            }
            finish();
            return;
        }

        String[] packages = intent.getStringArrayExtra("packages");
        if (packages == null || packages.length == 0) {
            if (isDebugModeEnabled()) {
                Log.e("DebugModeLogcat", "Intent packages: null or empty");
            }
            finish();
            return;
        }

        if (isDebugModeEnabled()) {
            Log.e("DebugModeLogcat", "Intent toString:" + intent.toString());
            for (String p : packages) {
                Log.e("DebugModeLogcat", "Intent packages:" + p);
            }
        }

        setResult(Activity.RESULT_OK);
        if (Build.VERSION.SDK_INT >= 21 && isDeviceOwner(DisableApplications.this)) {
            oneKeyActionMRoot(DisableApplications.this, true, packages);
            finish();
        } else {
            oneKeyActionRootAsync(DisableApplications.this, true, packages, true, this::finish);
        }
    }
}
