package cf.playhi.freezeyou.utils

import android.app.Application
import cf.playhi.freezeyou.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Three of the filters on the main screen are a one-key list shown as a list of apps, and a custom
 * category is a fourth. Their rows are read from storage only while the list is being built, so a
 * package added to or removed from the list that is on screen leaves the screen contradicting the
 * toast next to it: "Removed" is shown and the row stays until the filter is switched. Rebuilding
 * fixes that, and rebuilding a list that does not read the changed storage costs a full pass over
 * the installed packages plus the scroll position - so the decision of whether a change belongs to
 * what is on screen is the whole fix, and it is what is pinned here.
 *
 * The keys are read from resources rather than written out, because the filter and the key are two
 * names for one list and it is the pairing that can silently drift: rename a key and the filter
 * keeps rebuilding for a list nobody writes to, while the list that is written to stops rebuilding.
 * That needs a real Context, hence Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class OneKeyFilterMatchingTest {

    private lateinit var context: Application
    private lateinit var oneKeyFreezeKey: String
    private lateinit var oneKeyUFKey: String
    private lateinit var freezeOnceQuitKey: String

    @Before
    fun readTheThreeListKeys() {
        context = RuntimeEnvironment.getApplication()
        oneKeyFreezeKey = context.getString(R.string.sAutoFreezeApplicationList)
        oneKeyUFKey = context.getString(R.string.sOneKeyUFApplicationList)
        freezeOnceQuitKey = context.getString(R.string.sFreezeOnceQuit)
    }

    @Test
    fun `the one-key freeze filter belongs to the one-key freeze list alone`() {
        assertTrue(OneKeyListUtils.isOneKeyFilterAffected("OO", oneKeyFreezeKey, context))
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected("OO", oneKeyUFKey, context))
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected("OO", freezeOnceQuitKey, context))
    }

    @Test
    fun `the one-key unfreeze filter belongs to the one-key unfreeze list alone`() {
        assertTrue(OneKeyListUtils.isOneKeyFilterAffected("OOU", oneKeyUFKey, context))
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected("OOU", oneKeyFreezeKey, context))
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected("OOU", freezeOnceQuitKey, context))
    }

    @Test
    fun `the freeze once quit filter belongs to the freeze once quit list alone`() {
        assertTrue(OneKeyListUtils.isOneKeyFilterAffected("FOQ", freezeOnceQuitKey, context))
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected("FOQ", oneKeyFreezeKey, context))
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected("FOQ", oneKeyUFKey, context))
    }

    @Test
    fun `a filter that reads no one-key list is left alone by all three`() {
        val filters = listOf("all", "OF", "UF", "OS", "OUS", "OU", "UFU", "RUN", "RUN_SYS", "RUN_USER")

        for (filter in filters) {
            assertFalse(filter, OneKeyListUtils.isOneKeyFilterAffected(filter, oneKeyFreezeKey, context))
            assertFalse(filter, OneKeyListUtils.isOneKeyFilterAffected(filter, oneKeyUFKey, context))
            assertFalse(filter, OneKeyListUtils.isOneKeyFilterAffected(filter, freezeOnceQuitKey, context))
        }
    }

    @Test
    fun `a package list on screen is not a one-key filter, whatever changed`() {
        // A custom category and a shortcut both reach the screen as the packages they name.
        val packageList = "com.a,com.b,"

        assertFalse(OneKeyListUtils.isOneKeyFilterAffected(packageList, oneKeyFreezeKey, context))
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected(packageList, oneKeyUFKey, context))
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected(packageList, freezeOnceQuitKey, context))
    }

    @Test
    fun `only the filter itself matches, never something that looks like it`() {
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected("oo", oneKeyFreezeKey, context))
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected("OO ", oneKeyFreezeKey, context))
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected("O", oneKeyFreezeKey, context))
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected("OOU", oneKeyFreezeKey + " ", context))
    }

    @Test
    fun `with nothing to compare, nothing is affected`() {
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected(null, oneKeyFreezeKey, context))
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected("OO", null, context))
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected(null, null, context))
        assertFalse(OneKeyListUtils.isOneKeyFilterAffected("OO", oneKeyFreezeKey, null))
    }

    @Test
    fun `the category on screen is the one just changed`() {
        val label = "VGVzdA==\n"

        assertTrue(OneKeyListUtils.isCategoryFilterAffected(label, label))
    }

    @Test
    fun `another category changing leaves the one on screen alone`() {
        assertFalse(OneKeyListUtils.isCategoryFilterAffected("VGVzdA==\n", "T3RoZXI=\n"))
    }

    @Test
    fun `a label is compared as stored, not as the text it decodes to`() {
        // Base64.DEFAULT ends the label with a line feed, and that is what the database holds.
        assertFalse(OneKeyListUtils.isCategoryFilterAffected("VGVzdA==\n", "VGVzdA=="))
        assertFalse(OneKeyListUtils.isCategoryFilterAffected("VGVzdA==", "Test"))
    }

    @Test
    fun `no category on screen is never the one that changed`() {
        assertFalse(OneKeyListUtils.isCategoryFilterAffected(null, "VGVzdA==\n"))
        assertFalse(OneKeyListUtils.isCategoryFilterAffected(null, null))
        assertFalse(OneKeyListUtils.isCategoryFilterAffected("VGVzdA==\n", null))
    }
}
