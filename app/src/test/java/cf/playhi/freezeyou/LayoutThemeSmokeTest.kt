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
import java.io.File

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
@Config(
    sdk = [23, 34],
    // The plain framework Application, not the one from the manifest. Robolectric would
    // otherwise build MainApplication, and its onCreate loads the native MMKV libraries through
    // ReLinker, opens the multi-process stores, starts Shizuku support and starts a service -
    // none of which exists on the JVM, and none of which inflating a layout needs. That failure
    // would also happen before any test body runs, so the per-layout try/catch could not report
    // it, and it would read as a crash of the whole class rather than of one combination.
    application = android.app.Application::class
)
class LayoutThemeSmokeTest {

    // The owner's order: light is the default, black and dark follow.
    private val themes = listOf(
        "light" to R.style.AppTheme_Default,
        "black" to R.style.AppTheme_Dark_Black,
        "dark" to R.style.AppTheme_Dark_Default,
    )

    /**
     * The layouts that belong to the application, taken from its own res/layout* directories.
     *
     * R.layout is not the same list: it also carries the layouts of every library (Material's
     * m3_alert_dialog, its clock period toggle, its time picker among them), which those widgets
     * inflate themselves, with their own arguments, and which are not all inflatable standalone
     * on an old API level. The first version of this test walked R.layout and failed on five
     * library layouts on API 23 while saying nothing about the application at all. Reading the
     * directory keeps the subject of the test what it claims to be - the screens we ship - and
     * stops a library update from turning the test red.
     */
    private fun applicationLayouts(): List<Pair<String, Int>> {
        val resDir = File("src/main/res")
        val dirs = resDir.listFiles { file -> file.isDirectory && file.name.startsWith("layout") }
            ?: emptyArray()
        val names = dirs
            .flatMap { dir -> dir.listFiles { file -> file.extension == "xml" }?.toList() ?: emptyList() }
            .map { it.nameWithoutExtension }
            .distinct()
            .sorted()
        return names.map { name -> name to R.layout::class.java.getField(name).getInt(null) }
    }

    @Test
    fun everyLayoutInflatesInEveryTheme() {
        val layouts = applicationLayouts()
        // A silent empty list would make this test pass while checking nothing, and a wrong
        // working directory would quietly shrink the matrix. Both have to be loud instead.
        assertTrue(
            "No layouts found under ${File("src/main/res").absolutePath}: the test's working " +
                "directory is not the module directory.",
            layouts.isNotEmpty()
        )
        assertTrue(
            "Only ${layouts.size} layouts found; the application has around forty. The list is " +
                "taken from res/layout* and something is cutting it short.",
            layouts.size >= 30
        )

        val failures = mutableListOf<String>()
        val application = RuntimeEnvironment.getApplication()
        val sdk = Build.VERSION.SDK_INT
        for ((layoutName, layoutId) in layouts) {
            for ((themeName, themeId) in themes) {
                try {
                    val themed = ContextThemeWrapper(application, themeId)
                    // attachToRoot is true on purpose: a <merge> layout refuses to inflate
                    // without it, and for every other layout it merely fills the throwaway root.
                    LayoutInflater.from(themed).inflate(layoutId, FrameLayout(themed), true)
                } catch (t: Throwable) {
                    // One line per combination, carrying the root cause: for a Material widget
                    // under a theme that is not a Material one the useful line is the deepest
                    // "requires your app theme to be ...", and for a missing resource it is the
                    // Resources.NotFoundException naming the id. The "FAIL:" prefix keeps these
                    // greppable in a CI log.
                    val root = generateSequence(t) { it.cause }.last()
                    val rootNote =
                        if (root !== t) " (root: ${root.javaClass.simpleName}: ${root.message})" else ""
                    failures += "FAIL: SDK $sdk, $layoutName, $themeName theme: " +
                        "${t.javaClass.simpleName}: ${t.message}$rootNote"
                }
            }
        }
        assertTrue(
            "Layouts that do not inflate:\n" + failures.joinToString("\n"),
            failures.isEmpty()
        )
    }
}
