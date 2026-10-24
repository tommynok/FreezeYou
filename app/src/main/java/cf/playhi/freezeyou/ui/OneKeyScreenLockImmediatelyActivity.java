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
        if (Intent.ACTION_CREATE_SHORTCUT.equals(getIntent().getAction())) {
            Intent shortcutIntent = new Intent(this, this.getClass());
            ShortcutTokenUtils.attachToken(shortcutIntent);
            Intent intent = new Intent();
            intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.oneKeyLockScreen));
            intent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this, R.drawable.screenlock));
            setResult(RESULT_OK, intent);
            finish();
            return;
        }
        if (!ShortcutTokenUtils.verifyToken(getIntent())) {
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
