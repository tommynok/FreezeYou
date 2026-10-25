package cf.playhi.freezeyou.ui

import android.app.Application
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
    fun onlyThePresentSkippedKeysAreSubtracted() {
        val settings = JSONObject()
            .put("firstIconEnabled", true)
            .put("noCaution", true)

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
    fun aMissingCategoryIsReportedAsAbsent() {
        assertEquals(
            -1,
            BackupImportChooserActivity.countEntries(JSONObject(), "generalSettings_boolean")
        )
    }

    @Test
    fun arrayCategoriesCountTheirElements() {
        val tasks = JSONArray().put(JSONObject()).put(JSONObject())
        val backup = JSONObject().put("userTimeScheduledTasks", tasks)

        assertEquals(
            2,
            BackupImportChooserActivity.countEntries(backup, "userTimeScheduledTasks")
        )
    }

    @Test
    fun settingsCategoriesCountTheirObjectKeys() {
        val settings = JSONObject().put("onClickFunctionStatus", 1).put("sortMethodStatus", 0)
        val backup = JSONObject().put("generalSettings_int", JSONArray().put(settings))

        assertEquals(2, BackupImportChooserActivity.countEntries(backup, "generalSettings_int"))
    }

    private fun countBooleanEntries(settings: JSONObject): Int =
        BackupImportChooserActivity.countEntries(
            JSONObject().put("generalSettings_boolean", JSONArray().put(settings)),
            "generalSettings_boolean"
        )
}
