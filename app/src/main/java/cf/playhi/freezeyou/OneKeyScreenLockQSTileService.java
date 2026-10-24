package cf.playhi.freezeyou;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

import cf.playhi.freezeyou.ui.OneKeyScreenLockImmediatelyActivity;
import cf.playhi.freezeyou.utils.ShortcutTokenUtils;

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
@TargetApi(Build.VERSION_CODES.N)
public class OneKeyScreenLockQSTileService extends TileService {
    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, OneKeyScreenLockImmediatelyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ShortcutTokenUtils.attachToken(intent, ShortcutTokenUtils.ACTION_LOCK_SCREEN);
        startActivity(intent);
    }
}
