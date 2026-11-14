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

    /**
     * Checks if a package name is well-formed according to Android conventions.
     * Package names must consist solely of ASCII letters, digits, underscores, and dots,
     * cannot be empty or exceed 256 characters, and must not contain directory traversal sequences.
     */
    public static boolean isValidPackageName(@Nullable String pkgName) {
        if (pkgName == null || pkgName.isEmpty() || pkgName.length() > 256) {
            return false;
        }
        if (pkgName.contains("..") || pkgName.contains("/") || pkgName.contains("\\")) {
            return false;
        }
        for (int i = 0; i < pkgName.length(); i++) {
            char c = pkgName.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '.' || c == '_')) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public static File getIconCacheFile(@NonNull Context context, @Nullable String pkgName) {
        return getSafeIconFile(new File(context.getCacheDir(), "icon"), pkgName);
    }

    @Nullable
    public static File getLegacyIconFilesDirFile(@NonNull Context context, @Nullable String pkgName) {
        return getSafeIconFile(new File(context.getFilesDir(), "icon"), pkgName);
    }

    @Nullable
    private static File getSafeIconFile(@NonNull File iconDir, @Nullable String pkgName) {
        if (!isValidPackageName(pkgName)) {
            return null;
        }
        File targetFile = new File(iconDir, pkgName + ".png");
        try {
            String canonicalDest = targetFile.getCanonicalPath();
            String canonicalDir = iconDir.getCanonicalPath();
            if (!canonicalDest.startsWith(canonicalDir + File.separator)) {
                return null;
            }
            return targetFile;
        } catch (Exception e) {
            return null;
        }
    }

    public static void deleteIconCache(@NonNull Context context, @Nullable String pkgName) {
        if (!isValidPackageName(pkgName)) {
            return;
        }
        File cacheFile = getIconCacheFile(context, pkgName);
        if (cacheFile != null && cacheFile.exists() && cacheFile.isFile()) {
            cacheFile.delete();
        }
        File filesFile = getLegacyIconFilesDirFile(context, pkgName);
        if (filesFile != null && filesFile.exists() && filesFile.isFile()) {
            filesFile.delete();
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
        File cacheFile = (saveIconCache && isValidPackageName(pkgName)) ? getIconCacheFile(context, pkgName) : null;
        if (cacheFile != null && cacheFile.exists()) {
            drawable = BitmapDrawable.createFromPath(cacheFile.getAbsolutePath());
            if (drawable == null || !hasIconSize(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight())) {
                cacheFile.delete();
                drawable = null;
            }
        }
        if (drawable == null) {
            if (applicationInfo != null) {
                try {
                    drawable = applicationInfo.loadIcon(context.getPackageManager());
                    if (cacheFile != null) {
                        writeIconToCache(cacheFile, drawable);
                    }
                } catch (Throwable t) {
                    android.util.Log.w("ApplicationIconUtils", "Failed to load icon from applicationInfo for " + pkgName, t);
                    drawable = null;
                }
            } else if (!"".equals(pkgName)) {
                try {
                    drawable = context.getPackageManager().getApplicationIcon(pkgName);
                    if (cacheFile != null) {
                        writeIconToCache(cacheFile, drawable);
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
    private static void writeIconToCache(@NonNull File cacheFile, @Nullable Drawable drawable) {
        if (drawable == null
                || !hasIconSize(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight())) {
            return;
        }
        Bitmap bitmap = getBitmapFromDrawable(drawable);
        if (bitmap == null || !hasIconSize(bitmap.getWidth(), bitmap.getHeight())) {
            return;
        }
        File parentDir = cacheFile.getParentFile();
        if (parentDir != null) {
            folderCheck(parentDir);
        }
        writeBitmapToFile(cacheFile, bitmap);
    }

    private static boolean hasIconSize(int width, int height) {
        return width >= MIN_ICON_SIZE && height >= MIN_ICON_SIZE;
    }

    private static void writeBitmapToFile(@NonNull File targetFile, @Nullable Bitmap b) {
        if (b == null) return;
        File parentDir = targetFile.getParentFile();
        if (parentDir == null) return;
        File tempFile = new File(parentDir, targetFile.getName() + ".tmp." + System.nanoTime());
        try {
            String canonicalTemp = tempFile.getCanonicalPath();
            String canonicalParent = parentDir.getCanonicalPath();
            if (!canonicalTemp.startsWith(canonicalParent + File.separator)) {
                return;
            }
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

    private static void folderCheck(@NonNull File dir) {
        try {
            if (dir.exists() && !dir.isDirectory()) {
                dir.delete();
            }
            if (!dir.exists()) {
                dir.mkdirs();
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
