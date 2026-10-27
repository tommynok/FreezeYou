package cf.playhi.freezeyou.service

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import cf.playhi.freezeyou.utils.InstallPackagesUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The "install when not using" request used to live only in a static field, so a killed
 * `:backgroundService` left the waiting card behind and the approved install never happened.
 * The request is now encoded into the multi-process store the accessibility service reads back
 * after the user leaves the target application. The record has to round-trip every field the
 * service needs, reject anything malformed, and refuse an install whose staged APK is gone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WaitingInstallPersistenceTest {

    private fun request(
        packageName: String = "com.example.target",
        install: Boolean = true,
        apkFilePath: String? = null,
        packageUri: String? = null,
        uninstallUpdatesOnly: Boolean = false
    ) = InstallPackagesUtils.WaitingInstallRequest(
        packageName, install, apkFilePath, packageUri, uninstallUpdatesOnly
    )

    @Test
    fun `an install request keeps every field across encode and decode`() {
        val original = request(
            apkFilePath = "/cache/ZDF-package1F.apk",
            packageUri = "content://provider/apk"
        )

        val restored = InstallPackagesUtils.WaitingInstallRequest.decode(original.encode())

        assertTrue(restored != null)
        assertEquals(original.requestId, restored!!.requestId)
        assertEquals("com.example.target", restored.packageName)
        assertTrue(restored.install)
        assertEquals("/cache/ZDF-package1F.apk", restored.apkFilePath)
        assertEquals("content://provider/apk", restored.packageUri)
        assertFalse(restored.uninstallUpdatesOnly)
    }

    @Test
    fun `an uninstall request keeps its nulls and its flags`() {
        val original = request(
            install = false,
            packageUri = "package:com.example.target",
            uninstallUpdatesOnly = true
        )

        val restored = InstallPackagesUtils.WaitingInstallRequest.decode(original.encode())

        assertTrue(restored != null)
        assertFalse(restored!!.install)
        assertNull(restored.apkFilePath)
        assertEquals("package:com.example.target", restored.packageUri)
        assertTrue(restored.uninstallUpdatesOnly)
    }

    @Test
    fun `a malformed or unknown record is rejected`() {
        assertNull(InstallPackagesUtils.WaitingInstallRequest.decode(null))
        assertNull(InstallPackagesUtils.WaitingInstallRequest.decode(""))
        assertNull(InstallPackagesUtils.WaitingInstallRequest.decode("not a record"))
        assertNull(
            InstallPackagesUtils.WaitingInstallRequest.decode(
                "3\u0000id\u0000com.example.target\u00001\u0000\u0000\u00000"
            )
        )
        assertNull(
            InstallPackagesUtils.WaitingInstallRequest.decode(
                "2\u0000com.example.target\u00001\u0000\u0000\u00000"
            )
        )
        assertNull(
            InstallPackagesUtils.WaitingInstallRequest.decode(
                "2\u0000\u0000com.example.target\u00001\u0000\u0000\u00000"
            )
        )
        assertNull(
            InstallPackagesUtils.WaitingInstallRequest.decode(
                "2\u0000id\u0000\u00001\u0000\u0000\u00000"
            )
        )
    }

    @Test
    fun `an install whose staged APK disappeared is not dispatched`() {
        val app = RuntimeEnvironment.getApplication()
        val present = File(app.cacheDir, "present.apk").apply { createNewFile() }
        val missing = File(app.cacheDir, "missing.apk")

        assertTrue(request(apkFilePath = present.absolutePath).isApkAvailable())
        assertFalse(request(apkFilePath = missing.absolutePath).isApkAvailable())
    }

    @Test
    fun `a content URI install and every uninstall are dispatchable without a staged file`() {
        val missingPath = File(RuntimeEnvironment.getApplication().cacheDir, "missing.apk")

        assertTrue(request(apkFilePath = null, packageUri = "content://provider/apk").isApkAvailable())
        assertTrue(
            request(
                install = false,
                apkFilePath = missingPath.absolutePath,
                packageUri = "package:com.example.target"
            ).isApkAvailable()
        )
    }

    @Test
    fun `the rebuilt intent carries exactly what the install service reads`() {
        val app = RuntimeEnvironment.getApplication()
        val packageInfo = PackageInfo().apply { packageName = "com.example.target" }
        val original = Intent("cf.playhi.freezeyou.action.TEST")
            .putExtra("install", true)
            .putExtra("apkFilePath", "/cache/ZDF-package1F.apk")
            .putExtra("packageUri", Uri.parse("content://provider/apk"))
            .putExtra("packageInfo", packageInfo)
            .putExtra("waitForLeaving", true)

        val rebuilt = InstallPackagesUtils.WaitingInstallRequest
            .fromIntent(original, "com.example.target")
            .toIntent(app)

        assertEquals(InstallPackagesService::class.java.name, rebuilt.component?.className)
        assertTrue(rebuilt.getBooleanExtra("install", false))
        assertEquals("/cache/ZDF-package1F.apk", rebuilt.getStringExtra("apkFilePath"))
        assertEquals(
            Uri.parse("content://provider/apk"),
            rebuilt.getParcelableExtra<Uri>("packageUri")
        )
        assertFalse(rebuilt.getBooleanExtra("uninstall_updates_only", true))
        assertFalse(rebuilt.getBooleanExtra("waitForLeaving", true))
    }

    @Test
    fun `only the dispatched request is eligible for removal`() {
        val stored = request(apkFilePath = "/cache/ZDF-package1F.apk")
        val decoded = InstallPackagesUtils.WaitingInstallRequest.decode(stored.encode())
        val another = request(apkFilePath = "/cache/ZDF-package1F.apk")

        assertTrue(stored.matches(decoded))
        assertFalse(stored.matches(another))
        assertFalse(stored.matches(null))
    }

    @Test
    fun `a request without extras is an install with no staged path`() {
        val rebuilt = InstallPackagesUtils.WaitingInstallRequest
            .fromIntent(Intent(), "com.example.target")
            .toIntent(RuntimeEnvironment.getApplication())

        assertTrue(rebuilt.getBooleanExtra("install", false))
        assertNull(rebuilt.getStringExtra("apkFilePath"))
        assertNull(rebuilt.getParcelableExtra<Uri>("packageUri"))
    }
}
