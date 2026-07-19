package cf.playhi.freezeyou.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import cf.playhi.freezeyou.R
import cf.playhi.freezeyou.storage.key.DefaultSharedPreferenceStorageBooleanKeys.cacheApplicationsIcons
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

object ApplicationIconUtils {
    /**
     * Drawable 转 Bitmap
     * 最初参考 http://www.cnblogs.com/zhou2016/p/6281678.html
     * 后续参考 https://stackoverflow.com/a/10600736/10011687
     *
     * 待切至 Kotlin 后可换用 DrawableKt.toBitmap()，但仍需注意 Intrinsic == -1 的情况
     *
     * @param drawable drawable
     * @return Bitmap
     */
    @JvmStatic
    fun getBitmapFromDrawable(drawable: Drawable?): Bitmap {
        return if (drawable == null) {
            Bitmap.createBitmap(144, 144, Bitmap.Config.ARGB_8888)
        } else {
            if (drawable is BitmapDrawable) {
                val bm = drawable.bitmap
                if (bm != null) {
                    return bm
                }
            }
            val width = drawable.intrinsicWidth
            val height = drawable.intrinsicHeight
            val oldBounds = drawable.copyBounds()
            val bitmap: Bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            } else {
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            }
            drawable.setBounds(0, 0, width, height)
            drawable.draw(Canvas(bitmap))
            drawable.setBounds(oldBounds)
            bitmap
        }
    }

    @JvmStatic
    fun getApplicationIcon(
        context: Context, pkgName: String,
        applicationInfo: ApplicationInfo?, resize: Boolean
    ): Drawable {
        return getApplicationIcon(
            context, pkgName, applicationInfo, resize,
            cacheApplicationsIcons.getValue(context)
        )
    }

    @JvmStatic
    fun getApplicationIcon(
        context: Context, pkgName: String,
        applicationInfo: ApplicationInfo?, resize: Boolean, saveIconCache: Boolean
    ): Drawable {
        var drawable: Drawable? = null
        val path = context.cacheDir.toString() + "/icon/" + pkgName + ".png"
        if (saveIconCache && File(path).exists()) {
            drawable = BitmapDrawable.createFromPath(path)
        } else if (applicationInfo != null) {
            drawable = applicationInfo.loadIcon(context.packageManager)
            if (saveIconCache) {
                folderCheck(context.cacheDir.toString() + "/icon")
                writeBitmapToFile(path, getBitmapFromDrawable(drawable))
            }
        } else if ("" != pkgName) {
            try {
                drawable = context.packageManager.getApplicationIcon(pkgName)
                if (saveIconCache) {
                    folderCheck(context.cacheDir.toString() + "/icon")
                    writeBitmapToFile(path, getBitmapFromDrawable(drawable))
                }
            } catch (e: PackageManager.NameNotFoundException) {
                drawable = context.resources.getDrawable(android.R.drawable.ic_menu_delete)
            } catch (e: Exception) {
                drawable = context.resources.getDrawable(android.R.drawable.sym_def_app_icon)
            }
        }
        if (drawable == null || drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            drawable = context.resources.getDrawable(R.mipmap.ic_launcher_round)
        }
        return if (resize) {
            val bitmap = getBitmapFromDrawable(drawable)
            val width = bitmap.width
            val height = bitmap.height
            val matrix = Matrix()
            val scaleWidth = 72.toFloat() / width
            val scaleHeight = 72.toFloat() / height
            matrix.postScale(scaleWidth, scaleHeight)
            BitmapDrawable(
                context.resources,
                Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
            )
        } else {
            drawable
        }
    }

    private fun writeBitmapToFile(filePath: String, b: Bitmap) {
        try {
            val file = File(filePath)
            val fos = FileOutputStream(file)
            val bos = BufferedOutputStream(fos)
            b.compress(Bitmap.CompressFormat.PNG, 100, bos)
            bos.flush()
            bos.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun folderCheck(path: String) {
        try {
            val file = File(path)
            if (!file.isDirectory) {
                file.delete()
            }
            if (!file.exists()) {
                file.mkdirs()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 参考：https://blog.csdn.net/xuwenneng/article/details/52634979
     * 对图片进行灰度化处理
     *
     * @param bm 原始图片
     * @return 灰度化图片
     */
    @JvmStatic
    fun getGrayBitmap(bm: Bitmap): Bitmap {
        val bitmap = Bitmap.createBitmap(bm.width, bm.height, Bitmap.Config.ARGB_8888)
        //创建画布
        val canvas = Canvas(bitmap)
        //创建画笔
        val paint = Paint()
        //创建颜色矩阵
        val matrix = ColorMatrix()
        //设置颜色矩阵的饱和度:0代表灰色,1表示原图
        matrix.setSaturation(0f)
        //颜色过滤器
        val cmcf = ColorMatrixColorFilter(matrix)
        //设置画笔颜色过滤器
        paint.colorFilter = cmcf
        //画图
        canvas.drawBitmap(bm, 0f, 0f, paint)
        return bitmap
    }
}
