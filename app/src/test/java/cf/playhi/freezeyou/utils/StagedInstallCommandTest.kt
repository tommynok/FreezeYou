package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The privileged install command stages the APK into /data/local/tmp because system_server
 * cannot read the app's external cache since Android 11, and the staged copy must be removed
 * whether the install succeeded or not. The command shape is pinned here: copy first, make the
 * copy readable, install from the staged path, keep the install exit code across the cleanup.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StagedInstallCommandTest {

    @Test
    fun `command copies the source to the staged path before installing`() {
        val cmd = PrivilegedShellUtils.buildStagedInstallCommand(
            "/a/b.apk", "/data/local/tmp/freezeyou-1.apk"
        )
        assertTrue(
            cmd.startsWith(
                "cp '/a/b.apk' '/data/local/tmp/freezeyou-1.apk'"
                        + " && chmod 644 '/data/local/tmp/freezeyou-1.apk'"
                        + " && pm install -r '/data/local/tmp/freezeyou-1.apk'"
            )
        )
    }

    @Test
    fun `command removes the staged copy and reports the install exit code`() {
        val cmd = PrivilegedShellUtils.buildStagedInstallCommand(
            "/a/b.apk", "/data/local/tmp/freezeyou-1.apk"
        )
        assertTrue(
            cmd.endsWith(
                "; rc=$?; rm -f '/data/local/tmp/freezeyou-1.apk'; exit \$rc"
            )
        )
    }

    @Test
    fun `command quotes a single quote in the source path`() {
        val cmd = PrivilegedShellUtils.buildStagedInstallCommand(
            "/a/b'c.apk", "/data/local/tmp/freezeyou-1.apk"
        )
        assertTrue(cmd.startsWith("cp '/a/b'\\''c.apk' "))
    }

    @Test
    fun `command quotes a single quote in the staged path`() {
        val cmd = PrivilegedShellUtils.buildStagedInstallCommand(
            "/a/b.apk", "/data/local/tmp/freezeyou'1.apk"
        )
        assertEquals(
            "cp '/a/b.apk' '/data/local/tmp/freezeyou'\\''1.apk'"
                    + " && chmod 644 '/data/local/tmp/freezeyou'\\''1.apk'"
                    + " && pm install -r '/data/local/tmp/freezeyou'\\''1.apk'"
                    + "; rc=$?; rm -f '/data/local/tmp/freezeyou'\\''1.apk'; exit \$rc",
            cmd
        )
    }
}
