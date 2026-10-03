package cf.playhi.freezeyou

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Measured geometry of the activity-shortcut screen (`lscaga_main`), where the "..." buttons sit
 * next to the fields.
 *
 * The owner described this screen against the original application: there the buttons are
 * compact, level with the text of the field on their left, and the ellipsis is three horizontal
 * dots with an offset of its own rather than something geometrically centred. In this branch the
 * buttons had become large and stretched down the screen, the ellipsis had stopped being
 * horizontal, and the vertical centring was off.
 *
 * The cause was arithmetic, not taste. In this row the field is `match_parent` with weight 2 and
 * the button is `wrap_content` with weight 0.8, so the row measures wider than it is, the
 * shortfall is shared out by weight, and the button ends up around 63dp wide. That was fine for
 * the AppCompat coloured button this screen used before - 8dp of padding around the text - but
 * the Material 3 button the design work swapped in carries 24dp of padding, leaving the three
 * dots barely no room: they wrapped onto a second line, which made the button tall and the
 * ellipsis stop being horizontal.
 *
 * This test measures the inflated screen instead of looking at it, so the next change that
 * quietly re-inflates a button is caught by numbers rather than by the owner's eye. It runs at
 * one API level: the geometry here comes from our own styles and from the layout, not from a
 * version overlay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherShortcutGeometryTest {

    private class Row(
        val name: String,
        val buttonId: Int,
        val fieldId: Int,
    )

    private val rows = listOf(
        Row("application", R.id.lscaga_package_button, R.id.lscaga_package_editText),
        Row("target", R.id.lscaga_target_button, R.id.lscaga_target_editText),
    )

    @Test
    fun theEllipsisButtonsStayCompactAndOnOneLine() {
        val report = mutableListOf<String>()
        val problems = mutableListOf<String>()

        val application = RuntimeEnvironment.getApplication()
        val themed = ContextThemeWrapper(application, R.style.AppTheme_Default)
        val root = LayoutInflater.from(themed).inflate(R.layout.lscaga_main, null) as ViewGroup
        val metrics = themed.resources.displayMetrics
        val density = metrics.density
        root.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, metrics.widthPixels, metrics.heightPixels)

        for (row in rows) {
            val button = root.findViewById<Button>(row.buttonId)
            val field = root.findViewById<EditText>(row.fieldId)

            val lines = button.layout?.lineCount ?: -1
            val lineWidth = if (lines >= 1) button.layout.getLineWidth(0) else 0f
            val contentWidth = (button.width - button.paddingLeft - button.paddingRight).toFloat()
            val centreOffset = (button.top + button.height / 2f) - (field.top + field.height / 2f)

            report += "GEOM: %s row: field %d x %d px (%.1fdp tall), button %d x %d px " +
                "(%.1fdp tall), lines %d, text %.1fdp in %.1fdp, centre offset %.1fdp".format(
                    row.name, field.width, field.height, field.height / density,
                    button.width, button.height, button.height / density,
                    lines, lineWidth / density, contentWidth / density, centreOffset / density
                )

            // Real glyph metrics, not the stand-in a fake graphics mode returns: with zero-width
            // text every fit check below would pass while proving nothing.
            if (lineWidth < 1f) {
                problems += "FAIL: ${row.name}: the ellipsis measured ${lineWidth}px wide, so the " +
                    "fit checks below cannot mean anything (graphics mode or font metrics)"
            }
            if (lines != 1) {
                problems += "FAIL: ${row.name}: the ellipsis occupies $lines lines; in the " +
                    "original it is three dots in one row"
            }
            if (lines == 1 && contentWidth < lineWidth) {
                problems += "FAIL: ${row.name}: the dots do not fit - ${lineWidth / density}dp of " +
                    "text inside ${contentWidth / density}dp of content box, so they wrap"
            }
            if (button.height / density > 48.5f) {
                problems += "FAIL: ${row.name}: the button is ${button.height / density}dp tall; " +
                    "the original drew a 36dp shape inside a 48dp touch target"
            }
            if (abs(centreOffset) / density > 2f) {
                problems += "FAIL: ${row.name}: the button's centre is ${centreOffset / density}dp " +
                    "off the field's centre, so it does not sit level with the text on its left"
            }
        }

        println(report.joinToString("\n"))
        assertTrue(
            "The ellipsis buttons of the shortcut screen:\n" + problems.joinToString("\n"),
            problems.isEmpty()
        )
    }
}
