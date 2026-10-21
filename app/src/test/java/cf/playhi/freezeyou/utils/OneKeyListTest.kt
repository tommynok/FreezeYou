package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A OneKEY list is one comma separated string in preferences, and these three functions decide
 * whether an app is on it, how the string grows when one joins, and what it looks like after an
 * app leaves. Getting it wrong is not cosmetic: `existsInOneKeyList` compared the raw comma-split
 * entries, so a list stored as `"com.a, com.b"` answered "not on the list" for `com.b`, and the
 * menu then offered *Add* for an app that was already frozen. Adding concatenated, so a list
 * hand-edited into `"com.a"` grew into `"com.acom.b,"` - one package name belonging to neither app.
 * Removal used `String.replace(pkgName + ",")`, which left the last entry of a comma-less list in
 * place and matched substrings, so removing `com.a` from `"xcom.a,"` stored `x` as a package name.
 *
 * Only the string functions are covered here. The `Context` overloads read and write the
 * multi-process store, and a unit test that needs a database or SharedPreferences proves less than
 * the same rules checked on a string.
 */
class OneKeyListTest {

    @Test
    fun `a padded entry is still the app it names`() {
        assertTrue(OneKeyListUtils.existsInOneKeyList("com.a, com.b ", "com.b"))
        assertTrue(OneKeyListUtils.existsInOneKeyList("com.a, com.b ", "com.a"))
        assertTrue(OneKeyListUtils.existsInOneKeyList(" , com.a , com.b , ", "com.a"))
    }

    @Test
    fun `an app that is not on the list is not found, however the list is padded`() {
        assertFalse(OneKeyListUtils.existsInOneKeyList("com.a, com.b", "com.c"))
        assertFalse(OneKeyListUtils.existsInOneKeyList("com.a", "com.b"))
    }

    @Test
    fun `empty sections name no package`() {
        assertTrue(OneKeyListUtils.existsInOneKeyList(",,com.a,", "com.a"))
        assertFalse(OneKeyListUtils.existsInOneKeyList(",,com.a,", ""))
        assertFalse(OneKeyListUtils.existsInOneKeyList(",,,", "com.a"))
        assertFalse(OneKeyListUtils.existsInOneKeyList("   ", "com.a"))
    }

    @Test
    fun `no preference and an empty preference hold no app`() {
        assertFalse(OneKeyListUtils.existsInOneKeyList(null as String?, "com.a"))
        assertFalse(OneKeyListUtils.existsInOneKeyList("", "com.a"))
    }

    @Test
    fun `only a whole entry matches, never a piece of one`() {
        val list = "com.a.app,com.other,"

        assertTrue(OneKeyListUtils.existsInOneKeyList(list, "com.a.app"))
        assertFalse(OneKeyListUtils.existsInOneKeyList(list, "com.a"))
        assertFalse(OneKeyListUtils.existsInOneKeyList(list, "a.app"))
        assertFalse(OneKeyListUtils.existsInOneKeyList(list, "com.a.ap"))
    }

    @Test
    fun `an entry in the middle is cut out and the neighbours survive`() {
        assertEquals(
            "com.a,com.c,",
            OneKeyListUtils.removePkgFromPkgNames("com.a,com.b,com.c,", "com.b")
        )
    }

    @Test
    fun `the last entry is removed even when the list has no trailing comma`() {
        assertEquals("com.a,", OneKeyListUtils.removePkgFromPkgNames("com.a,com.b", "com.b"))
        assertEquals("com.a,", OneKeyListUtils.removePkgFromPkgNames("com.a,com.b,", "com.b"))
        assertEquals("com.b,", OneKeyListUtils.removePkgFromPkgNames("com.a,com.b", "com.a"))
    }

    @Test
    fun `removing the only entry empties the list`() {
        assertEquals("", OneKeyListUtils.removePkgFromPkgNames("com.a,", "com.a"))
        assertEquals("", OneKeyListUtils.removePkgFromPkgNames("com.a", "com.a"))
        assertEquals("", OneKeyListUtils.removePkgFromPkgNames("  com.a  ", "com.a"))
    }

    @Test
    fun `removing an app that is not on the list changes nothing`() {
        assertEquals("com.a,com.b,", OneKeyListUtils.removePkgFromPkgNames("com.a,com.b,", "com.z"))
        assertEquals("", OneKeyListUtils.removePkgFromPkgNames("", "com.a"))
        assertEquals("", OneKeyListUtils.removePkgFromPkgNames(null, "com.a"))
    }

    @Test
    fun `a name that merely contains the package survives the removal`() {
        // The old replace(pkgName + ",") turned this into "x,", storing "x" as a package name.
        assertEquals("xcom.a,", OneKeyListUtils.removePkgFromPkgNames("xcom.a,com.a,", "com.a"))
        assertEquals("com.a.app,", OneKeyListUtils.removePkgFromPkgNames("com.a.app,com.a,", "com.a"))
        assertEquals("com.a,", OneKeyListUtils.removePkgFromPkgNames("com.a.app,com.a,", "com.a.app"))
        assertEquals("com.a.sub,", OneKeyListUtils.removePkgFromPkgNames("com.a.sub,", "com.a"))
    }

    @Test
    fun `a padded list comes back canonical, with one trailing comma per entry`() {
        assertEquals("com.b,", OneKeyListUtils.removePkgFromPkgNames("  com.a , com.b ", "com.a"))
        assertEquals(
            "com.a,com.c,",
            OneKeyListUtils.removePkgFromPkgNames(",, com.a ,,\tcom.b,, com.c ,,", "com.b")
        )
    }

    @Test
    fun `an entry listed twice is gone from both places`() {
        assertEquals(
            "com.b,",
            OneKeyListUtils.removePkgFromPkgNames("com.a,com.b,com.a", "com.a")
        )
    }

    @Test
    fun `what removal drops, existence stops seeing`() {
        val before = "com.a, com.b,com.c"

        assertTrue(OneKeyListUtils.existsInOneKeyList(before, "com.b"))
        val after = OneKeyListUtils.removePkgFromPkgNames(before, "com.b")
        assertFalse(OneKeyListUtils.existsInOneKeyList(after, "com.b"))
        assertTrue(OneKeyListUtils.existsInOneKeyList(after, "com.a"))
        assertTrue(OneKeyListUtils.existsInOneKeyList(after, "com.c"))
        assertEquals("com.a,com.c,", after)
    }

    @Test
    fun `adding to an empty or missing list starts one`() {
        assertEquals("com.a,", OneKeyListUtils.addPkgToPkgNames("", "com.a"))
        assertEquals("com.a,", OneKeyListUtils.addPkgToPkgNames(null, "com.a"))
        assertEquals("com.a,", OneKeyListUtils.addPkgToPkgNames("   ", "com.a"))
        assertEquals("", OneKeyListUtils.addPkgToPkgNames(null, ""))
    }

    @Test
    fun `adding to a list without a trailing comma does not glue two names into one`() {
        // Plain concatenation stored "com.acom.b," here: one name belonging to neither app.
        assertEquals("com.a,com.b,", OneKeyListUtils.addPkgToPkgNames("com.a", "com.b"))
        assertEquals("com.a,com.b,", OneKeyListUtils.addPkgToPkgNames("com.a,", "com.b"))
        assertEquals("com.a,com.b,com.c,", OneKeyListUtils.addPkgToPkgNames("com.a,com.b", "com.c"))
    }

    @Test
    fun `a padded list is normalised on the way in`() {
        assertEquals("com.a,com.b,", OneKeyListUtils.addPkgToPkgNames(" com.a , ", "com.b"))
        assertEquals("com.a,com.b,", OneKeyListUtils.addPkgToPkgNames(",, com.a ,,", "com.b"))
        assertEquals("com.a,com.b,", OneKeyListUtils.addPkgToPkgNames("com.a", " com.b "))
    }

    @Test
    fun `an app already on the list is not added a second time`() {
        assertEquals("com.a,com.b,", OneKeyListUtils.addPkgToPkgNames("com.a,com.b,", "com.a"))
        assertEquals("com.a,com.b,", OneKeyListUtils.addPkgToPkgNames("com.a, com.b ", "com.b"))
        assertEquals("com.a,com.b,", OneKeyListUtils.addPkgToPkgNames("com.a,com.b", "com.a"))
        // A name padded on one side is the same app, and still not added again.
        assertEquals("com.a,com.b,", OneKeyListUtils.addPkgToPkgNames("com.a,com.b", " com.a "))
    }

    @Test
    fun `a blank name adds nothing`() {
        assertEquals("com.a,", OneKeyListUtils.addPkgToPkgNames("com.a,", ""))
        assertEquals("com.a,", OneKeyListUtils.addPkgToPkgNames("com.a,", "   "))
        assertEquals("com.a,", OneKeyListUtils.addPkgToPkgNames("com.a,", null))
    }

    @Test
    fun `adding the same app twice in a row leaves one entry`() {
        val once = OneKeyListUtils.addPkgToPkgNames("", "com.a")

        assertEquals("com.a,", once)
        assertEquals("com.a,", OneKeyListUtils.addPkgToPkgNames(once, "com.a"))
    }

    @Test
    fun `add only reorders nothing and duplicates what was already duplicated`() {
        assertEquals(
            "com.a,com.a,com.b,",
            OneKeyListUtils.addPkgToPkgNames("com.a,com.a,", "com.b")
        )
    }

    @Test
    fun `what add appends, existence sees and removal takes back out`() {
        val added = OneKeyListUtils.addPkgToPkgNames("com.a", "com.b")

        assertTrue(OneKeyListUtils.existsInOneKeyList(added, "com.a"))
        assertTrue(OneKeyListUtils.existsInOneKeyList(added, "com.b"))
        assertEquals("com.a,", OneKeyListUtils.removePkgFromPkgNames(added, "com.b"))
        assertEquals("com.b,", OneKeyListUtils.removePkgFromPkgNames(added, "com.a"))
    }
}
