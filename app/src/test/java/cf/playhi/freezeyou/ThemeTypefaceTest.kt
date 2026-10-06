package cf.playhi.freezeyou

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The app-wide typeface is one family per API level, and from API 26 up it is the bundled one.
 *
 * The font arrived as an experiment by another agent (see the ROADMAP ledger) and it arrived in two
 * pieces: the fifteen text appearances were repointed at the bundled variable font in values-v26,
 * while the theme-level `fontFamily` stayed the system string in values/. A view takes the theme's
 * font only when nothing above it in the precedence chain sets one, so the result was two typefaces
 * in one screen - the ellipsis button in the bundled cut, the field beside it in the system's - and
 * on Android 12+ a third, because the light dialog theme there is rebuilt on an outside Material3
 * parent and inherited no font of ours at all: every light dialog was set in the system regular.
 *
 * The fix keeps the font in a leaf style per theme base, so values-v26 can repoint just the font: a
 * same-named style in a version folder replaces the base one entirely, which is the trap that cost
 * this fork its Material3 attributes once already (the file that carries them says so).
 *
 * What is checked is the property, not the shape of the files: resolve the theme-level font of every
 * theme the app applies, at API 16 and at API 26, and it must be a single value each time - the
 * system string below 26, the bundled family from 26 up.
 */
class ThemeTypefaceTest {

    /** The themes the app applies, as [ThemeUtils.processSetTheme] names them: screen, then dialog. */
    private val appliedThemes = listOf(
        "AppTheme.Default" to "AppTheme.Default.Dialog",
        "AppTheme.Dark.Default" to "AppTheme.Dark.Dialog.Default",
        "AppTheme.Dark.Black" to "AppTheme.Dark.Dialog.Black",
    )

    private val bundledFont = "@font/fira_condensed"
    private val systemFont = "sans-serif-condensed"

    /**
     * The one theme base that is deliberately not given a leaf of its own. values-v19 and values-v21
     * redefine it on an outside parent, so from API 19 up the most specific definition of that name
     * wins and a values-v26 leaf here would take the theme back over on 26+, handing its eight
     * activities their Material3 attributes. That is a fix, but a different one, and it has to be
     * made on purpose - the comment where the theme is declared says the same.
     */
    private val deliberatelyWithoutALeaf = setOf("Base.AppTheme.Translucent.NoTitleBar")

    private fun moduleDir(): File {
        var dir = File("").absoluteFile
        while (!File(dir, "app/src/main/res").isDirectory) {
            dir = dir.parentFile ?: error("app/src/main/res not found above ${File("").absoluteFile}")
        }
        return File(dir, "app")
    }

    /** A style as it is declared: its parent, and its own items only. */
    private data class Declared(val parent: String?, val items: Map<String, String>)

    /**
     * Every style in every values* folder, keyed by folder then by name. The files are parsed as XML
     * rather than matched by a pattern, because they carry commented-out styles and a pattern would
     * count those. Each folder is read in isolation and then merged the way the resource compiler
     * does: the most specific version folder wins for a given name.
     */
    private fun declaredStyles(): Map<String, Map<String, Declared>> {
        val out = mutableMapOf<String, Map<String, Declared>>()
        val resDir = File(moduleDir(), "src/main/res")
        resDir.listFiles { file -> file.isDirectory && file.name.startsWith("values") }!!
            .sortedBy { it.name }
            .forEach { dir ->
                val styles = mutableMapOf<String, Declared>()
                dir.listFiles { file -> file.extension == "xml" }!!
                    .sortedBy { it.name }
                    .forEach { file ->
                        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                        val nodes = document.getElementsByTagName("style")
                        for (i in 0 until nodes.length) {
                            val style = nodes.item(i) as Element
                            val name = style.getAttribute("name")
                            if (name.isEmpty()) continue
                            val items = mutableMapOf<String, String>()
                            val itemNodes = style.getElementsByTagName("item")
                            for (j in 0 until itemNodes.length) {
                                val item = itemNodes.item(j) as Element
                                items[item.getAttribute("name")] = item.textContent.trim()
                            }
                            val parent = style.getAttribute("parent")
                            styles[name] = Declared(if (parent.isEmpty()) null else parent, items)
                        }
                    }
                out[dir.name] = styles
            }
        return out
    }

    /** The folders that apply at [api], most specific last: values, then values-vNN for NN <= api. */
    private fun foldersAt(
        declared: Map<String, Map<String, Declared>>,
        api: Int,
    ): List<String> {
        val versioned = Regex("values-v(\\d+)")
        return declared.keys
            .filter { folder ->
                folder == "values" ||
                    versioned.matchEntire(folder)?.groupValues?.get(1)?.toInt()?.let { it <= api } == true
            }
            .sortedBy { folder ->
                if (folder == "values") 0 else versioned.matchEntire(folder)!!.groupValues[1].toInt()
            }
    }

    /** The folder whose declaration of [name] wins at [api], or null when no applicable folder has it. */
    private fun folderWith(
        declared: Map<String, Map<String, Declared>>,
        folders: List<String>,
        name: String?,
    ): String? {
        if (name == null) return null
        return folders.lastOrNull { declared[it]?.containsKey(name) == true }
    }

    /**
     * The theme-level font of a style at [api], walking the parent chain the way the resource
     * compiler does. The nearest declaration wins, so a leaf that only repoints the font is enough.
     */
    private fun fontAt(declared: Map<String, Map<String, Declared>>, api: Int, name: String): String? {
        val folders = foldersAt(declared, api)
        var current = name
        var folder = folderWith(declared, folders, current)
        val seen = mutableSetOf<String>()
        var font: String? = null
        while (folder != null && seen.add(current)) {
            val declaredStyle = declared[folder]!![current]!!
            font = declaredStyle.items["android:fontFamily"] ?: font
            current = declaredStyle.parent ?: break
            folder = folderWith(declared, folders, current)
        }
        return font
    }

    @Test
    fun everyThemeTheAppAppliesHasOneTypefacePerApiLevel() {
        val declared = declaredStyles()
        val problems = mutableListOf<String>()

        for ((screen, dialog) in appliedThemes) {
            for ((label, name) in listOf("screen" to screen, "dialog" to dialog)) {
                val below26 = fontAt(declared, 16, name)
                val from26 = fontAt(declared, 26, name)
                if (below26 != systemFont) {
                    problems += "$name ($label) resolves to $below26 below API 26, expected $systemFont"
                }
                if (from26 != bundledFont) {
                    problems += "$name ($label) resolves to $from26 from API 26 up, expected $bundledFont"
                }
            }
        }

        assertTrue(
            "The theme-level typeface is not one family per API level:\n- " +
                problems.joinToString("\n- ") +
                "\nTwo values in one theme is the mixed-font bug: the text that goes through a text " +
                "appearance and the text that does not end up in different typefaces on one screen.",
            problems.isEmpty(),
        )
    }

    @Test
    fun theFontLivesInLeafStylesSoAVersionFolderCanRepointIt() {
        val declared = declaredStyles()
        val values = declared["values"]!!
        val v26 = declared["values-v26"]!!
        val problems = mutableListOf<String>()

        val carriers = values.filter { (name, style) ->
            name.startsWith("Base.AppTheme") &&
                style.items["android:fontFamily"] == systemFont &&
                name !in deliberatelyWithoutALeaf
        }

        assertTrue(
            "No theme base in values/ sets the system typeface any more, so this test checked " +
                "nothing. The font is supposed to live in leaf styles, one per theme base.",
            carriers.isNotEmpty(),
        )
        carriers.forEach { (name, style) ->
            val parent = style.parent
            if (parent == null || !parent.endsWith(".Core")) {
                problems += "$name sets the font inline instead of inheriting a .Core theme"
            }
            val fontItems = style.items.keys.count { it == "fontFamily" || it == "android:fontFamily" }
            if (fontItems != 2) {
                problems += "$name carries $fontItems font items, expected the two it takes to cover " +
                    "both the framework and the AppCompat attribute"
            }
            val core = parent?.let { values[it] }
            if (core != null && core.items.keys.any { "fontFamily" in it }) {
                problems += "$parent sets a font of its own, so the leaf is no longer the only source"
            }
            val leaf = v26[name]
            if (leaf == null) {
                problems += "$name is not redeclared in values-v26, so API 26+ keeps the system font"
            } else if (leaf.items["android:fontFamily"] != bundledFont) {
                problems += "$name in values-v26 points at ${leaf.items["android:fontFamily"]}"
            }
        }

        // The exclusion above has to stay a decision, not a hole: those bases must still exist, still
        // carry the system font, and still be the ones the exclusion names.
        deliberatelyWithoutALeaf.forEach { name ->
            val style = values[name]
            if (style == null || style.items["android:fontFamily"] != systemFont) {
                problems += "$name is excluded from the leaf rule but no longer carries the system " +
                    "font in values/, so the exclusion no longer describes anything"
            }
        }

        assertTrue(
            "The typeface leaves are not shaped the way the fix needs:\n- " +
                problems.joinToString("\n- "),
            problems.isEmpty(),
        )
    }

    @Test
    fun theLightDialogThemeKeepsItsOwnTypefaceOnTwelveUp() {
        val declared = declaredStyles()
        val v31 = declared["values-v31"]!!
        val dialog = v31["AppTheme.Default.Dialog"]

        assertTrue(
            "AppTheme.Default.Dialog is no longer rebuilt in values-v31. It is the one light dialog " +
                "theme whose parent is an outside Material3 theme, so it is the one that has to carry " +
                "the typeface itself - without it every light dialog on Android 12+ is set in the " +
                "system regular while the screen behind it is condensed.",
            dialog != null,
        )
        assertEquals(
            "AppTheme.Default.Dialog in values-v31 does not set the bundled family itself.",
            bundledFont,
            dialog!!.items["android:fontFamily"],
        )
    }
}
