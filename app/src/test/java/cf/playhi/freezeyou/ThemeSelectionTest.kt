package cf.playhi.freezeyou

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The menu offers three themes, and this test is where that agreement is written down.
 *
 * Light (called "Default" in the menu), dark and black. The coloured palettes - blue, yellow,
 * orange, green, pink, red - are settled as gone: they are not in the menu in any of the four
 * languages, they are not to be tested, and deleting them from the code is separate, low-priority
 * hygiene. The reason deletion is not a one-liner is worth knowing before anyone starts: below
 * API 31 the live light theme is defined as a child of AppTheme.Light.White, and its dialog form
 * as a child of AppTheme.Light.Dialog.White, so two of the "dead" styles are load-bearing. On top
 * of that an installation that once stored the value "blue" still carries it, and ThemeUtils still
 * maps it to a style - no migration ever rewrote it.
 *
 * Three things can go wrong silently, and each has its own check below:
 *   1. the two arrays - labels for the menu and the values written to storage - drift apart;
 *   2. a translation of the menu lists a different number of themes than the default one;
 *   3. a theme is offered but has no dialog counterpart, so the dialog screens of that theme fall
 *      back to something else by way of the `else` branch.
 */
class ThemeSelectionTest {

    /** The agreed set, in the order the menu shows them. */
    private val expectedValues = listOf("default", "black", "deepBlack")

    private val palettesThatShouldNotBeOffered =
        listOf("blue", "yellow", "orange", "green", "pink", "red", "white")

    /** value -> the style for a screen, and the style for a dialog of the same theme. */
    private val expectedStyles = mapOf(
        "default" to ("AppTheme.Default" to "AppTheme.Default.Dialog"),
        "black" to ("AppTheme.Dark.Default" to "AppTheme.Dark.Dialog.Default"),
        "deepBlack" to ("AppTheme.Dark.Black" to "AppTheme.Dark.Dialog.Black"),
    )

    /**
     * Gradle runs unit tests with the module directory as the working directory, but that is a
     * convention rather than a promise, so the module is found by looking for it.
     */
    private fun moduleDir(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "src/main/res/values/strings.xml").isFile) return dir
            if (File(dir, "app/src/main/res/values/strings.xml").isFile) return File(dir, "app")
            dir = dir.parentFile
        }
        throw AssertionError("Module directory not found from ${File("").absolutePath}")
    }

    /** The <item> texts of a <string-array>, or an empty list when the file has no such array. */
    private fun itemsOf(file: File, arrayName: String): List<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val arrays = document.getElementsByTagName("string-array")
        for (i in 0 until arrays.length) {
            val array = arrays.item(i) as Element
            if (array.getAttribute("name") != arrayName) continue
            val items = array.getElementsByTagName("item")
            return (0 until items.length).map { items.item(it).textContent.trim() }
        }
        return emptyList()
    }

    /**
     * Every style name defined in a values* directory, parsed as XML rather than matched by a
     * pattern: a commented-out style is not an element, so it does not count. The files carry
     * commented-out styles of the old AppCompat themes, and taking those for live ones would
     * make this test pass for the wrong reason.
     */
    private fun styleNames(resDir: File): Set<String> {
        val names = mutableSetOf<String>()
        resDir.walkTopDown()
            .filter { it.isFile && it.parentFile.name.startsWith("values") && it.extension == "xml" }
            .sortedBy { it.path }
            .forEach { file ->
                val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                val styles = document.getElementsByTagName("style")
                for (i in 0 until styles.length) {
                    names += (styles.item(i) as Element).getAttribute("name")
                }
            }
        return names
    }

    @Test
    fun theMenuOffersTheAgreedThreeThemes() {
        val resDir = File(moduleDir(), "src/main/res")
        val defaultStrings = File(resDir, "values/strings.xml")

        val values = itemsOf(defaultStrings, "uiStyleSelectionValues")
        assertEquals(
            "The theme menu no longer offers the agreed set. Light, dark and black are the whole " +
                "list; the coloured palettes are settled as gone (see the class comment).",
            expectedValues, values
        )
        palettesThatShouldNotBeOffered.forEach { palette ->
            assertTrue(
                "The value \"$palette\" is offered again, but that palette is settled as gone. " +
                    "If it is coming back on purpose, this test and the class comment are what " +
                    "has to be updated with the decision.",
                palette !in values
            )
        }
    }

    @Test
    fun everyLanguageListsAsManyThemesAsTheDefaultOne() {
        val resDir = File(moduleDir(), "src/main/res")
        val defaultCount = itemsOf(File(resDir, "values/strings.xml"), "uiStyleSelection").size

        val localeDirs = resDir.listFiles { file ->
            file.isDirectory && file.name.startsWith("values-")
        } ?: emptyArray()

        var checked = 0
        localeDirs.sortedBy { it.name }.forEach { dir ->
            val strings = File(dir, "strings.xml")
            if (!strings.isFile) return@forEach
            val labels = itemsOf(strings, "uiStyleSelection")
            if (labels.isEmpty()) return@forEach
            checked++
            assertEquals(
                "${dir.name} lists ${labels.size} themes, the default language lists $defaultCount. " +
                    "A ListPreference pairs the labels with the values by position, so a mismatch " +
                    "shows one theme and stores another.",
                defaultCount, labels.size
            )
        }
        assertTrue(
            "No translated theme menu was found at all, so this test checked nothing. " +
                "Expected values-ru-rRU, values-uk-rUA and values-zh-rCN.",
            checked >= 3
        )
    }

    @Test
    fun everyOfferedThemeHasAScreenStyleAndADialogStyle() {
        val resDir = File(moduleDir(), "src/main/res")
        val styles = styleNames(resDir)

        expectedStyles.forEach { (value, pair) ->
            val (screenStyle, dialogStyle) = pair
            assertTrue(
                "The theme \"$value\" is offered, but the style $screenStyle is not defined",
                screenStyle in styles
            )
            assertTrue(
                "The theme \"$value\" is offered, but the dialog style $dialogStyle is not " +
                    "defined; its dialog screens would then resolve a theme through the else branch.",
                dialogStyle in styles
            )
        }

        // ThemeUtils is what turns a stored value into a style. "default" deliberately has no
        // branch of its own: it is the else branch, which is also the fallback for anything
        // unrecognised - including a palette value stored by an older version.
        val themeUtils = File(moduleDir(), "src/main/java/cf/playhi/freezeyou/utils/ThemeUtils.kt")
            .readText()
        expectedValues.filter { it != "default" }.forEach { value ->
            assertTrue(
                "ThemeUtils does not map the offered value \"$value\" to a style, so choosing it " +
                    "would silently apply whatever the else branch holds.",
                "\"$value\" ->" in themeUtils
            )
        }
        assertTrue(
            "ThemeUtils no longer has an else branch in processSetTheme, so a stored value with " +
                "no branch (the default, or a palette from an older version) would apply no theme.",
            "else -> context.setTheme(" in themeUtils
        )
    }
}
