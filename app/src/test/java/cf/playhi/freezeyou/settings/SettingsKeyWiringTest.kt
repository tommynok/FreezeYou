package cf.playhi.freezeyou.settings

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A settings row stores its value in SharedPreferences under its `app:key`. SettingsUtils then
 * looks that key up **by name** among the storage enums in `storage/key` and, on a match, copies the
 * value into MMKV, which is where the rest of the application reads it. No match means the lookup
 * returns null and the change is dropped without a word: the switch moves and the setting does
 * nothing.
 *
 * That is not a hypothetical. `DebugModeEnabled` sat in spr_advance.xml against an enum constant
 * called `debugModeEnabled`, so debug mode could not be turned on at all, and it took an owner
 * saying "I enabled it" against a log that showed otherwise to find out.
 *
 * The enum names are read from source rather than by reflection on purpose: loading those classes
 * drags in Android types and the MMKV storage, none of which a plain JVM test should need in order
 * to compare two sets of names.
 */
class SettingsKeyWiringTest {

    /** Rows that hold a value. A plain `<Preference>` opens a screen or runs an action. */
    private val valueTags = setOf(
        "CheckBoxPreference",
        "SwitchPreference",
        "SwitchPreferenceCompat",
        "ListPreference",
        "EditTextPreference",
        "MultiSelectListPreference",
        "SeekBarPreference"
    )

    private val appNamespace = "http://schemas.android.com/apk/res-auto"
    private val androidNamespace = "http://schemas.android.com/apk/res/android"

    /**
     * Gradle runs unit tests with the module directory as the working directory, but that is a
     * convention rather than a promise, so the module is found by looking for it.
     */
    private fun moduleDir(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "src/main/res/xml").isDirectory) return dir
            if (File(dir, "app/src/main/res/xml").isDirectory) return File(dir, "app")
            dir = dir.parentFile
        }
        throw AssertionError("cannot locate the module directory from ${File("").absolutePath}")
    }

    private fun storageKeyNames(module: File): Set<String> {
        val keyDir = File(module, "src/main/java/cf/playhi/freezeyou/storage/key")
        assertTrue("storage key package not found at $keyDir", keyDir.isDirectory)
        val sources = keyDir.listFiles { file: File ->
            file.name.startsWith("Default") && file.name.endsWith("Keys.kt")
        } ?: emptyArray()
        // An enum constant is declared at four spaces of indentation followed by its body.
        val constant = Regex("""^ {4}(\w+)\s*\{""", RegexOption.MULTILINE)
        val names = mutableSetOf<String>()
        for (source in sources) {
            for (match in constant.findAll(source.readText())) {
                names += match.groupValues[1]
            }
        }
        assertTrue("no storage keys parsed — has the storage package moved?", names.isNotEmpty())
        return names
    }

    /** Every value-holding row across the settings screens, as file name to `app:key`. */
    private fun settingsRows(module: File): List<Pair<String, String?>> {
        val xmlDir = File(module, "src/main/res/xml")
        val files = (xmlDir.listFiles { file: File ->
            file.name.startsWith("spr") && file.name.endsWith(".xml")
        } ?: emptyArray()).sortedBy { it.name }
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val rows = mutableListOf<Pair<String, String?>>()
        for (file in files) {
            val nodes = builder.parse(file).getElementsByTagName("*")
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as? Element ?: continue
                if (element.tagName.substringAfterLast(':') !in valueTags) continue
                val appKey = element.getAttributeNS(appNamespace, "key")
                val androidKey = element.getAttributeNS(androidNamespace, "key")
                val key = when {
                    appKey.isNotEmpty() -> appKey
                    androidKey.isNotEmpty() -> androidKey
                    else -> null
                }
                rows += Pair(file.name, key)
            }
        }
        assertTrue("no settings rows parsed from $xmlDir", rows.isNotEmpty())
        return rows
    }

    @Test
    fun `every settings row stores its value under a key the storage knows`() {
        val module = moduleDir()
        val known = storageKeyNames(module)
        val unwired = settingsRows(module).filter { row ->
            val key = row.second
            key == null || !known.contains(key)
        }
        if (unwired.isNotEmpty()) {
            val report = StringBuilder(
                "these rows store a value under a key no storage enum defines, " +
                        "so the setting will appear to work and do nothing:"
            )
            for (row in unwired) {
                report.append("\n  ").append(row.first).append(": app:key=\"").append(row.second)
                    .append('"')
            }
            fail(report.toString())
        }
    }
}
