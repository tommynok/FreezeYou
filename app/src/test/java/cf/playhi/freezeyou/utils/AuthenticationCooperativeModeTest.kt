package cf.playhi.freezeyou.utils

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [34], application = Application::class,
    shadows = [ShadowContractMMKV::class],
    instrumentedPackages = ["com.tencent.mmkv"]
)
class AuthenticationCooperativeModeTest {

    @Before
    fun resetStorage() {
        ShadowContractMMKV.reset()
    }

    @Test
    fun `default primary method is biometric when no code is set`() {
        assertEquals(
            AuthenticationUtils.AUTH_METHOD_BIOMETRIC,
            AuthenticationUtils.getPrimaryAuthenticationMethod()
        )
    }

    @Test
    fun `legacy migration preserves app code as primary when code exists but no key stored`() {
        AppCodeUtils.setCode("1234")
        assertTrue(AppCodeUtils.hasCode())
        // No explicit primaryAuthMethod stored yet
        assertEquals(
            AuthenticationUtils.AUTH_METHOD_APP_CODE,
            AuthenticationUtils.getPrimaryAuthenticationMethod()
        )
    }

    @Test
    fun `cooperative mode allows biometric primary while app code is set`() {
        AppCodeUtils.setCode("1234")
        assertTrue(AppCodeUtils.hasCode())

        AuthenticationUtils.setPrimaryAuthenticationMethod(
            AuthenticationUtils.AUTH_METHOD_BIOMETRIC
        )

        // Both coexist: app code exists, but primary is explicitly biometric
        assertEquals(
            AuthenticationUtils.AUTH_METHOD_BIOMETRIC,
            AuthenticationUtils.getPrimaryAuthenticationMethod()
        )
        assertTrue(AppCodeUtils.hasCode())
    }

    @Test
    fun `explicit app code selection is preserved`() {
        AppCodeUtils.setCode("5678")
        AuthenticationUtils.setPrimaryAuthenticationMethod(
            AuthenticationUtils.AUTH_METHOD_APP_CODE
        )

        assertEquals(
            AuthenticationUtils.AUTH_METHOD_APP_CODE,
            AuthenticationUtils.getPrimaryAuthenticationMethod()
        )
    }

    @Test
    fun `clearing code falls back safely to biometric even if app code was primary`() {
        AppCodeUtils.setCode("1234")
        AuthenticationUtils.setPrimaryAuthenticationMethod(
            AuthenticationUtils.AUTH_METHOD_APP_CODE
        )
        assertEquals(
            AuthenticationUtils.AUTH_METHOD_APP_CODE,
            AuthenticationUtils.getPrimaryAuthenticationMethod()
        )

        AppCodeUtils.clearCode()
        assertFalse(AppCodeUtils.hasCode())

        assertEquals(
            AuthenticationUtils.AUTH_METHOD_BIOMETRIC,
            AuthenticationUtils.getPrimaryAuthenticationMethod()
        )
    }
}
