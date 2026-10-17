package cf.playhi.freezeyou.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The OneKEY lists are stored as one comma separated string, and what comes back out of it decides
 * what gets frozen when the screen goes off. An empty preference used to be turned into a batch of
 * one empty package name, which reported success for a freeze that never happened; a trailing
 * comma or a stray space would have done the same. These are the shapes a stored list actually
 * takes after a user edits it by hand.
 */
class PackageListTest {

    @Test
    fun `no preference and an empty preference both yield no packages`() {
        assertTrue(PackageListUtils.parse(null).isEmpty())
        assertTrue(PackageListUtils.parse("").isEmpty())
        // A field cleared to spaces or tabs is as empty as one cleared to nothing.
        assertTrue(PackageListUtils.parse("   ").isEmpty())
        assertTrue(PackageListUtils.parse(" \t \n ").isEmpty())
    }

    @Test
    fun `a list of nothing but separators yields no packages`() {
        assertTrue(PackageListUtils.parse(",").isEmpty())
        assertTrue(PackageListUtils.parse(",,,").isEmpty())
        assertTrue(PackageListUtils.parse(",,,, ").isEmpty())
        // Separators with nothing but padding between them are still only separators.
        assertTrue(PackageListUtils.parse(" , , , ").isEmpty())
    }

    @Test
    fun `a trailing comma does not become an empty package name`() {
        assertArrayEquals(
            arrayOf("com.example.one", "com.example.two"),
            PackageListUtils.parse("com.example.one,com.example.two,")
        )
    }

    @Test
    fun `names are trimmed, because a padded name addresses nothing`() {
        assertArrayEquals(
            arrayOf("com.example.one", "com.example.two"),
            PackageListUtils.parse(" com.example.one , com.example.two ")
        )
    }

    @Test
    fun `order is kept as stored`() {
        assertArrayEquals(
            arrayOf("c", "a", "b"),
            PackageListUtils.parse("c,a,b")
        )
    }

    @Test
    fun `a list of one app is a batch of one app`() {
        val parsed = PackageListUtils.parse("com.example.app")

        assertEquals(1, parsed.size)
        assertArrayEquals(arrayOf("com.example.app"), parsed)
    }

    @Test
    fun `leading and doubled commas drop out beside a trailing one`() {
        assertArrayEquals(
            arrayOf("com.example.one", "com.example.two"),
            PackageListUtils.parse(",com.example.one,,com.example.two,")
        )
        assertArrayEquals(
            arrayOf("com.example.one", "com.example.two"),
            PackageListUtils.parse(",,,com.example.one,,,,com.example.two,,")
        )
        // A padded gap between separators is still only a gap, and the names keep their places.
        assertArrayEquals(
            arrayOf("c", "a", "b"),
            PackageListUtils.parse(" , c , , a ,b, ")
        )
    }

    @Test
    fun `tabs and newlines pad a name like spaces do`() {
        assertArrayEquals(
            arrayOf("com.example.one", "com.example.two"),
            PackageListUtils.parse("\tcom.example.one\t,\ncom.example.two\n")
        )
    }

    /**
     * Editing the field twice leaves a name in the list twice, and both copies stay: the parser
     * cleans the string, it does not decide what the batch means. Pinned so a later dedup, if it
     * ever happens, is a deliberate change rather than an accident.
     */
    @Test
    fun `a name entered twice is kept twice`() {
        val parsed = PackageListUtils.parse("com.example.one,com.example.one")

        assertEquals(2, parsed.size)
        assertArrayEquals(arrayOf("com.example.one", "com.example.one"), parsed)

        assertArrayEquals(
            arrayOf("com.example.one", "com.example.two", "com.example.one"),
            PackageListUtils.parse("com.example.one,com.example.two,com.example.one")
        )
        // Padding does not hide a repeat: " one " and "one" name the same app, and both stay.
        assertArrayEquals(
            arrayOf("com.example.one", "com.example.one"),
            PackageListUtils.parse(" com.example.one , com.example.one ")
        )
    }
}
