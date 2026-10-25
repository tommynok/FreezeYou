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
     * What a drawable that reports no intrinsic size is drawn into, since there is no size of its
     * own to use: the size the list icons are scaled to anyway.
     */
    private static final int FALLBACK_ICON_SIZE = 72;

    /**
     * The smallest side an icon may have. A 1x1 image is the placeholder the no-size case used to
     * produce and cache, and once on disk it was read back as that app's icon forever, so it has
     * to be recognised as corrupt everywhere an icon is accepted.
     */
    private static final int MIN_ICON_SIZE = 2;

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
            if (!hasIconSize(width, height)) {
                width = FALLBACK_ICON_SIZE;
                height = FALLBACK_ICON_SIZE;
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
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
        File cacheFile = new File(path);
        if (saveIconCache && cacheFile.exists()) {
            drawable = BitmapDrawable.createFromPath(path);
            if (drawable == null || !hasIconSize(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight())) {
                cacheFile.delete();
                drawable = null;
            }
        }
        if (drawable == null) {
            if (applicationInfo != null) {
                try {
                    drawable = applicationInfo.loadIcon(context.getPackageManager());
                    if (saveIconCache) {
                        writeIconToCache(path, drawable);
                    }
                } catch (Throwable t) {
                    android.util.Log.w("ApplicationIconUtils", "Failed to load icon from applicationInfo for " + pkgName, t);
                    drawable = null;
                }
            } else if (!"".equals(pkgName)) {
                try {
                    drawable = context.getPackageManager().getApplicationIcon(pkgName);
                    if (saveIconCache) {
                        writeIconToCache(path, drawable);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    drawable = context.getResources().getDrawable(android.R.drawable.ic_menu_delete);
                } catch (Throwable t) {
                    android.util.Log.w("ApplicationIconUtils", "Failed to load icon for " + pkgName, t);
                    drawable = context.getResources().getDrawable(android.R.drawable.sym_def_app_icon);
                }
            }
        }
        if ((drawable == null) || !hasIconSize(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight())) {
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

    /**
     * Cache an icon, unless it is not one. A drawable with no size of its own is drawn as a
     * placeholder; a placeholder written to disk would be read back as that app's icon on every
     * later launch, so it stays out of the cache and is loaded again next time instead.
     */
    private static void writeIconToCache(String path, @Nullable Drawable drawable) {
        if (drawable == null
                || !hasIconSize(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight())) {
            return;
        }
        Bitmap bitmap = getBitmapFromDrawable(drawable);
        if (bitmap == null || !hasIconSize(bitmap.getWidth(), bitmap.getHeight())) {
            return;
        }
        folderCheck(new File(path).getParent());
        writeBitmapToFile(path, bitmap);
    }

    private static boolean hasIconSize(int width, int height) {
        return width >= MIN_ICON_SIZE && height >= MIN_ICON_SIZE;
    }

    private static void writeBitmapToFile(String filePath, Bitmap b) {
        if (b == null) return;
        File targetFile = new File(filePath);
        File tempFile = new File(filePath + ".tmp." + System.nanoTime());
        try {
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                b.compress(Bitmap.CompressFormat.PNG, 100, bos);
                bos.flush();
            }
            if (tempFile.exists()) {
                if (targetFile.exists()) {
                    targetFile.delete();
                }
                tempFile.renameTo(targetFile);
            }
        } catch (Exception e) {
            if (tempFile.exists()) {
                tempFile.delete();
            }
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
