package cf.playhi.freezeyou.utils

import android.app.Application
import cf.playhi.freezeyou.utils.LauncherShortcutUtils.ShortcutRecord
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, shadows = [ShadowContractMMKV::class])
class LauncherShortcutUtilsTest {

    private val context: Application
        get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        ShadowContractMMKV.reset()
    }

    @Test
    fun saveAndRetrieveShortcutRecords() {
        val record1 = ShortcutRecord(
            id = "id_1",
            name = "App One",
            pkgName = "com.example.app1",
            cls = "cf.playhi.freezeyou.Freeze",
            target = null,
            tasks = null,
            justLaunch = false
        )
        val record2 = ShortcutRecord(
            id = "id_2",
            name = "App Two Launch",
            pkgName = "com.example.app2",
            cls = "cf.playhi.freezeyou.Freeze",
            target = "com.example.app2.MainActivity",
            tasks = "tasks_data",
            justLaunch = true
        )

        LauncherShortcutUtils.saveShortcutRecord(context, record1)
        LauncherShortcutUtils.saveShortcutRecord(context, record2)

        val retrieved = LauncherShortcutUtils.getSavedShortcutRecords(context)
        assertEquals(2, retrieved.size)
        assertEquals(record1, retrieved[0])
        assertEquals(record2, retrieved[1])
    }

    @Test
    fun saveShortcutRecordReplacesSameId() {
        val record1 = ShortcutRecord(
            id = "same_id",
            name = "Old Name",
            pkgName = "com.example.app",
            cls = "cf.playhi.freezeyou.Freeze"
        )
        val record2 = ShortcutRecord(
            id = "same_id",
            name = "New Name",
            pkgName = "com.example.app",
            cls = "cf.playhi.freezeyou.Freeze"
        )

        LauncherShortcutUtils.saveShortcutRecord(context, record1)
        LauncherShortcutUtils.saveShortcutRecord(context, record2)

        val retrieved = LauncherShortcutUtils.getSavedShortcutRecords(context)
        assertEquals(1, retrieved.size)
        assertEquals("New Name", retrieved[0].name)
    }

    @Test
    fun removeShortcutsForPackageCleansMatchingShortcuts() {
        val record1 = ShortcutRecord("id_1", "App1", "com.example.remove", "cf.playhi.freezeyou.Freeze")
        val record2 = ShortcutRecord("id_2", "App2", "com.example.keep", "cf.playhi.freezeyou.Freeze")

        LauncherShortcutUtils.saveShortcutRecord(context, record1)
        LauncherShortcutUtils.saveShortcutRecord(context, record2)

        LauncherShortcutUtils.removeShortcutsForPackage(context, "com.example.remove")

        val retrieved = LauncherShortcutUtils.getSavedShortcutRecords(context)
        assertEquals(1, retrieved.size)
        assertEquals("com.example.keep", retrieved[0].pkgName)
    }

    @Test
    fun exportAndImportShortcutsJsonRoundTrip() {
        val record = ShortcutRecord(
            id = "id_export",
            name = "Export App",
            pkgName = "com.example.export",
            cls = "cf.playhi.freezeyou.Freeze",
            target = "com.example.export.Activity",
            tasks = "task",
            justLaunch = true
        )
        LauncherShortcutUtils.saveShortcutRecord(context, record)

        val exportedJson = LauncherShortcutUtils.exportShortcutsToJson(context)
        assertEquals(1, exportedJson.length())

        ShadowContractMMKV.reset()
        assertTrue(LauncherShortcutUtils.getSavedShortcutRecords(context).isEmpty())

        val importedCount = LauncherShortcutUtils.importShortcutsFromJson(context, exportedJson)
        assertEquals(1, importedCount)

        val retrieved = LauncherShortcutUtils.getSavedShortcutRecords(context)
        assertEquals(1, retrieved.size)
        assertEquals(record, retrieved[0])
    }

    @Test
    fun importShortcutsFromJsonIgnoresInvalidEntries() {
        val array = JSONArray()
            .put(JSONObject().put("name", "Valid").put("pkgName", "com.valid").put("cls", "cf.playhi.freezeyou.Freeze"))
            .put(JSONObject().put("name", "").put("pkgName", "com.invalid").put("cls", "cf.playhi.freezeyou.Freeze"))
            .put(JSONObject().put("name", "NoClass").put("pkgName", "com.noclass"))

        val count = LauncherShortcutUtils.importShortcutsFromJson(context, array)
        assertEquals(1, count)

        val retrieved = LauncherShortcutUtils.getSavedShortcutRecords(context)
        assertEquals(1, retrieved.size)
        assertEquals("com.valid", retrieved[0].pkgName)
    }

    @Test
    fun parseShortcutRecordsHandlesCorruptedJson() {
        val result = LauncherShortcutUtils.parseShortcutRecords("not valid json")
        assertTrue(result.isEmpty())
    }

    @Test
    fun isSpecialOrBatchShortcutIdentifiesSpecialKeys() {
        assertTrue(LauncherShortcutUtils.isSpecialOrBatchShortcut("cf.playhi.freezeyou.extra.fuf"))
        assertTrue(LauncherShortcutUtils.isSpecialOrBatchShortcut("cf.playhi.freezeyou.extra.oklock"))
        assertTrue(LauncherShortcutUtils.isSpecialOrBatchShortcut("CATEGORY_GAMES"))
        assertTrue(LauncherShortcutUtils.isSpecialOrBatchShortcut("FORCESTOPCATEGORY_WORK"))
        assertTrue(LauncherShortcutUtils.isSpecialOrBatchShortcut("FOQ"))
        assertFalse(LauncherShortcutUtils.isSpecialOrBatchShortcut("com.example.regular.app"))
        assertFalse(LauncherShortcutUtils.isSpecialOrBatchShortcut(null))
    }
}
