package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The list of applications currently showing a notification is one string, each package followed by
 * a comma, and it decides whether "do not freeze applications that are notifying" lets a freeze
 * through. A batch used to take packages out of it one at a time, each with its own round trip to
 * Tray's content provider; it is one pass now, and this is the part of that pass worth pinning
 * down, because a package wrongly left in the list silently stops being freezable.
 *
 * Both directions of that one string are checked here. Removal used `String.replace(pkgName + ",")`
 * and addition used `notifying + pkgName + ","` guarded by a `contains` test, so a shorter name cut
 * a piece out of a longer one, and a name added to a list without a trailing comma glued itself onto
 * the last entry.
 */
class NotifyingListTest {

    private fun remove(list: String, vararg packages: String) =
        NotificationUtils.removeFromNotifyingList(list, packages.toList())

    private fun add(list: String?, pkgName: String?) =
        NotificationUtils.addToNotifyingList(list, pkgName)

    @Test
    fun `a whole batch leaves in one pass`() {
        assertEquals(
            "com.c,",
            remove("com.a,com.b,com.c,", "com.a", "com.b")
        )
    }

    @Test
    fun `a package that is not in the list changes nothing`() {
        assertEquals("com.a,", remove("com.a,", "com.b"))
    }

    @Test
    fun `a package whose name starts another package's name is not touched`() {
        // "com.foo" must not take "com.foo.bar" with it: entries are parsed and compared whole.
        assertEquals("com.foo.bar,", remove("com.foo.bar,com.foo,", "com.foo"))
        assertEquals("com.foo.bar,", remove("com.foo.bar,", "com.foo"))
    }

    @Test
    fun `the longer name is removed without disturbing the shorter one`() {
        assertEquals("com.foo,", remove("com.foo,com.foo.bar,", "com.foo.bar"))
    }

    @Test
    fun `removing everything empties the list`() {
        assertEquals("", remove("com.a,com.b,", "com.a", "com.b"))
    }

    @Test
    fun `an empty or absent list survives being asked`() {
        assertEquals("", remove("", "com.a"))
        assertEquals("", NotificationUtils.removeFromNotifyingList(null, listOf("com.a")))
    }

    @Test
    fun `an empty batch returns the list untouched`() {
        val list = "com.a,com.b,"
        // Same instance: nothing was rebuilt, so the caller will not write it back either.
        assertSame(list, NotificationUtils.removeFromNotifyingList(list, emptyList()))
    }

    @Test
    fun `a duplicated entry is removed completely`() {
        assertEquals("com.b,", remove("com.a,com.b,com.a,", "com.a"))
    }

    @Test
    fun `a name that merely contains the removed package keeps its shape`() {
        // "com.foo," is a substring of "xcom.foo,", so the old replace() cut a piece out of the
        // longer name and left "x," behind as a package that is still showing a notification.
        assertEquals("com.barfoo,", remove("com.barfoo,", "com.foo"))
        assertEquals("com.barfoo,", remove("com.barfoo,com.foo,", "com.foo"))
        assertEquals("xcom.foo,", remove("xcom.foo,com.foo,", "com.foo"))
        assertEquals("com.foo,", remove("com.foo,", "foo"))
    }

    @Test
    fun `removal normalises the padded list it was handed`() {
        assertEquals("com.a,", remove("com.a, com.b ,", "com.b"))
        // A name padded inside the batch is the same package.
        assertEquals("com.a,", remove("com.a,com.b,", " com.b "))
    }

    @Test
    fun `adding to an empty or missing list starts one`() {
        assertEquals("com.a,", add("", "com.a"))
        assertEquals("com.a,", add(null, "com.a"))
        assertEquals("com.a,", add("   ", "com.a"))
        assertEquals("", add(null, ""))
    }

    @Test
    fun `adding to a list without a trailing comma does not glue two names into one`() {
        assertEquals("com.a,com.b,", add("com.a", "com.b"))
        assertEquals("com.a,com.b,", add("com.a,", "com.b"))
        assertEquals("com.a,com.b,com.c,", add("com.a,com.b", "com.c"))
    }

    @Test
    fun `adding normalises a padded list`() {
        assertEquals("com.a,com.b,", add(" com.a , ", "com.b"))
        assertEquals("com.a,com.b,", add("com.a", " com.b "))
    }

    @Test
    fun `a package already notifying is not added twice`() {
        assertEquals("com.a,com.b,", add("com.a,com.b,", "com.a"))
        assertEquals("com.a,com.b,", add("com.a, com.b ", "com.b"))
        assertEquals("com.a,", add(add("", "com.a"), "com.a"))
    }

    @Test
    fun `a name that merely contains this package does not block the add`() {
        // contains(pkgName + ",") read "com.foo," inside "xcom.foo," and refused to record a package
        // that really is notifying, which left it freezable with a notification on screen.
        assertEquals("xcom.foo,com.foo,", add("xcom.foo,", "com.foo"))
        assertEquals("com.foo,com.foo.bar,", add("com.foo,", "com.foo.bar"))
    }

    @Test
    fun `a blank name adds nothing`() {
        assertEquals("com.a,", add("com.a,", ""))
        assertEquals("com.a,", add("com.a,", "   "))
        assertEquals("com.a,", add("com.a,", null))
    }

    @Test
    fun `what add records, removal takes back out`() {
        val added = add("com.a", "com.b")

        assertEquals("com.a,com.b,", added)
        assertEquals("com.a,", remove(added, "com.b"))
        assertEquals("com.b,", remove(added, "com.a"))
        assertEquals("", remove(added, "com.a", "com.b"))
    }
}
