package cf.playhi.freezeyou

import android.app.Application
import android.content.Intent
import cf.playhi.freezeyou.ui.AppLockActivity
import cf.playhi.freezeyou.utils.ProcessSharedState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The lock that is thrown over an application the user keeps unfrozen to receive its messages.
 *
 * Three decisions are worth pinning down, and all three are the kind that only show up on a device
 * once they have gone wrong. The gate decides whether a window change is answered with a lock
 * screen: raise it too readily and the user is asked again inside an application they have just
 * proved themselves inside, raise it too rarely and the application is simply open. The intent is
 * the whole contract between the service that sees the window and the screen that asks - the extra
 * naming the application is what lets that screen know that leaving it towards the application
 * would be the answer the user refused. And the session set is what the two processes hand each
 * other, so its shape has to survive two names of which one begins with the other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppLockInterceptionTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `a protected application is locked while the lock is on and can be asked for`() {
        assertTrue(AccessibilityService.appLockShouldIntercept(true, true, false))
    }

    @Test
    fun `nothing is raised when the user has not switched the lock on`() {
        assertFalse(AccessibilityService.appLockShouldIntercept(false, true, false))
    }

    @Test
    fun `nothing is raised on a device that has nothing to ask with`() {
        // An application the user cannot be asked to prove themselves to stays an application they
        // are not asked about: raising the screen here is a wall with no door in it.
        assertFalse(AccessibilityService.appLockShouldIntercept(true, false, false))
    }

    @Test
    fun `the application the user was let into is not asked about again`() {
        assertFalse(AccessibilityService.appLockShouldIntercept(true, true, true))
    }

    @Test
    fun `the interception names the application it covers`() {
        val intent = AccessibilityService.buildAppLockIntent(context, "com.example.mail")

        assertEquals("cf.playhi.freezeyou", intent.component!!.packageName)
        assertEquals("cf.playhi.freezeyou.ui.AppLockActivity", intent.component!!.className)
        assertEquals("com.example.mail", intent.getStringExtra("targetPkgName"))
        assertEquals("com.example.mail", intent.getStringExtra("unlockLogoPkgName"))
    }

    @Test
    fun `the interception is raised without an animation and refuses the previous answer`() {
        val intent = AccessibilityService.buildAppLockIntent(context, "com.example.mail")

        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            intent.flags
        )
        // The lock of this application reads its own timeout; an application that just took the
        // screen has no unlocked state to be measured against, and the screen has to come up.
        assertTrue(intent.getBooleanExtra("ignoreCurrentUnlockStatus", false))
    }

    @Test
    fun `an intent naming its application is an interception`() {
        assertTrue(
            AppLockActivity.isInterceptionRequest(
                AccessibilityService.buildAppLockIntent(context, "com.example.mail")
            )
        )
    }

    @Test
    fun `the lock of this application is not an interception and is not sent home`() {
        // The settings row that turns the lock on proves the door opens before it installs the door.
        // Sending the user out of the device over a cancelled test there is a worse answer than the
        // one it had, which was to leave the caller finish its own screen.
        assertFalse(
            AppLockActivity.isInterceptionRequest(
                Intent().putExtra("ignoreCurrentUnlockStatus", true)
            )
        )
        assertFalse(AppLockActivity.isInterceptionRequest(Intent()))
        assertFalse(AppLockActivity.isInterceptionRequest(null))
        assertFalse(
            AppLockActivity.isInterceptionRequest(Intent().putExtra("targetPkgName", "  "))
        )
    }

    @Test
    fun `unlocking an application puts it in the session`() {
        assertEquals("com.a,", ProcessSharedState.unlockedPackagesAfterUnlock("", "com.a"))
        assertEquals("com.a,", ProcessSharedState.unlockedPackagesAfterUnlock(null, "com.a"))
        assertTrue(ProcessSharedState.isUnlockedIn("com.a,", "com.a"))
    }

    @Test
    fun `an application opened twice is still one entry`() {
        val once = ProcessSharedState.unlockedPackagesAfterUnlock("", "com.a")

        assertEquals("com.a,", ProcessSharedState.unlockedPackagesAfterUnlock(once, "com.a"))
        // Unchanged in, unchanged out: the caller writes nothing back, and every window change does
        // not pay for a trip to the store.
        assertEquals(once, ProcessSharedState.unlockedPackagesAfterUnlock(once, "com.a"))
    }

    @Test
    fun `two applications open at once are two entries`() {
        val both = ProcessSharedState.unlockedPackagesAfterUnlock("com.a,", "com.b")

        assertEquals("com.a,com.b,", both)
        assertTrue(ProcessSharedState.isUnlockedIn(both, "com.a"))
        assertTrue(ProcessSharedState.isUnlockedIn(both, "com.b"))
    }

    @Test
    fun `locking the shorter name does not open the longer one it was inside`() {
        // "com.foo" is a piece of "com.foo.bar,". A removal that matched the piece took the real
        // name with it, and locked an application nobody had been let into while leaving this one
        // recorded as open for good.
        val both = "com.foo,com.foo.bar,"

        assertEquals(
            "com.foo.bar,", ProcessSharedState.unlockedPackagesAfterLock(both, "com.foo")
        )
        assertEquals("com.foo,", ProcessSharedState.unlockedPackagesAfterLock(both, "com.foo.bar"))
    }

    @Test
    fun `a name that merely sits inside another is not unlocked`() {
        assertFalse(ProcessSharedState.isUnlockedIn("com.foo.bar,", "com.foo"))
        assertFalse(ProcessSharedState.isUnlockedIn("xcom.foo,", "com.foo"))
        assertTrue(ProcessSharedState.isUnlockedIn("com.foo.bar,", "com.foo.bar"))
    }

    @Test
    fun `locking an application that was never open changes nothing`() {
        val set = "com.a,"

        assertEquals(set, ProcessSharedState.unlockedPackagesAfterLock(set, "com.b"))
        assertEquals("", ProcessSharedState.unlockedPackagesAfterLock("", "com.b"))
    }

    @Test
    fun `what was unlocked is locked again by the way out`() {
        val opened = ProcessSharedState.unlockedPackagesAfterUnlock("com.a,", "com.b")

        assertEquals("com.a,", ProcessSharedState.unlockedPackagesAfterLock(opened, "com.b"))
        assertFalse(ProcessSharedState.isUnlockedIn("com.a,", "com.b"))
    }

    @Test
    fun `a blank name is neither unlocked nor taken out of the session`() {
        val set = "com.a,"

        assertEquals(set, ProcessSharedState.unlockedPackagesAfterUnlock(set, ""))
        assertEquals(set, ProcessSharedState.unlockedPackagesAfterUnlock(set, null))
        assertEquals(set, ProcessSharedState.unlockedPackagesAfterLock(set, ""))
        assertEquals(set, ProcessSharedState.unlockedPackagesAfterLock(set, null))
        assertFalse(ProcessSharedState.isUnlockedIn(set, ""))
        assertFalse(ProcessSharedState.isUnlockedIn(set, null))
    }

    @Test
    fun `an absent session unlocks nothing`() {
        assertFalse(ProcessSharedState.isUnlockedIn(null, "com.a"))
        assertFalse(ProcessSharedState.isUnlockedIn("", "com.a"))
    }
}
