package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Before a bulk freeze the application lists the selected packages that would leave the device
 * hard to recover - no Settings, no keyboard, no status bar, no home screen - and asks for
 * confirmation. The two names that are critical on any device are checked here, together with the
 * rule that says the answer keeps the order of the selection and contains nothing else. The
 * device's own Settings, input method and default launcher packages are resolved from it at
 * runtime and cannot be reached from a plain JVM test, which is why both the decision and the set
 * it reads take those names as arguments.
 */
class CriticalPackagesTest {

    /** What a device would report: the two fixed names plus this build's own roles. */
    private val deviceCritical = setOf(
        "android",
        "com.android.systemui",
        "com.example.settings",
        "com.example.ime",
        "com.example.launcher"
    )

    @Test
    fun `the system image and the status bar are critical everywhere`() {
        assertTrue(CriticalPackagesUtils.alwaysCriticalPackages().contains("android"))
        assertTrue(CriticalPackagesUtils.alwaysCriticalPackages().contains("com.android.systemui"))
    }

    @Test
    fun `only the critical ones are reported, in the order they were selected`() {
        val selected = listOf(
            "com.example.ime",
            "com.example.game",
            "android",
            "com.example.settings",
            "com.example.browser"
        )
        assertEquals(
            listOf("com.example.ime", "android", "com.example.settings"),
            CriticalPackagesUtils.findCriticalPackages(selected, deviceCritical)
        )
    }

    @Test
    fun `an ordinary application is never reported`() {
        assertEquals(
            emptyList<String>(),
            CriticalPackagesUtils.findCriticalPackages(
                listOf("com.example.game", "com.example.browser"), deviceCritical
            )
        )
    }

    @Test
    fun `an empty selection reports nothing`() {
        assertTrue(
            CriticalPackagesUtils.findCriticalPackages(emptyList<String>(), deviceCritical).isEmpty()
        )
    }

    @Test
    fun `a device role that was not resolved cannot make anything critical`() {
        // The resolved names come from the device: if Settings cannot be resolved there, the
        // package is simply not protected, and nothing else must be flagged in its place.
        val withoutRoles = CriticalPackagesUtils.alwaysCriticalPackages()
        assertTrue(
            CriticalPackagesUtils.findCriticalPackages(listOf("com.example.settings"), withoutRoles)
                .isEmpty()
        )
    }

    @Test
    fun `parsePackageNameFromImeId extracts package name correctly`() {
        assertEquals(
            "com.example.keyboard",
            CriticalPackagesUtils.parsePackageNameFromImeId("com.example.keyboard/.service.ExampleKeyboardService")
        )
        assertEquals(
            "com.android.inputmethod.latin",
            CriticalPackagesUtils.parsePackageNameFromImeId("com.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME")
        )
        assertEquals(
            "com.example.ime",
            CriticalPackagesUtils.parsePackageNameFromImeId("com.example.ime")
        )
        org.junit.Assert.assertNull(CriticalPackagesUtils.parsePackageNameFromImeId(null))
        org.junit.Assert.assertNull(CriticalPackagesUtils.parsePackageNameFromImeId(""))
        org.junit.Assert.assertNull(CriticalPackagesUtils.parsePackageNameFromImeId("   "))
        org.junit.Assert.assertNull(CriticalPackagesUtils.parsePackageNameFromImeId("null"))
    }

    @Test
    fun `the launcher the home button goes to is critical`() {
        val critical = CriticalPackagesUtils.buildCriticalSet(
            "com.example.settings",
            setOf("com.example.ime"),
            "com.example.launcher"
        )
        assertEquals(
            listOf("com.example.launcher"),
            CriticalPackagesUtils.findCriticalPackages(
                listOf("com.example.launcher", "com.example.game"), critical
            )
        )
    }

    @Test
    fun `a launcher that is not the default home stays freezable without a question`() {
        val critical = CriticalPackagesUtils.buildCriticalSet(
            "com.example.settings",
            setOf("com.example.ime"),
            "com.example.launcher"
        )
        assertTrue(
            CriticalPackagesUtils.findCriticalPackages(
                listOf("com.example.otherlauncher"), critical
            ).isEmpty()
        )
    }

    @Test
    fun `a device that resolved no launcher protects nothing in its place`() {
        assertEquals(
            CriticalPackagesUtils.alwaysCriticalPackages(),
            CriticalPackagesUtils.buildCriticalSet(null, emptySet(), null)
        )
    }

    @Test
    fun `only a real launcher name survives the home resolution`() {
        // "android" answers the home query when no launcher is left to answer it, and it is
        // already critical under its own name, so it must not be mistaken for a launcher here.
        assertNull(CriticalPackagesUtils.sanitizeHomePackage(null))
        assertNull(CriticalPackagesUtils.sanitizeHomePackage(""))
        assertNull(CriticalPackagesUtils.sanitizeHomePackage("   "))
        assertNull(CriticalPackagesUtils.sanitizeHomePackage("android"))
        assertEquals(
            "com.example.launcher",
            CriticalPackagesUtils.sanitizeHomePackage("com.example.launcher")
        )
        assertEquals(
            "com.example.launcher",
            CriticalPackagesUtils.sanitizeHomePackage("  com.example.launcher  ")
        )
    }
}
