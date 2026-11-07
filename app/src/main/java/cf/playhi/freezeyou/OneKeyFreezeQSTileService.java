package cf.playhi.freezeyou;

import android.annotation.TargetApi;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import cf.playhi.freezeyou.service.OneKeyFreezeService;
import cf.playhi.freezeyou.utils.ServiceUtils;

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
@TargetApi(Build.VERSION_CODES.N)
public class OneKeyFreezeQSTileService extends TileService {
    // The manifest icon only reaches tiles added from now on: SystemUI keeps the icon it cached
    // for a tile already on the shelf. Repainting it on every listen refreshes those tiles without
    // asking the user to remove and re-add them.
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
        ServiceUtils.startService(
                this,
                new Intent(getApplicationContext(), OneKeyFreezeService.class)
        );
    }
}
