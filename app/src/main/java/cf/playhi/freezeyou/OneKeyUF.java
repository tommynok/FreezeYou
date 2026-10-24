package cf.playhi.freezeyou;

import android.content.Intent;
import android.os.Bundle;

import cf.playhi.freezeyou.app.FreezeYouBaseActivity;
import cf.playhi.freezeyou.service.OneKeyUFService;
import cf.playhi.freezeyou.utils.ServiceUtils;
import cf.playhi.freezeyou.utils.ShortcutTokenUtils;

// Needs to be retained for compatibility
// with old FreezeYou structures and settings.
public class OneKeyUF extends FreezeYouBaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ShortcutTokenUtils.verifyToken(getIntent(), ShortcutTokenUtils.ACTION_ONE_KEY_UF)) {
            finish();
            return;
        }
        ServiceUtils.startService(this, new Intent(getApplicationContext(), OneKeyUFService.class));
        finish();
    }
}
