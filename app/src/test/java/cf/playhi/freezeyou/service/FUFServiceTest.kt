package cf.playhi.freezeyou.service

import android.app.Activity
import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import cf.playhi.freezeyou.DeviceAdminReceiver
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_LEGACY_AUTO
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_MROOT_DPM
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_MROOT_PROFILE_OWNER
import cf.playhi.freezeyou.fuf.FUFSinglePackage.Companion.API_FREEZEYOU_ROOT_DISABLE_ENABLE
import cf.playhi.freezeyou.storage.key.DefaultMultiProcessMMKVStorageStringKeys.selectFUFMode
import cf.playhi.freezeyou.utils.FUFUtils
import cf.playhi.freezeyou.utils.ShadowContractMMKV
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34], application = Application::class,
    shadows = [ShadowContractMMKV::class, ShadowServiceActions::class],
    instrumentedPackages = [
        "com.tencent.mmkv", "cf.playhi.freezeyou.utils.FUFUtils"
    ]
)
class FUFServiceTest {
    private lateinit var context: Application
    private lateinit var admin: ComponentName
    private lateinit var policy: DevicePolicyManager
    private lateinit var controller: ServiceController<FUFService>
    private lateinit var executor: ExecutorService
    private val packageName = "com.example.single.target"

    @Before
    fun setUp() {
        ShadowContractMMKV.reset()
        ShadowServiceActions.reset()
        context = RuntimeEnvironment.getApplication()
        admin = ComponentName(context, DeviceAdminReceiver::class.java)
        policy = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            this.packageName = this@FUFServiceTest.packageName
        })
        selectFUFMode.setValue(null, API_FREEZEYOU_LEGACY_AUTO.toString())
        controller = Robolectric.buildService(FUFService::class.java).create()
        executor = ReflectionHelpers.getField(controller.get(), "mExecutor")
    }

    @After
    fun tearDown() {
        if (::controller.isInitialized) controller.destroy()
        if (::executor.isInitialized) {
            try {
                assertTrue("Service worker did not terminate", executor.awaitTermination(5, TimeUnit.SECONDS))
            } finally {
                executor.shutdownNow()
            }
        }
        ShadowServiceActions.reset()
        ShadowContractMMKV.reset()
    }

    @Test
    fun `legacy single freeze uses the profile owner api without device ownership`() {
        shadowOf(policy).setProfileOwner(admin)
        assertTrue(policy.isProfileOwnerApp(context.packageName))
        assertFalse(policy.isDeviceOwnerApp(context.packageName))
        assertRoute(freeze = true, expectedApi = API_FREEZEYOU_MROOT_PROFILE_OWNER)
    }

    @Test
    fun `legacy single unfreeze of a hidden package uses the profile owner api`() {
        shadowOf(policy).setProfileOwner(admin)
        assertTrue(policy.setApplicationHidden(admin, packageName, true))
        assertTrue(policy.isApplicationHidden(admin, packageName))
        assertFalse(policy.isDeviceOwnerApp(context.packageName))
        assertRoute(freeze = false, expectedApi = API_FREEZEYOU_MROOT_PROFILE_OWNER)
    }

    @Test
    fun `device owner single freeze still uses the device owner api`() {
        shadowOf(policy).setDeviceOwner(admin)
        assertRoute(freeze = true, expectedApi = API_FREEZEYOU_MROOT_DPM)
    }

    @Test
    fun `device owner single unfreeze still uses the device owner api`() {
        shadowOf(policy).setDeviceOwner(admin)
        assertTrue(policy.setApplicationHidden(admin, packageName, true))
        assertRoute(freeze = false, expectedApi = API_FREEZEYOU_MROOT_DPM)
    }

    @Test
    fun `device owner takes precedence when both owner roles match`() {
        shadowOf(policy).setProfileOwner(admin)
        shadowOf(policy).setDeviceOwner(admin)
        assertRoute(freeze = true, expectedApi = API_FREEZEYOU_MROOT_DPM)
        assertTrue(policy.setApplicationHidden(admin, packageName, true))
        assertRoute(freeze = false, expectedApi = API_FREEZEYOU_MROOT_DPM)
    }

    @Test
    fun `profile owner does not override an explicitly selected root mode`() {
        shadowOf(policy).setProfileOwner(admin)
        selectFUFMode.setValue(null, API_FREEZEYOU_ROOT_DISABLE_ENABLE.toString())
        assertRoute(freeze = true, expectedApi = API_FREEZEYOU_ROOT_DISABLE_ENABLE)
        assertRoute(freeze = false, expectedApi = API_FREEZEYOU_ROOT_DISABLE_ENABLE)
    }

    @Test
    fun `profile owner unfreeze falls back to root when the package is not hidden`() {
        shadowOf(policy).setProfileOwner(admin)
        assertFalse(policy.isApplicationHidden(admin, packageName))
        assertRoute(freeze = false, expectedApi = API_FREEZEYOU_ROOT_DISABLE_ENABLE)
    }

    @Test
    fun `ownerless legacy single actions retain the root fallback`() {
        assertFalse(policy.isProfileOwnerApp(context.packageName))
        assertFalse(policy.isDeviceOwnerApp(context.packageName))
        assertRoute(freeze = true, expectedApi = API_FREEZEYOU_ROOT_DISABLE_ENABLE)
        assertRoute(freeze = false, expectedApi = API_FREEZEYOU_ROOT_DISABLE_ENABLE)
    }

    private fun assertRoute(freeze: Boolean, expectedApi: Int) {
        ShadowServiceActions.calls.clear()
        val intent = Intent(context, FUFService::class.java)
            .putExtra("single", true)
            .putExtra("freeze", freeze)
            .putExtra("pkgName", packageName)
            .putExtra("target", FUFUtils.ONLY_UNFREEZE_TARGET)
            .putExtra("tasks", "17,23,")
            .putExtra("askRun", true)
            .putExtra("runImmediately", !freeze)

        controller.get().onStartCommand(intent, 0, 1)
        // A queued sentinel waits for the real service worker without replacing its executor.
        executor.submit {}.get(5, TimeUnit.SECONDS)

        assertEquals(1, ShadowServiceActions.calls.size)
        val call = ShadowServiceActions.calls.single()
        assertEquals(packageName, call.packageName)
        assertEquals(expectedApi, call.apiMode)
        assertEquals(!freeze, call.enable)
        assertTrue(call.askRun)
        assertEquals(FUFUtils.ONLY_UNFREEZE_TARGET, call.target)
        assertEquals("17,23,", call.tasks)
        assertEquals(!freeze, call.runImmediately)
        assertFalse(call.finish)
        assertNull(call.activity)
    }
}

@Implements(FUFUtils::class, isInAndroidSdk = false)
class ShadowServiceActions {
    data class Call(
        val packageName: String,
        val apiMode: Int,
        val enable: Boolean,
        val askRun: Boolean,
        val target: String?,
        val tasks: String?,
        val runImmediately: Boolean,
        val activity: Activity?,
        val finish: Boolean
    )

    companion object {
        val calls = ConcurrentLinkedQueue<Call>()

        @JvmStatic
        @Resetter
        fun reset() = calls.clear()

        @JvmStatic
        @JvmOverloads
        @Implementation
        fun processAction(
            context: Context, pkgName: String, apiMode: Int,
            enable: Boolean, showUnnecessaryToast: Boolean,
            askRun: Boolean, target: String?, tasks: String?,
            runImmediately: Boolean, activity: Activity?, finish: Boolean,
            skipQuickNotification: Boolean = false
        ): Boolean {
            calls.add(Call(pkgName, apiMode, enable, askRun, target, tasks, runImmediately, activity, finish))
            return true
        }
    }
}
