package cf.playhi.freezeyou

import android.graphics.Rect
import android.graphics.drawable.InsetDrawable
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
import kotlin.math.round

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
 * shortfall is shared out by weight, and the button ends up narrower than it would like to be.
 * That was fine for the AppCompat coloured button this screen used before - 8dp of padding
 * around the text, 6dp of vertical inset - but the Material 3 button the design work swapped in
 * (1739ac4e) carries 24dp of horizontal padding, and the three dots stopped fitting.
 *
 * The test measures the inflated screen rather than looking at it: it is the answer to "you never
 * look at the screenshots", and to the fact that screenshots do not reach the sandbox at all. The
 * numbers are printed as "GEOM:" lines, which the CI step turns into annotations on both green
 * and red runs - annotations come back through the API on a machine where the log host is
 * blocked, so a fix can be confirmed by numbers instead of by a screenshot.
 *
 * One API level only: this geometry comes from our own styles and from the layout, not from a
 * version overlay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LauncherShortcutGeometryTest {

    private class Row(val name: String, val buttonId: Int, val fieldId: Int)

    private val rows = listOf(
        Row("application", R.id.lscaga_package_button, R.id.lscaga_package_editText),
        Row("target", R.id.lscaga_target_button, R.id.lscaga_target_editText),
    )

    /** One decimal place, because the point of printing these numbers is that they can be read. */
    private fun dp(px: Number, density: Float): Float = round(px.toFloat() / density * 10f) / 10f

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
            val insets = Rect()
            (button.background as? InsetDrawable)?.getInsets(insets)

            val where = row.name
            report += "GEOM: $where row: field ${field.width}x${field.height}px " +
                "(${dp(field.height, density)}dp tall), button ${button.width}x${button.height}px " +
                "(${dp(button.height, density)}dp tall, insets ${dp(insets.top, density)}/" +
                "${dp(insets.bottom, density)}dp), padding ${button.paddingLeft}/${button.paddingRight}px, " +
                "lines $lines, text ${dp(lineWidth, density)}dp in ${dp(contentWidth, density)}dp, " +
                "centre offset ${dp(centreOffset, density)}dp"

            // Real glyph metrics, not the stand-in a fake graphics mode returns: with zero-width
            // text every fit check below would pass while proving nothing.
            if (lineWidth < 1f) {
                problems += "the ellipsis in the $where row measured ${lineWidth}px wide, so the " +
                    "fit checks cannot mean anything (graphics mode or font metrics)"
            }
            if (lines != 1) {
                problems += "the $where row's ellipsis occupies $lines lines; in the original it " +
                    "is three dots in one row"
            }
            if (lines == 1 && contentWidth < lineWidth) {
                problems += "the $where row's dots do not fit: ${dp(lineWidth, density)}dp of " +
                    "text inside ${dp(contentWidth, density)}dp of content box, so they wrap"
            }
            if (dp(button.height, density) > 48.5f) {
                problems += "the $where row's button is ${dp(button.height, density)}dp tall; the " +
                    "original drew a 36dp shape inside a 48dp touch target"
            }
            if (abs(centreOffset) / density > 2f) {
                problems += "the $where row's button is centred ${dp(centreOffset, density)}dp " +
                    "away from the field's centre, so it does not sit level with the text on its left"
            }
        }

        println(report.joinToString("\n"))
        assertTrue(
            "The ellipsis buttons of the shortcut screen:\n- " + problems.joinToString("\n- "),
            problems.isEmpty()
        )
    }
}
