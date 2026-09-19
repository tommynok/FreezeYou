package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Searching the application list matches both the name shown on screen and the package name.
 * On a real device "droid" matches every com.android.* package, so the order decides whether the
 * one the user meant is on screen at all — which is what made the search look broken.
 */
class SearchRankingTest {

    private fun entry(name: String, pkgName: String): MutableMap<String, Any> =
        mutableMapOf("Name" to name, "PackageName" to pkgName)

    private fun namesOf(result: List<Map<String, Any>>): List<String> =
        result.map { it["Name"] as String }

    private val apps = arrayListOf<MutableMap<String, Any>>(
        entry("Bluetooth", "com.android.bluetooth"),
        entry("Камера", "com.android.camera"),
        entry("Droid-ify", "com.looker.droidify"),
        entry("HostLookup", "de.obsp.hostlookup")
    )

    @Test
    fun `a name beginning with the text comes before packages containing it`() {
        val result = MoreUtils.processListFilter("droid", ArrayList(apps))
        assertEquals(
            listOf("Droid-ify", "Bluetooth", "Камера"),
            namesOf(result)
        )
    }

    @Test
    fun `a name containing the text comes before a package containing it`() {
        val result = MoreUtils.processListFilter("loo", ArrayList(apps))
        assertEquals(
            listOf("HostLookup", "Droid-ify"),
            namesOf(result)
        )
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(
            listOf("Droid-ify"),
            namesOf(MoreUtils.processListFilter("DROID-IFY", ArrayList(apps)))
        )
    }

    @Test
    fun `surrounding spaces do not stop a match`() {
        assertEquals(
            listOf("Droid-ify"),
            namesOf(MoreUtils.processListFilter("  droid-ify  ", ArrayList(apps)))
        )
    }

    @Test
    fun `text that matches nothing gives an empty list rather than everything`() {
        assertEquals(
            emptyList<String>(),
            namesOf(MoreUtils.processListFilter("zzz", ArrayList(apps)))
        )
    }

    @Test
    fun `a null list is tolerated`() {
        assertEquals(0, MoreUtils.processListFilter("anything", null).size)
    }
}
