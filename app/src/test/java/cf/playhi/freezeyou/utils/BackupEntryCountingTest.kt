package cf.playhi.freezeyou.utils

import android.app.Application
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class BackupEntryCountingTest {

    @Test
    fun deviceSpecificBooleansAreNotCountedAsRestorable() {
        val settings = JSONObject()
            .put("firstIconEnabled", true)
            .put("secondIconEnabled", true)
            .put("thirdIconEnabled", true)
            .put("enableAuthentication", true)
            .put("noCaution", true)
            .put("cacheApplicationsIcons", true)

        assertEquals(2, countBooleanEntries(settings))
    }

    @Test
    fun unknownBooleanKeysAreNotCounted() {
        val settings = JSONObject()
            .put("noCaution", true)
            .put("notASetting", true)

        assertEquals(1, countBooleanEntries(settings))
    }

    @Test
    fun aBackupWithOnlySkippedBooleansHasNothingToRestore() {
        val settings = JSONObject()
            .put("firstIconEnabled", true)
            .put("secondIconEnabled", false)
            .put("thirdIconEnabled", true)
            .put("enableAuthentication", false)

        assertEquals(0, countBooleanEntries(settings))
    }

    @Test
    fun onlyRecognisedStringSettingsAreCounted() {
        val settings = JSONObject()
            .put("uiStyleSelection", "default")
            .put("notASetting", "x")
        val backup = JSONObject().put("generalSettings_string", JSONArray().put(settings))

        assertEquals(1, count(backup, "generalSettings_string"))
    }

    @Test
    fun onlyRecognisedIntSettingsAreCounted() {
        val settings = JSONObject()
            .put("onClickFunctionStatus", 1)
            .put("notASetting", 2)
        val backup = JSONObject().put("generalSettings_int", JSONArray().put(settings))

        assertEquals(1, count(backup, "generalSettings_int"))
    }

    @Test
    fun aMissingCategoryIsReportedAsAbsent() {
        assertEquals(-1, count(JSONObject(), "generalSettings_boolean"))
    }

    @Test
    fun onlyValidTimeTasksAreCounted() {
        val tasks = JSONArray()
            .put(timeTask(7, 30, "0", "okff", 1))
            .put(timeTask(25, 0, "0", "okff", 1))
            .put(timeTask(8, 0, "0", "okff", 1, doNotImport = true))
            .put(JSONObject().put("hour", 9))
        val backup = JSONObject().put("userTimeScheduledTasks", tasks)

        assertEquals(1, count(backup, "userTimeScheduledTasks"))
    }

    @Test
    fun onlySupportedTriggerTasksAreCounted() {
        val tasks = JSONArray()
            .put(triggerTask("onScreenOn", "okff", 1))
            .put(triggerTask("onNothing", "okff", 1))
        val backup = JSONObject().put("userTriggerScheduledTasks", tasks)

        assertEquals(1, count(backup, "userTriggerScheduledTasks"))
    }

    @Test
    fun oneKeyListsCountOnlyKnownListKeys() {
        val lists = JSONArray().put(
            JSONObject().put("okff", "a,b").put("notAList", "c")
        )
        val backup = JSONObject().put("oneKeyList", lists)

        assertEquals(1, count(backup, "oneKeyList"))
    }

    @Test
    fun categoryLabelsMustBeBase64Decodable() {
        val categories = JSONArray()
            .put(category("cat", "a,b"))
            .put(JSONObject().put("label", "not base64!!").put("packages", "a,b"))
        val backup = JSONObject().put("userDefinedCategories", categories)

        assertEquals(1, count(backup, "userDefinedCategories"))
    }

    @Test
    fun anAllowListIsCountedOnlyWhenItsObjectExists() {
        val present = JSONObject().put(
            "uriAutoAllowPkgs_allows",
            JSONArray().put(JSONObject().put("lists", "a"))
        )
        val empty = JSONObject().put("uriAutoAllowPkgs_allows", JSONArray())

        assertEquals(1, count(present, "uriAutoAllowPkgs_allows"))
        assertEquals(0, count(empty, "uriAutoAllowPkgs_allows"))
    }

    private fun count(backup: JSONObject, category: String): Int =
        BackupUtils.countImportableEntries(backup, category)

    private fun countBooleanEntries(settings: JSONObject): Int =
        count(JSONObject().put("generalSettings_boolean", JSONArray().put(settings)),
            "generalSettings_boolean")

    private fun timeTask(
        hour: Int, minutes: Int, repeat: String, task: String, enabled: Int,
        doNotImport: Boolean = false
    ): JSONObject = JSONObject()
        .put("hour", hour)
        .put("minutes", minutes)
        .put("repeat", repeat)
        .put("enabled", enabled)
        .put("label", "label")
        .put("task", task)
        .put("doNotImport", doNotImport)

    private fun triggerTask(tg: String, task: String, enabled: Int): JSONObject = JSONObject()
        .put("tg", tg)
        .put("tgextra", "")
        .put("enabled", enabled)
        .put("label", "label")
        .put("task", task)

    private fun category(label: String, packages: String): JSONObject = JSONObject()
        .put("label", Base64.encodeToString(label.toByteArray(), Base64.NO_WRAP))
        .put("packages", packages)
}
