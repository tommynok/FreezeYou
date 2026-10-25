package cf.playhi.freezeyou;

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import cf.playhi.freezeyou.ui.OneKeyScreenLockImmediatelyActivity;
import cf.playhi.freezeyou.utils.ShortcutTokenUtils;

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
@TargetApi(Build.VERSION_CODES.N)
public class OneKeyScreenLockQSTileService extends TileService {
    // Repaints tiles already on the shelf, which keep the icon SystemUI cached for them.
    @Override
    public void onStartListening() {
        super.onStartListening();
        Tile tile = getQsTile();
        if (tile != null) {
            tile.setIcon(Icon.createWithResource(this, R.drawable.ic_qs_snowflake));
            tile.updateTile();
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, OneKeyScreenLockImmediatelyActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ShortcutTokenUtils.attachToken(intent, ShortcutTokenUtils.ACTION_LOCK_SCREEN);
        startActivity(intent);
    }
}
