package cf.playhi.freezeyou.ui;

import android.content.Intent;
import android.os.Bundle;

import cf.playhi.freezeyou.R;
import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.utils.DevicePolicyManagerUtils;
import cf.playhi.freezeyou.utils.ShortcutTokenUtils;

public class OneKeyScreenLockImmediatelyActivity extends FreezeYouBaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ShortcutTokenUtils.verifyToken(getIntent(), ShortcutTokenUtils.ACTION_LOCK_SCREEN)) {
            finish();
            return;
        }
        new Thread(() -> {
            DevicePolicyManagerUtils.doLockScreen(OneKeyScreenLockImmediatelyActivity.this);
            runOnUiThread(OneKeyScreenLockImmediatelyActivity.this::finish);
        }, "LockShortcutThread").start();
    }

    @Override
    protected boolean activityNeedCheckAppLock() {
        return false;
    }

}
