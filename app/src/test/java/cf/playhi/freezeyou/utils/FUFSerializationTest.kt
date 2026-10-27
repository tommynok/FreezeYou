package cf.playhi.freezeyou.utils

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * At most one FUF operation per package at a time. A rapid double tap on a list row or a shortcut
 * used to start two operations for the same application: both read the old state, both committed,
 * the statistics and the trigger tasks counted two changes, and a shortcut could flip the state
 * back and forth. The paths that use the registry sit behind MMKV, toasts and package checks, so
 * the registry itself is held here without Android.
 */
class FUFSerializationTest {

    @Before
    fun clearBefore() {
        FUFUtils.clearInFlightPackagesForTests()
    }

    @After
    fun clearAfter() {
        FUFUtils.clearInFlightPackagesForTests()
    }

    @Test
    fun `a package can be acquired only once until released`() {
        assertTrue(FUFUtils.tryAcquirePackage("com.example.app"))
        assertFalse(FUFUtils.tryAcquirePackage("com.example.app"))
    }

    @Test
    fun `release allows the package to be acquired again`() {
        assertTrue(FUFUtils.tryAcquirePackage("com.example.app"))
        FUFUtils.releasePackage("com.example.app")
        assertTrue(FUFUtils.tryAcquirePackage("com.example.app"))
    }

    @Test
    fun `distinct packages do not block each other`() {
        assertTrue(FUFUtils.tryAcquirePackage("com.example.one"))
        assertTrue(FUFUtils.tryAcquirePackage("com.example.two"))
    }

    @Test
    fun `isPackageInFlight follows acquire and release`() {
        assertFalse(FUFUtils.isPackageInFlight("com.example.app"))
        FUFUtils.tryAcquirePackage("com.example.app")
        assertTrue(FUFUtils.isPackageInFlight("com.example.app"))
        FUFUtils.releasePackage("com.example.app")
        assertFalse(FUFUtils.isPackageInFlight("com.example.app"))
    }

    @Test
    fun `clear resets every in-flight package`() {
        FUFUtils.tryAcquirePackage("com.example.one")
        FUFUtils.tryAcquirePackage("com.example.two")
        FUFUtils.clearInFlightPackagesForTests()
        assertFalse(FUFUtils.isPackageInFlight("com.example.one"))
        assertFalse(FUFUtils.isPackageInFlight("com.example.two"))
        assertTrue(FUFUtils.tryAcquirePackage("com.example.one"))
    }
}
