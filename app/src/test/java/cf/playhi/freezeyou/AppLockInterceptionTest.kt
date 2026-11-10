package cf.playhi.freezeyou

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import cf.playhi.freezeyou.utils.AccessibilityUtils
import cf.playhi.freezeyou.ui.AppLockActivity
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.enableAppLock
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageBooleanKeys.enableAuthentication
import cf.playhi.freezeyou.utils.AuthenticationUtils
import cf.playhi.freezeyou.utils.OneKeyListUtils
import cf.playhi.freezeyou.utils.ProcessSharedState
import cf.playhi.freezeyou.utils.ShadowContractMMKV
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
 * would be the answer the user refused, and the session it was stamped in is what lets it know that
 * the answer arrived after that session had ended. And the session set is what the two processes
 * hand each other, so its shape has to survive two names of which one begins with the other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34], application = Application::class,
    shadows = [ShadowContractMMKV::class],
    instrumentedPackages = ["com.tencent.mmkv"]
)
class AppLockInterceptionTest {

    private lateinit var context: Application

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ShadowContractMMKV.reset()
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
    fun `the app lock requires an active protection method`() {
        assertFalse(AuthenticationUtils.isAuthenticationEnabled())
        assertFalse(AuthenticationUtils.isAppLockEnabled())

        // A switch left on with no protection method chosen must not intercept unprotected: the
        // gate answers for the upgrade until a method is chosen.
        enableAppLock.setValue(value = true)
        assertFalse(AuthenticationUtils.isAppLockEnabled())

        enableAuthentication.setValue(value = true)
        assertTrue(AuthenticationUtils.isAuthenticationEnabled())
        assertTrue(AuthenticationUtils.isAppLockEnabled())

        // Turning the protection off takes the app lock with it, even with the switch left on.
        enableAuthentication.setValue(value = false)
        assertFalse(AuthenticationUtils.isAppLockEnabled())
    }

    @Test
    fun `lock on screen off is applicable whenever either lock is active`() {
        assertFalse(AuthenticationUtils.isLockOnScreenOffApplicable(false, false))
        assertTrue(AuthenticationUtils.isLockOnScreenOffApplicable(true, false))
        assertTrue(AuthenticationUtils.isLockOnScreenOffApplicable(false, true))
        assertTrue(AuthenticationUtils.isLockOnScreenOffApplicable(true, true))

        enableAuthentication.setValue(value = false)
        enableAppLock.setValue(value = false)
        assertFalse(AuthenticationUtils.isLockOnScreenOffApplicable())

        // The app lock switch alone no longer counts: without a protection method it is gated off.
        enableAppLock.setValue(value = true)
        assertFalse(AuthenticationUtils.isLockOnScreenOffApplicable())

        enableAuthentication.setValue(value = true)
        assertTrue(AuthenticationUtils.isLockOnScreenOffApplicable())

        enableAppLock.setValue(value = false)
        assertTrue(AuthenticationUtils.isLockOnScreenOffApplicable())
    }

    @Test
    fun `a shortcut that only launches an unprotected application is not stopped at this door`() {
        assertTrue(Freeze.isPlainLaunchOfAnUnprotectedApp(true, true, false))

        // An application the user listed is asked about by the service that watches windows, over
        // the application itself - asking here would be two screens for one answer, so the
        // trampoline stays out of it.
        assertFalse(Freeze.isPlainLaunchOfAnUnprotectedApp(true, true, true))

        // A shortcut that freezes, unfreezes or opens-and-unfreezes changes this application's own
        // state, and keeps the lock that guards it.
        assertFalse(Freeze.isPlainLaunchOfAnUnprotectedApp(false, true, false))

        // Nothing to launch is not a launch either: the request is left to the ordinary gate, which
        // answers it with an invalid-arguments toast rather than a silent pass.
        assertFalse(Freeze.isPlainLaunchOfAnUnprotectedApp(true, false, false))
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

    @Test
    fun `the interception stamps the session it was raised in`() {
        val epoch = ProcessSharedState.advanceSessionEpoch()

        val intent = AccessibilityService.buildAppLockIntent(context, "com.example.mail")

        assertEquals(epoch, intent.getLongExtra("appLockSessionEpoch", ProcessSharedState.NO_APP_LOCK_SESSION))
    }

    @Test
    fun `a session that ended is not one a lock screen may answer in`() {
        assertFalse(ProcessSharedState.isAppLockSessionStale(0L, 0L))
        assertFalse(ProcessSharedState.isAppLockSessionStale(2L, 2L))
        assertTrue(ProcessSharedState.isAppLockSessionStale(0L, 1L))
        assertTrue(ProcessSharedState.isAppLockSessionStale(2L, 3L))
    }

    @Test
    fun `a screen that was never stamped is not refused by a session it never had`() {
        // The settings row that proves the door opens names no session. Refusing its answer would be
        // the worse kind of safe: the user leaves the application because a screen-off happened while
        // they were being asked whether they may open this one.
        assertFalse(
            ProcessSharedState.isAppLockSessionStale(
                ProcessSharedState.NO_APP_LOCK_SESSION, 0L
            )
        )
        assertFalse(
            ProcessSharedState.isAppLockSessionStale(
                ProcessSharedState.NO_APP_LOCK_SESSION, 7L
            )
        )
    }

    @Test
    fun `ending the session moves the epoch even when nothing was unlocked`() {
        // The set cannot report this session: a lock screen still waiting for its first answer names
        // no unlocked package, so clearing the set would be a no-op and the late success would stand.
        val raised = ProcessSharedState.getSessionEpoch()

        ProcessSharedState.clearAllUnlockedPackages()

        assertEquals(raised + 1, ProcessSharedState.getSessionEpoch())
        assertTrue(
            ProcessSharedState.isAppLockSessionStale(raised, ProcessSharedState.getSessionEpoch())
        )
    }

    @Test
    fun `an answer belongs to the application and the session it was asked about`() {
        // The screen can be re-tasked over another application, or outlive the session it was raised
        // in, while a question is still in flight. The biometric proves who is holding the phone in
        // both cases and says nothing about which application they were being let into.
        assertTrue(AppLockActivity.isAnswerForCurrentQuestion("com.a,", "com.a,", 3L, 3L))
        assertFalse(AppLockActivity.isAnswerForCurrentQuestion("com.a,", "com.b,", 3L, 3L))
        assertFalse(AppLockActivity.isAnswerForCurrentQuestion("com.a,", "com.a,", 3L, 4L))
    }

    @Test
    fun `an answer given on the lock of this application is never refused for a session`() {
        // The settings row that proves the door opens names no application and carries no stamp, and
        // a screen-off while the user stands in front of it is not a reason to doubt their answer.
        assertTrue(
            AppLockActivity.isAnswerForCurrentQuestion(
                null, null, ProcessSharedState.NO_APP_LOCK_SESSION, 9L
            )
        )
        // The same screen re-tasked over somebody else's application is a different answer, though.
        assertFalse(AppLockActivity.isAnswerForCurrentQuestion(null, "com.a,", -1L, 9L))
    }

    @Test
    fun `a refused lock owes the way out of the application it stopped`() {
        // The lock screen is a window of this application, so the user never left it for the
        // protected one: the freeze-on-quit and the exit rules of that application have nobody to
        // run them until somebody else takes the screen.
        assertTrue(AccessibilityService.pendingLockOwesDeparture("com.example.bank", false))
        assertFalse(AccessibilityService.pendingLockOwesDeparture("com.example.bank", true))
    }

    @Test
    fun `an application the user was let into is not left by the lock coming down`() {
        // Their answer opened it, and the way out is an ordinary departure with the unlock still
        // recorded - running the tasks here would freeze the application the user is reading.
        assertFalse(AccessibilityService.pendingLockOwesDeparture(null, false))
        assertFalse(AccessibilityService.pendingLockOwesDeparture("", false))
    }

    @Test
    fun `a protected application is on the list the menus read, and off it again`() {
        val listKey = context.getString(R.string.sAppLockList)
        assertEquals("New_AppLockList", listKey)

        // The popup decides add-or-remove inside the write, so the second tap is the removal and not
        // a repeat of the first.
        assertTrue(OneKeyListUtils.toggleInOneKeyList(context, listKey, "com.example.mail")!!)
        assertTrue(OneKeyListUtils.existsInOneKeyList(context, listKey, "com.example.mail"))
        assertFalse(OneKeyListUtils.toggleInOneKeyList(context, listKey, "com.example.mail")!!)
        assertFalse(OneKeyListUtils.existsInOneKeyList(context, listKey, "com.example.mail"))
    }

    @Test
    fun `a selection added to the protected list is all on it and a removal takes only what it names`() {
        val listKey = context.getString(R.string.sAppLockList)

        assertTrue(
            OneKeyListUtils.addToOneKeyList(context, listKey, listOf("com.example.mail", "com.example.bank"))
        )
        assertTrue(OneKeyListUtils.existsInOneKeyList(context, listKey, "com.example.mail"))
        assertTrue(OneKeyListUtils.existsInOneKeyList(context, listKey, "com.example.bank"))

        assertTrue(OneKeyListUtils.removeFromOneKeyList(context, listKey, listOf("com.example.mail")))

        assertFalse(OneKeyListUtils.existsInOneKeyList(context, listKey, "com.example.mail"))
        assertTrue(OneKeyListUtils.existsInOneKeyList(context, listKey, "com.example.bank"))
    }

    @Test
    fun `re-lock policy determines whether application is locked immediately on exit`() {
        assertTrue(AccessibilityService.shouldRelockOnAppExit("immediately"))
        assertTrue(AccessibilityService.shouldRelockOnAppExit(null))
        assertFalse(AccessibilityService.shouldRelockOnAppExit("screen_off"))
        assertFalse(AccessibilityService.shouldRelockOnAppExit("60000"))
        assertFalse(AccessibilityService.shouldRelockOnAppExit("120000"))
        assertFalse(AccessibilityService.shouldRelockOnAppExit("300000"))
    }

    @Test
    fun `re-lock timeout expiry evaluates departure timestamp against configured threshold`() {
        // Immediate policy always expires
        assertTrue(AccessibilityService.isAppLockSessionExpired("immediately", 1000L, 1005L))
        assertTrue(AccessibilityService.isAppLockSessionExpired(null, 1000L, 1005L))

        // Screen-off policy never expires while device remains active
        assertFalse(AccessibilityService.isAppLockSessionExpired("screen_off", 1000L, 9999999L))

        // Threshold policy
        // Active session without recorded departure never expires prematurely
        assertFalse(AccessibilityService.isAppLockSessionExpired("60000", null, 5000L))

        // Within threshold: unexpired
        assertFalse(AccessibilityService.isAppLockSessionExpired("60000", 1000L, 30000L))
        assertFalse(AccessibilityService.isAppLockSessionExpired("60000", 1000L, 60999L))

        // At or beyond threshold: expired
        assertTrue(AccessibilityService.isAppLockSessionExpired("60000", 1000L, 61000L))
        assertTrue(AccessibilityService.isAppLockSessionExpired("60000", 1000L, 70000L))

        // Malformed policy safely defaults to expired
        assertTrue(AccessibilityService.isAppLockSessionExpired("invalid", 1000L, 1005L))
    }

    @Test
    fun `accessibility detection matches both full and short component names`() {
        val target = ComponentName("cf.playhi.freezeyou", "cf.playhi.freezeyou.AccessibilityService")

        // Full component notation
        assertTrue(
            AccessibilityUtils.isComponentInSettingValue(
                "cf.playhi.freezeyou/cf.playhi.freezeyou.AccessibilityService",
                target
            )
        )

        // Short component notation common on modern Android / OEM ROMs
        assertTrue(
            AccessibilityUtils.isComponentInSettingValue(
                "cf.playhi.freezeyou/.AccessibilityService",
                target
            )
        )

        // Multi-service colon-separated string with both short and external services
        val composite = "com.other.service/.MyService:cf.playhi.freezeyou/.AccessibilityService:com.third.app/.ThirdService"
        assertTrue(AccessibilityUtils.isComponentInSettingValue(composite, target))

        // Non-matching services
        assertFalse(AccessibilityUtils.isComponentInSettingValue("com.other.service/.MyService", target))
        assertFalse(AccessibilityUtils.isComponentInSettingValue("cf.playhi.freezeyou/.OtherService", target))

        // Null and empty checks
        assertFalse(AccessibilityUtils.isComponentInSettingValue(null, target))
        assertFalse(AccessibilityUtils.isComponentInSettingValue("", target))
        assertFalse(AccessibilityUtils.isComponentInSettingValue("cf.playhi.freezeyou/.AccessibilityService", null))
    }

    @Test
    fun `isReturningFromAppLock identifies transitions returning from lock screen`() {
        val ourPkg = "cf.playhi.freezeyou"
        val lockedApp = "com.example.bank"

        // Active lock flag is sufficient to identify return
        assertTrue(
            AccessibilityService.isReturningFromAppLock(
                true, "com.any", lockedApp, lockedApp, ourPkg
            )
        )

        // Matching package transition when lock flag was already reset
        assertTrue(
            AccessibilityService.isReturningFromAppLock(
                false, ourPkg, lockedApp, lockedApp, ourPkg
            )
        )

        // Ordinary app transitions from external apps are not returning from lock
        assertFalse(
            AccessibilityService.isReturningFromAppLock(
                false, "com.android.launcher", lockedApp, lockedApp, ourPkg
            )
        )
        assertFalse(
            AccessibilityService.isReturningFromAppLock(
                false, ourPkg, "com.other.app", lockedApp, ourPkg
            )
        )
        assertFalse(
            AccessibilityService.isReturningFromAppLock(
                false, null, lockedApp, lockedApp, ourPkg
            )
        )
        assertFalse(
            AccessibilityService.isReturningFromAppLock(
                false, ourPkg, lockedApp, null, ourPkg
            )
        )
    }

    @Test
    fun `isLauncherOrRecents detects overview and launcher windows across OEMs`() {
        // AOSP / Pixel Quickstep & Launcher3
        assertTrue(AccessibilityService.isLauncherOrRecents(context, "com.android.launcher3", null))
        assertTrue(AccessibilityService.isLauncherOrRecents(context, "com.google.android.apps.nexuslauncher", null))
        assertTrue(
            AccessibilityService.isLauncherOrRecents(
                context, "com.android.systemui", "com.android.systemui.recents.RecentsActivity"
            )
        )
        assertTrue(
            AccessibilityService.isLauncherOrRecents(
                context, "com.android.systemui", "com.android.quickstep.RecentsActivity"
            )
        )

        // Xiaomi MIUI / HyperOS
        assertTrue(AccessibilityService.isLauncherOrRecents(context, "com.miui.home", "com.miui.home.launcher.Launcher"))
        assertTrue(
            AccessibilityService.isLauncherOrRecents(
                context, "com.android.systemui", "com.android.systemui.fsgesture.FsGestureService"
            )
        )

        // Samsung OneUI
        assertTrue(AccessibilityService.isLauncherOrRecents(context, "com.sec.android.app.launcher", null))

        // Non-overview SystemUI windows must NOT be mistaken for recents
        assertFalse(
            AccessibilityService.isLauncherOrRecents(
                context, "com.android.systemui", "com.android.systemui.statusbar.phone.NotificationShadeWindowView"
            )
        )
        assertFalse(
            AccessibilityService.isLauncherOrRecents(
                context, "miui.systemui.plugin", "com.miui.misound.playervolume.MiuiVolumeDialogImpl\$CustomDialog"
            )
        )

        // Regular third-party application
        assertFalse(
            AccessibilityService.isLauncherOrRecents(
                context, "com.example.bank", "com.example.bank.MainActivity"
            )
        )

        // Null and blank checks
        assertFalse(AccessibilityService.isLauncherOrRecents(context, null, null))
        assertFalse(AccessibilityService.isLauncherOrRecents(context, "", null))
    }

    @Test
    fun `shouldSkipSamePackageTransition protects locked apps while allowing free navigation inside unlocked apps`() {
        // Non-protected apps always skip (standard activity transitions)
        assertTrue(
            AccessibilityService.shouldSkipSamePackageTransition(
                false, false, false
            )
        )

        // Protected app that is already unlocked for this session skips
        assertTrue(
            AccessibilityService.shouldSkipSamePackageTransition(
                true, true, true
            )
        )

        // Protected app that is currently locked MUST NOT skip: interception must run
        assertFalse(
            AccessibilityService.shouldSkipSamePackageTransition(
                true, false, true
            )
        )

        // Protected app when lock is disabled in settings skips
        assertTrue(
            AccessibilityService.shouldSkipSamePackageTransition(
                true, false, false
            )
        )
    }

    @Test
    fun `isTransientInputOverlay identifies soft keyboards and OEM volume dialogs`() {
        assertTrue(
            AccessibilityService.isTransientInputOverlay(
                context, "com.google.android.inputmethod.latin", "android.inputmethodservice.SoftInputWindow"
            )
        )
        assertTrue(
            AccessibilityService.isTransientInputOverlay(
                context, "miui.systemui.plugin", "com.miui.misound.playervolume.MiuiVolumeDialogImpl\$CustomDialog"
            )
        )
        assertFalse(
            AccessibilityService.isTransientInputOverlay(
                context, "com.example.bank", "com.example.bank.MainActivity"
            )
        )
        assertFalse(
            AccessibilityService.isTransientInputOverlay(
                context, null, null
            )
        )
    }

    @Test
    fun `isPermissionDialog recognizes runtime permission dialog packages`() {
        assertTrue(AccessibilityService.isPermissionDialog("com.android.permissioncontroller"))
        assertTrue(AccessibilityService.isPermissionDialog("com.google.android.permissioncontroller"))
        assertTrue(AccessibilityService.isPermissionDialog("com.android.packageinstaller"))
        assertFalse(AccessibilityService.isPermissionDialog("com.example.bank"))
        assertFalse(AccessibilityService.isPermissionDialog(null))
        assertFalse(AccessibilityService.isPermissionDialog(""))
    }

    @Test
    fun `isSystemUiOverlay distinguishes status bar shades from multitasking recents and launchers`() {
        // Notification shade is an overlay
        assertTrue(
            AccessibilityService.isSystemUiOverlay(
                context, "com.android.systemui", "com.android.systemui.statusbar.phone.NotificationShadeWindowView"
            )
        )
        assertTrue(
            AccessibilityService.isSystemUiOverlay(
                context, "android", null
            )
        )

        // Recents overview is NOT a transient shade overlay
        assertFalse(
            AccessibilityService.isSystemUiOverlay(
                context, "com.android.systemui", "com.android.systemui.recents.RecentsActivity"
            )
        )

        // Launchers are NOT overlays
        assertFalse(
            AccessibilityService.isSystemUiOverlay(
                context, "com.miui.home", "com.miui.home.launcher.Launcher"
            )
        )

        // Standard user applications are NOT overlays
        assertFalse(
            AccessibilityService.isSystemUiOverlay(
                context, "com.example.bank", "com.example.bank.MainActivity"
            )
        )
        assertFalse(AccessibilityService.isSystemUiOverlay(context, null, null))
        assertFalse(AccessibilityService.isSystemUiOverlay(context, "", null))
    }
}
