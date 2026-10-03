package cf.playhi.freezeyou

import android.graphics.Color
import android.os.Build
import android.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The status bar and its icons must be readable together: a dark bar needs light icons, a light
 * bar needs dark ones. Neither the build nor a screenshot of one theme catches the mismatch -
 * the owner found it by holding the phone next to the original application, where the icons were
 * white on the same purple bar and ours were black on it.
 *
 * The polarity lives in android:windowLightStatusBar and the colour in android:statusBarColor;
 * the colour is often not set by us at all, but arrives from Material 3 through
 * colorPrimaryDark = colorPrimary, which is why it changed to purple when the design work moved
 * the light themes onto Material 3 parents.
 *
 * Six configurations: the three themes the application offers - light (the one called "Default"
 * in the menu), dark and black - and the dialog form of each, because the dialog screens call
 * processSetTheme(this, true) and run under AppTheme.*.Dialog. The coloured palettes (blue,
 * pink, ...) that used to be in that menu are not here: they are gone from it, and a test that
 * keeps covering styles nobody can select only keeps dead code alive. Deleting them is separate,
 * low-priority work; until then they inherit the fixes that live in the shared light base.
 *
 * Both API levels are checked because the same theme name resolves differently: at 23 the light
 * default is an AppCompat-era style with a light grey bar, at 34 it is the Material 3 one with
 * the purple bar, so the two need opposite icon colours.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 34], application = android.app.Application::class)
class StatusBarContrastTest {

    private val themes = linkedMapOf(
        "light (default)" to R.style.AppTheme_Default,
        "dark" to R.style.AppTheme_Dark_Default,
        "black" to R.style.AppTheme_Dark_Black,
        "light, dialog" to R.style.AppTheme_Default_Dialog,
        "dark, dialog" to R.style.AppTheme_Dark_Dialog_Default,
        "black, dialog" to R.style.AppTheme_Dark_Dialog_Black,
    )

    @Test
    fun theStatusBarAndItsIconsAgree() {
        val application = RuntimeEnvironment.getApplication()
        val sdk = Build.VERSION.SDK_INT
        val report = mutableListOf<String>()
        val problems = mutableListOf<String>()
        var checked = 0

        for ((name, styleId) in themes) {
            val themed = ContextThemeWrapper(application, styleId)
            val attributes = themed.obtainStyledAttributes(
                intArrayOf(android.R.attr.statusBarColor, android.R.attr.windowLightStatusBar)
            )
            val colour = attributes.getColor(0, Color.TRANSPARENT)
            val darkIcons = attributes.getBoolean(1, false)
            attributes.recycle()

            if (Color.alpha(colour) == 0) {
                report += "  SDK $sdk, $name: the bar has no colour of its own (transparent), skipped"
                continue
            }

            checked++
            val luminance = ColorUtils.calculateLuminance(colour)
            val barIsLight = luminance > 0.5
            report += "  SDK $sdk, %-22s bar #%08X luminance %.3f, dark icons %s".format(
                name, colour, luminance, darkIcons
            )
            if (darkIcons != barIsLight) {
                problems += "FAIL: SDK $sdk, $name: the bar is %s (#%08X, luminance %.3f) " +
                    "but the icons are %s, which is what makes them hard to read".format(
                        if (barIsLight) "light" else "dark", colour, luminance,
                        if (darkIcons) "dark" else "light"
                    )
            }
        }

        println(report.joinToString("\n"))
        // A resolution that answered "no colour" for everything would let this test pass while
        // checking nothing at all, which is the failure mode a guard test must not have.
        assertTrue(
            "Only $checked of ${themes.size} themes reported a status bar colour, so this test " +
                "checked almost nothing. Reported:\n" + report.joinToString("\n"),
            checked >= themes.size - 1
        )
        assertTrue(
            "The status bar and its icons disagree:\n" + problems.joinToString("\n"),
            problems.isEmpty()
        )
    }
}
