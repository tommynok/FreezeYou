package cf.playhi.freezeyou.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;

import cf.playhi.freezeyou.R;

import static cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.cacheApplicationsIcons;

public final class ApplicationIconUtils {

    /**
     * Convert Drawable to Bitmap.
     * References:
     * - http://www.cnblogs.com/zhou2016/p/6281678.html
     * - https://stackoverflow.com/a/10600736/10011687
     * <p>
     * Once migrated to Kotlin, DrawableKt.toBitmap() can be used,
     * but care must still be taken when intrinsic width/height == -1.
     *
     * @param drawable drawable
     * @return Bitmap
     */
    public static Bitmap getBitmapFromDrawable(Drawable drawable) {
        if (drawable == null) {
            return Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888);
        } else {
            if (drawable instanceof BitmapDrawable) {
                Bitmap bm = ((BitmapDrawable) drawable).getBitmap();
                if (bm != null) {
                    return bm;
                }
            }

            int width = drawable.getIntrinsicWidth(), height = drawable.getIntrinsicHeight();
            Rect oldBounds = drawable.copyBounds();
            Bitmap bitmap;
            if (drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0) {
                bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            } else {
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            }
            drawable.setBounds(0, 0, width, height);
            drawable.draw(new Canvas(bitmap));
            drawable.setBounds(oldBounds);
            return bitmap;
        }
    }

    public static Drawable getApplicationIcon(
            @NonNull Context context, @NonNull String pkgName,
            @Nullable ApplicationInfo applicationInfo, boolean resize
    ) {
        return getApplicationIcon(
                context, pkgName, applicationInfo, resize,
                cacheApplicationsIcons.getValue(context)
        );
    }

    public static Drawable getApplicationIcon(
            @NonNull Context context, @NonNull String pkgName,
            @Nullable ApplicationInfo applicationInfo, boolean resize, boolean saveIconCache
    ) {
        Drawable drawable = null;
        String path = context.getCacheDir() + "/icon/" + pkgName + ".png";
        if (saveIconCache && new File(path).exists()) {
            drawable = BitmapDrawable.createFromPath(path);
        } else if (applicationInfo != null) {
            drawable = applicationInfo.loadIcon(context.getPackageManager());
            if (saveIconCache) {
                folderCheck(context.getCacheDir() + "/icon");
                writeBitmapToFile(path, getBitmapFromDrawable(drawable));
            }
        } else if (!"".equals(pkgName)) {
            try {
                drawable = context.getPackageManager().getApplicationIcon(pkgName);
                if (saveIconCache) {
                    folderCheck(context.getCacheDir() + "/icon");
                    writeBitmapToFile(path, getBitmapFromDrawable(drawable));
                }
            } catch (PackageManager.NameNotFoundException e) {
                drawable = context.getResources().getDrawable(android.R.drawable.ic_menu_delete);
            } catch (Exception e) {
                drawable = context.getResources().getDrawable(android.R.drawable.sym_def_app_icon);
            }
        }
        if ((drawable == null) || (drawable.getIntrinsicWidth() <= 0) || (drawable.getIntrinsicHeight() <= 0)) {
            drawable = context.getResources().getDrawable(R.mipmap.ic_launcher_round);
        }
        if (resize) {
            Bitmap bitmap = getBitmapFromDrawable(drawable);
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            Matrix matrix = new Matrix();
            float scaleWidth = ((float) 72) / width;
            float scaleHeight = ((float) 72) / height;
            matrix.postScale(scaleWidth, scaleHeight);
            return new BitmapDrawable(Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true));
        } else {
            return drawable;
        }
    }

    private static void writeBitmapToFile(String filePath, Bitmap b) {
        try {
            File file = new File(filePath);
            FileOutputStream fos = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(fos);
            b.compress(Bitmap.CompressFormat.PNG, 100, bos);
            bos.flush();
            bos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void folderCheck(String path) {
        try {
            File file = new File(path);
            if (!file.isDirectory()) {
                file.delete();
            }
            if (!file.exists()) {
                file.mkdirs();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Convert bitmap to greyscale.
     * Reference: https://blog.csdn.net/xuwenneng/article/details/52634979
     *
     * @param bm Original bitmap
     * @return Greyscale bitmap
     */
    public static Bitmap getGrayBitmap(Bitmap bm) {
        Bitmap bitmap = Bitmap.createBitmap(bm.getWidth(), bm.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0); // 0 = greyscale, 1 = original
        ColorMatrixColorFilter cmcf = new ColorMatrixColorFilter(matrix);
        paint.setColorFilter(cmcf);
        canvas.drawBitmap(bm, 0, 0, paint);
        return bitmap;
    }

}
