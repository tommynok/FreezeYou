package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Before a bulk freeze the application lists the selected packages that would leave the device
 * hard to recover - no Settings, no keyboard, no status bar - and asks for confirmation. The two
 * names that are critical on any device are checked here, together with the rule that says the
 * answer keeps the order of the selection and contains nothing else. The device's own Settings and
 * input method packages are resolved from it at runtime and cannot be reached from a plain JVM
 * test, which is why the decision takes them as an argument.
 */
class CriticalPackagesTest {

    /** What a device would report: the two fixed names plus this build's own roles. */
    private val deviceCritical = setOf(
        "android",
        "com.android.systemui",
        "com.example.settings",
        "com.example.ime"
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
}
