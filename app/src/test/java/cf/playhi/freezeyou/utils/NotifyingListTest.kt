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
 */
class NotifyingListTest {

    private fun remove(list: String, vararg packages: String) =
        NotificationUtils.removeFromNotifyingList(list, packages.toList())

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
        // "com.foo" must not take "com.foo.bar" with it: the stored comma is what separates them.
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
}
