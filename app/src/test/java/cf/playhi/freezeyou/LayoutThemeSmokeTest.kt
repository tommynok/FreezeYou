package cf.playhi.freezeyou

import android.os.Build
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Inflates every layout of the application once per theme family, on API 23 and on API 34.
 *
 * This is the failure the build cannot see. A theme that loses an attribute to a values-vNN
 * overlay, or a widget paired with a theme that does not define what the widget asks for,
 * compiles and packages cleanly: it breaks when the screen is opened, on a phone, weeks after
 * CI went green. Six builds of the design work went into hunting two such traps by hand; the
 * checks in tools/ look for them in the style files, and this test builds the views for real.
 *
 * The two API levels cover every overlay in the project. At 23 the platform takes values-v19,
 * values-v21, layout-v21 and layout-v23; at 34 it takes values-v31 as well - that is why the
 * second level is 34 and not the current compileSdk. Robolectric supports 23 to 37, so the
 * app's oldest range (API 16-20, and with it part of values-v19) cannot be covered here: those
 * levels stay with tools/check_theme_resolution.py and with a real phone.
 *
 * Every failure is collected instead of thrown on the first one, because a red run should list
 * all the broken combinations at once rather than one per run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 34])
class LayoutThemeSmokeTest {

    // The owner's order: light is the default, black and dark follow.
    private val themes = listOf(
        "light" to R.style.AppTheme_Default,
        "black" to R.style.AppTheme_Dark_Black,
        "dark" to R.style.AppTheme_Dark_Default,
    )

    @Test
    fun everyLayoutInflatesInEveryTheme() {
        val failures = mutableListOf<String>()
        val application = RuntimeEnvironment.getApplication()
        val sdk = Build.VERSION.SDK_INT
        for (field in R.layout::class.java.declaredFields) {
            val layoutId = field.getInt(null)
            for ((themeName, themeId) in themes) {
                try {
                    val themed = ContextThemeWrapper(application, themeId)
                    // attachToRoot is true on purpose: a <merge> layout refuses to inflate
                    // without it, and for every other layout it merely fills the throwaway root.
                    LayoutInflater.from(themed).inflate(layoutId, FrameLayout(themed), true)
                } catch (t: Throwable) {
                    failures += "SDK $sdk, ${field.name}, $themeName theme: " +
                        "${t.javaClass.name}: ${t.message}"
                }
            }
        }
        assertTrue(
            "Layouts that do not inflate:\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }
}
