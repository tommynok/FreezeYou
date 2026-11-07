package cf.playhi.freezeyou.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.Window;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import cf.playhi.freezeyou.R;

public class ObsdAlertDialog extends AlertDialog {
    /** Material 3 dialog corners, the ShapeAppearance.Material3.Corner.ExtraLarge radius. */
    private static final float CORNER_RADIUS_DP = 28f;

    /** The transparent margin AppCompat keeps around the panel for the shadow to fall into. */
    private static final float WINDOW_INSET_DP = 16f;

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
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyMaterialSurface();
    }

    /**
     * A dialog paints its window from the theme's android:windowBackground, which for a dialog
     * theme is AppCompat's flat white rectangle; MaterialAlertDialogBuilder is what normally
     * replaces it with the rounded surface, and it does that once per dialog it builds. The
     * installer instead keeps one dialog and rewrites it through its four phases, so this paints
     * the same surface: Material 3 corners filled with ?attr/colorSurface, which is the lilac
     * main-popup tone on the light theme and the palette's own container colour on the dark ones.
     *
     * Runs after super, which is where AppCompat installs its content and reads the theme's
     * window background; anything set earlier would be overwritten by that read.
     */
    private void applyMaterialSurface() {
        Window window = getWindow();
        if (window == null) {
            return;
        }
        float density = getContext().getResources().getDisplayMetrics().density;
        MaterialShapeDrawable surface = new MaterialShapeDrawable(
                ShapeAppearanceModel.builder()
                        .setAllCornerSizes(CORNER_RADIUS_DP * density)
                        .build()
        );
        surface.setFillColor(ColorStateList.valueOf(resolveSurfaceColor()));
        window.setBackgroundDrawable(new InsetDrawable(surface, Math.round(WINDOW_INSET_DP * density)));
    }

    private int resolveSurfaceColor() {
        TypedValue outValue = new TypedValue();
        Context context = getContext();
        if (context.getTheme() != null && context.getTheme().resolveAttribute(
                com.google.android.material.R.attr.colorSurface, outValue, true)) {
            // A @color reference resolves through the palette; a literal value is already the colour.
            return outValue.resourceId != 0
                    ? ContextCompat.getColor(context, outValue.resourceId)
                    : outValue.data;
        }
        return ContextCompat.getColor(context, R.color.appMainPopupLight);
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
