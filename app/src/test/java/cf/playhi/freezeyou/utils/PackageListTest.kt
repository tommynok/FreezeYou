package cf.playhi.freezeyou.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
        assertEquals(0, PackageListUtils.parse(null).size)
        assertEquals(0, PackageListUtils.parse("").size)
    }

    @Test
    fun `a list of nothing but separators yields no packages`() {
        assertEquals(0, PackageListUtils.parse(",").size)
        assertEquals(0, PackageListUtils.parse(",,,").size)
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
}
