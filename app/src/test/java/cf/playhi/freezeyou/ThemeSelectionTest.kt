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
 * orange, green, pink, red - are gone: the menu stopped offering them, and then the styles, the
 * ThemeUtils branches and the coloured dot drawables were deleted. Below API 31 the values those
 * palettes carried for the light theme moved into AppTheme.Default and AppTheme.Default.Dialog,
 * where they were load-bearing (the "white" palette WAS the light theme), so nothing about the
 * light theme changed. An installation that once stored the value "blue" still carries it in
 * storage: there is no migration, and there does not need to be one - it falls into the else
 * branch of ThemeUtils and comes up as the light theme.
 *
 * Three things can go wrong silently, and each has its own check below:
 *   1. the two arrays - labels for the menu and the values written to storage - drift apart;
 *   2. a translation of the menu lists a different number of themes than the default one;
 *   3. a theme is offered but has no dialog counterpart, so the dialog screens of that theme fall
 *      back to something else by way of the `else` branch;
 *   4. a dialog theme is rebuilt on an outside Material3 parent on a higher API level and loses
 *      the app's alert-dialog overlay, which is how the action labels of every dialog turned the
 *      baseline Material3 purple on Android 12+ - the owner saw it on a device.
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

    private fun styleElement(file: File, name: String): Element? {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val styles = document.getElementsByTagName("style")
        for (i in 0 until styles.length) {
            val style = styles.item(i) as Element
            if (style.getAttribute("name") == name) return style
        }
        return null
    }

    private fun styleItems(style: Element): Map<String, String> {
        val items = style.getElementsByTagName("item")
        return (0 until items.length).associate { index ->
            val item = items.item(index) as Element
            item.getAttribute("name") to item.textContent.trim()
        }
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

    @Test
    fun darkAndBlackThemesRouteFrameworkTextColorsWithoutChangingLightOrPrimaryText() {
        val resDir = File(moduleDir(), "src/main/res")
        val valuesStyles = File(resDir, "values/styles.xml")
        val v26Styles = File(resDir, "values-v26/styles.xml")
        fun style(file: File, name: String): Element =
            styleElement(file, name) ?: throw AssertionError("Missing style $name in ${file.name}")

        val darkTextRouting = mapOf(
            "android:textColorPrimary" to "@color/app_dark_text_primary",
            "android:textColorSecondary" to "@color/app_dark_text_secondary",
            "android:textColorHint" to "@color/app_dark_text_secondary",
            "appListPackageTextColor" to "@color/app_dark_text_secondary",
        )
        val darkCore = style(valuesStyles, "Base.AppTheme.Dark.Core")
        val darkCoreItems = styleItems(darkCore)
        assertEquals("@color/appOnDarkSurfaceVariant", darkCoreItems["colorOnSurfaceVariant"])
        darkTextRouting.forEach { (attribute, value) ->
            assertEquals("Base.AppTheme.Dark.Core.$attribute", value, darkCoreItems[attribute])
        }

        val darkDialogCore = style(valuesStyles, "Base.AppTheme.Dark.Dialog.Core")
        val darkDialogItems = styleItems(darkDialogCore)
        assertEquals("@color/appOnDarkSurfaceVariant", darkDialogItems["colorOnSurfaceVariant"])
        listOf(
            "colorOnBackground",
            "colorOnSurface",
            "android:textColorPrimary",
            "android:textColorSecondary",
            "android:textColorHint",
            "appListPackageTextColor",
        ).forEach { attribute ->
            assertTrue("The dialog palette must not be changed through $attribute", attribute !in darkDialogItems)
        }

        val colorDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(resDir, "values/colors.xml"))
        val colorNodes = colorDocument.getElementsByTagName("color")
        fun color(name: String): String? = (0 until colorNodes.length)
            .map { colorNodes.item(it) as Element }
            .firstOrNull { it.getAttribute("name") == name }
            ?.textContent?.trim()
        assertEquals("#C4C4C6", color("appOnDarkSurfaceVariant"))
        assertEquals("#FFFFFF", color("appOnDarkSurface"))

        fun selectorColors(name: String): List<String> {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(resDir, "color/$name.xml"))
            val items = document.getElementsByTagName("item")
            return (0 until items.length)
                .map { (items.item(it) as Element).getAttribute("android:color") }
        }
        assertEquals(
            listOf("#61FFFFFF", "@color/appOnDarkSurface"),
            selectorColors("app_dark_text_primary")
        )
        assertEquals(
            listOf("#61C4C4C6", "@color/appOnDarkSurfaceVariant"),
            selectorColors("app_dark_text_secondary")
        )

        listOf(
            "layout/app_list_1.xml" to "@+id/pkgName",
            "layout/fufnm_list.xml" to "@+id/fufnml_pkgName",
            "layout/uaam_list.xml" to "@+id/uaaml_pkgName",
        ).forEach { (layoutName, packageId) ->
            val layoutDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File(resDir, layoutName))
            val textViews = layoutDocument.getElementsByTagName("TextView")
            val packageLabel = (0 until textViews.length)
                .map { textViews.item(it) as Element }
                .first { it.getAttribute("android:id") == packageId }
            assertEquals(
                "$layoutName package label",
                "?attr/appListPackageTextColor",
                packageLabel.getAttribute("android:textColor")
            )
        }

        // The light alias is the same framework primary color this TextView inherited before
        // the explicit dark-only package-label routing was added.
        listOf(
            "Base.AppTheme.Light.DarkActionBar.Core",
            "Base.AppTheme.Light.Core",
        ).forEach { name ->
            val items = styleItems(style(valuesStyles, name))
            assertEquals("?android:attr/textColorPrimary", items["appListPackageTextColor"])
            listOf(
                "colorOnBackground",
                "colorOnSurface",
                "colorOnSurfaceVariant",
                "android:textColorPrimary",
                "android:textColorSecondary",
                "android:textColorHint",
            ).forEach { attribute ->
                assertTrue("$name must not override $attribute", attribute !in items)
            }
        }
        val lightDialogItems =
            styleItems(style(valuesStyles, "Base.AppTheme.Light.Dialog.Core"))
        listOf(
            "appListPackageTextColor",
            "colorOnBackground",
            "colorOnSurface",
            "colorOnSurfaceVariant",
            "android:textColorPrimary",
            "android:textColorSecondary",
            "android:textColorHint",
        ).forEach { attribute ->
            assertTrue("Light dialog core must remain untouched through $attribute", attribute !in lightDialogItems)
        }

        // The black screen inherits the new screen-text routing through the dark core; dialog
        // leaves stay on their separate, unchanged dialog cores.
        assertEquals(
            "Base.AppTheme.Dark.Core",
            style(v26Styles, "Base.AppTheme.Dark").getAttribute("parent")
        )
        assertEquals(
            "Base.AppTheme.Dark",
            style(valuesStyles, "Base.AppTheme.Dark.Black").getAttribute("parent")
        )
        assertEquals(
            "Base.AppTheme.Dark.Dialog.Core",
            style(v26Styles, "Base.AppTheme.Dark.Dialog").getAttribute("parent")
        )
        assertEquals(
            "Base.AppTheme.Dark.Dialog",
            style(valuesStyles, "Base.V14.AppTheme.Dark.Dialog").getAttribute("parent")
        )
        assertEquals(
            "Base.V14.AppTheme.Dark.Dialog",
            style(valuesStyles, "Base.AppTheme.Dark.Dialog.Black").getAttribute("parent")
        )
    }

    @Test
    fun mainAndDialogActivitiesUsePopupSurfaceWithoutChangingSettingsScreen() {
        val stylesFile = File(moduleDir(), "src/main/res/values/styles.xml")
        fun style(name: String): Element =
            styleElement(stylesFile, name) ?: throw AssertionError("Missing style $name")

        val mainTheme = style("AppTheme.Default.Main")
        assertEquals("AppTheme.Default", mainTheme.getAttribute("parent"))
        assertEquals(
            "@style/ThemeOverlay.App.Dialog.Main.Light",
            styleItems(mainTheme)["materialAlertDialogTheme"]
        )

        val mainDialog = style("ThemeOverlay.App.Dialog.Main.Light")
        assertEquals("ThemeOverlay.App.Dialog.Light", mainDialog.getAttribute("parent"))
        assertEquals(
            mapOf("colorSurface" to "@color/appMainPopupLight"),
            styleItems(mainDialog)
        )

        val lightDialogCore = style("Base.AppTheme.Light.Dialog.Core")
        assertEquals(
            "@style/ThemeOverlay.App.Dialog.Main.Light",
            styleItems(lightDialogCore)["materialAlertDialogTheme"]
        )
        assertEquals(
            "Base.AppTheme.Light.Dialog",
            style("AppTheme.Default.Dialog").getAttribute("parent")
        )
        val v31StylesFile = File(moduleDir(), "src/main/res/values-v31/styles.xml")
        val v31DialogLeaf = styleElement(v31StylesFile, "AppTheme.Default.Dialog")
            ?: throw AssertionError("Missing values-v31/AppTheme.Default.Dialog")
        assertEquals(
            "@style/ThemeOverlay.App.Dialog.Main.Light",
            styleItems(v31DialogLeaf)["materialAlertDialogTheme"]
        )

        val settingsTheme = style("AppTheme.Default")
        assertEquals(
            "@style/ThemeOverlay.App.Dialog.Light",
            styleItems(settingsTheme)["materialAlertDialogTheme"]
        )
        assertEquals(
            "@color/appSurfaceLight",
            styleItems(style("ThemeOverlay.App.Dialog.Light"))["colorSurface"]
        )
    }

    /**
     * The coloured palettes are deleted, not merely hidden. A style that survives is a style
     * somebody will later fix, translate and test again - that is what the coloured themes cost
     * before they were removed - so their absence is worth a check of its own. The list of names
     * to look for is the same one the menu check uses: a palette may not exist as a screen style,
     * as a dialog style, or as a branch that maps a stored value to a style.
     */
    @Test
    fun theColouredPaletteStylesAreGone() {
        val resDir = File(moduleDir(), "src/main/res")
        val styles = styleNames(resDir)

        val offenders = styles.filter { name ->
            palettesThatShouldNotBeOffered.any { palette ->
                name.endsWith("." + palette.replaceFirstChar { it.uppercaseChar() })
            }
        }.sorted()
        assertTrue(
            "These styles of deleted palettes are back: ${offenders.joinToString(", ")}. The menu " +
                "offers three themes and no colour choice; a palette style that is not offered is " +
                "code that has to be kept correct for nobody.",
            offenders.isEmpty()
        )

        val themeUtils = File(moduleDir(), "src/main/java/cf/playhi/freezeyou/utils/ThemeUtils.kt")
            .readText()
        val strayBranches = palettesThatShouldNotBeOffered.filter { "\"$it\" ->" in themeUtils }
        assertTrue(
            "ThemeUtils still maps a deleted palette to something: ${strayBranches.joinToString(", ")}. " +
                "A stored value of a palette belongs in the else branch, which brings up the light theme.",
            strayBranches.isEmpty()
        )
    }

    /**
     * Dialogs are built with MaterialAlertDialogBuilder, and it takes the colours of its action
     * labels from `materialAlertDialogTheme`. A theme that replaces one of ours on a higher API
     * level - as AppTheme.Default.Dialog does in values-v31 - inherits from an outside Material3
     * theme instead, so unless it sets that attribute itself the labels fall back to the Material3
     * default overlay and come out baseline purple: not the app's accent, and not what the other
     * light dialog themes print. Only a declaration whose parent is an outside Material3 theme is
     * checked, because a declaration that inherits from one of ours already carries the attribute.
     */
    @Test
    fun aDialogThemeRebuiltOnAnOutsideParentKeepsTheAppsActionColours() {
        val resDir = File(moduleDir(), "src/main/res")
        val dialogStyles = expectedStyles.values.map { it.second }.toSet()

        val problems = mutableListOf<String>()
        var checked = 0

        resDir.walkTopDown()
            .filter { it.isFile && it.parentFile.name.startsWith("values") && it.extension == "xml" }
            .sortedBy { it.path }
            .forEach { file ->
                val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                val styles = document.getElementsByTagName("style")
                for (i in 0 until styles.length) {
                    val style = styles.item(i) as Element
                    val name = style.getAttribute("name")
                    val parent = style.getAttribute("parent")
                    if (name !in dialogStyles) continue
                    if (parent.isEmpty() || !parent.startsWith("Theme.Material3")) continue

                    checked++
                    val items = style.getElementsByTagName("item")
                    val itemNames = (0 until items.length)
                        .map { (items.item(it) as Element).getAttribute("name") }
                    if ("materialAlertDialogTheme" !in itemNames) {
                        problems += "$name in ${file.parentFile.name}/${file.name} inherits from " +
                            "$parent and does not set materialAlertDialogTheme"
                    }
                }
            }

        assertTrue(
            "A dialog theme rebuilt on an outside Material3 parent without materialAlertDialogTheme " +
                "- every dialog's action labels will be baseline purple instead of the app's accent:\n- " +
                problems.joinToString("\n- "),
            problems.isEmpty()
        )
        assertTrue(
            "No dialog theme rebuilding one of ours on an outside parent was found, so this test " +
                "checked nothing. Expected AppTheme.Default.Dialog in values-v31.",
            checked >= 1
        )
    }
}
