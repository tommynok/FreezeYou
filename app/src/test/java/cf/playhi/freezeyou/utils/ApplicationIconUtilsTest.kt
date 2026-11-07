package cf.playhi.freezeyou.utils

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The icon cache is a file per package under the app's cache directory, and it is read before the
 * package manager is ever asked. That makes the cache self-perpetuating: whatever lands in it is
 * what every later launch shows, with no second chance to notice that it is wrong.
 *
 * Some drawables report no intrinsic size at all - the class comment of the code under test warns
 * about exactly that. Such a drawable used to be drawn into a 1x1 bitmap, and the 1x1 bitmap used
 * to be written to the cache, where the size check of the time (`<= 0`) accepted it as a valid
 * icon. The row then showed a single pixel for that app forever, and clearing the app's cache was
 * the only way out.
 *
 * Real graphics mode, because the bug lives in the file: the placeholder has to be compressed to
 * a real PNG and read back from it for any of this to mean anything.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ApplicationIconUtilsTest {

    private val pkgName = "com.looker.droidify"
    private lateinit var context: Application

    @Before
    fun forgetAnyIconLeftOverFromAnotherTest() {
        context = RuntimeEnvironment.getApplication()
        iconCacheFile(pkgName).delete()
    }

    @Test
    fun `a drawable that reports no size is still drawn instead of being reduced to one pixel`() {
        val color = 0xFF336699.toInt()

        val bitmap = ApplicationIconUtils.getBitmapFromDrawable(ColorDrawable(color))

        assertTrue(
            "a drawable without an intrinsic size produced a ${bitmap.width}x${bitmap.height} bitmap",
            bitmap.width > 1 && bitmap.height > 1
        )
        assertEquals(color, bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
    }

    @Test
    fun `an icon that reports no size is never written to the cache`() {
        val icon = iconFor(pkgName, installedAs(ColorDrawable(Color.RED)))

        assertFalse(
            "a placeholder was cached as ${iconCacheFile(pkgName)}",
            iconCacheFile(pkgName).exists()
        )
        // Nothing usable came out of the package, so the row falls back to the app's own icon
        // rather than showing what the drawable happened to report.
        assertTrue(icon.intrinsicWidth > 1 && icon.intrinsicHeight > 1)
    }

    @Test
    fun `a one pixel file in the cache is thrown away rather than shown`() {
        val gone = "com.example.uninstalled"
        saveToCache(gone, bitmapOf(1, Color.TRANSPARENT))

        // A package that is no longer installed, so nothing can write the file back: what is left
        // on disk afterwards is exactly what the cache check decided about it.
        val icon = iconFor(gone, null)

        assertFalse("the one pixel file survived at ${iconCacheFile(gone)}", iconCacheFile(gone).exists())
        assertTrue(icon.intrinsicWidth > 1 && icon.intrinsicHeight > 1)
    }

    @Test
    fun `a real icon is cached, and the cache is what the next launch reads`() {
        iconFor(pkgName, installedAs(BitmapDrawable(context.resources, bitmapOf(48, Color.BLUE))))

        assertTrue(iconCacheFile(pkgName).exists())

        // The second call is handed a different icon on purpose: if the size of the first one comes
        // back, the row was drawn from the file and not from the package.
        val cached = iconFor(pkgName, installedAs(BitmapDrawable(context.resources, bitmapOf(64, Color.GREEN))))

        assertEquals(48, cached.intrinsicWidth)
        assertEquals(48, cached.intrinsicHeight)
    }

    private fun iconFor(pkgName: String, applicationInfo: ApplicationInfo?): Drawable =
        ApplicationIconUtils.getApplicationIcon(context, pkgName, applicationInfo, false, true)

    /**
     * Stands in for an installed package. The icon is passed in rather than taken from resources,
     * because the drawable a package reports is what varies between devices - that variation is the
     * bug, and a test cannot reproduce it by reading this app's own manifest.
     */
    private fun installedAs(icon: Drawable): ApplicationInfo = object : ApplicationInfo() {
        override fun loadIcon(pm: PackageManager): Drawable = icon
    }

    private fun iconCacheFile(pkgName: String) = File(context.cacheDir, "icon/$pkgName.png")

    private fun saveToCache(pkgName: String, bitmap: Bitmap) {
        val file = iconCacheFile(pkgName)
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun bitmapOf(size: Int, color: Int): Bitmap =
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
}
