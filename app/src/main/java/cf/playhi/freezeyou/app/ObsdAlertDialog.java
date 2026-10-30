package cf.playhi.freezeyou.app;

import android.content.Context;
import android.util.TypedValue;
import android.view.MotionEvent;

import androidx.appcompat.app.AlertDialog;

public class ObsdAlertDialog extends AlertDialog {
    private boolean isObsd;

    public ObsdAlertDialog(Context context) {
        super(context, resolveDialogTheme(context));
    }

    public ObsdAlertDialog(Context context, int themeResId) {
        super(context, themeResId == 0 ? resolveDialogTheme(context) : themeResId);
    }

    private static int resolveDialogTheme(Context context) {
        TypedValue outValue = new TypedValue();
        if (context != null && context.getTheme() != null
                && context.getTheme().resolveAttribute(
                        com.google.android.material.R.attr.materialAlertDialogTheme, outValue, true)) {
            return outValue.resourceId;
        }
        return 0;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        isObsd = (ev.getFlags() & MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0;
        return super.dispatchTouchEvent(ev);
    }

    public boolean isObsd() {
        return isObsd;
    }
}
