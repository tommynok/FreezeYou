package cf.playhi.freezeyou.export

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import cf.playhi.freezeyou.DeviceAdminReceiver
import cf.playhi.freezeyou.fuf.FUFSinglePackage
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
import org.robolectric.annotation.Config

/**
 * The v1 providers checked only isDeviceOwner. On a profile owner the legacy query reported the
 * mode as root or unavailable, and the legacy auto action fell through to root: with root present
 * it disabled an application the profile owner had already hidden, and without root it failed.
 * The same check chose the device-owner-only API for an explicit DPM request, so a profile owner
 * could not undo what it had hidden.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ExportV1ProfileOwnerTest {

    private lateinit var context: Application
    private lateinit var admin: ComponentName

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        admin = ComponentName(context, DeviceAdminReceiver::class.java)
    }

    @Test
    fun `a profile owner is reported as dpm by the legacy query`() {
        shadowOf(devicePolicyManager()).setProfileOwner(admin)

        assertTrue(Query.isDpmOwner(context))
        assertEquals("dpm", queryMode())
    }

    @Test
    fun `a device owner is still reported as dpm by the legacy query`() {
        shadowOf(devicePolicyManager()).setDeviceOwner(admin)

        assertTrue(Query.isDpmOwner(context))
        assertEquals("dpm", queryMode())
    }

    @Test
    fun `an ownerless device is not dpm`() {
        assertFalse(Query.isDpmOwner(context))
    }

    @Test
    fun `a profile owner freezes through the profile owner api mode`() {
        assertEquals(
            FUFSinglePackage.API_FREEZEYOU_MROOT_PROFILE_OWNER,
            resolveAutoDpmApiMode(deviceOwner = false, profileOwner = true)
        )
    }

    @Test
    fun `a device owner freezes through the dpm api mode`() {
        assertEquals(
            FUFSinglePackage.API_FREEZEYOU_MROOT_DPM,
            resolveAutoDpmApiMode(deviceOwner = true, profileOwner = false)
        )
    }

    @Test
    fun `the device owner mode wins when both owner roles match`() {
        assertEquals(
            FUFSinglePackage.API_FREEZEYOU_MROOT_DPM,
            resolveAutoDpmApiMode(deviceOwner = true, profileOwner = true)
        )
    }

    @Test
    fun `without an owner the legacy auto action falls back to root`() {
        assertNull(resolveAutoDpmApiMode(deviceOwner = false, profileOwner = false))
    }

    @Test
    fun `a profile owner resolves to the profile owner api from a context`() {
        shadowOf(devicePolicyManager()).setProfileOwner(admin)

        assertEquals(
            FUFSinglePackage.API_FREEZEYOU_MROOT_PROFILE_OWNER,
            resolveOwnerApiMode(context)
        )
    }

    @Test
    fun `a device owner resolves to the dpm api from a context`() {
        shadowOf(devicePolicyManager()).setDeviceOwner(admin)

        assertEquals(FUFSinglePackage.API_FREEZEYOU_MROOT_DPM, resolveOwnerApiMode(context))
    }

    @Test
    fun `an ownerless device has no owner api mode`() {
        assertNull(resolveOwnerApiMode(context))
    }

    private fun devicePolicyManager(): DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private fun queryMode(): String? {
        val provider = Robolectric.buildContentProvider(Query::class.java).create().get()
        return provider.call("QUERY_MODE", null, Bundle()).getString("currentMode")
    }
}
