package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every in-process batch path has to skip FreezeYou itself, silently: a self entry rides in from
 * a restored backup or a hand-edited OneKEY list, and acting on it only ever produces the -11
 * rejection and a failure toast naming FreezeYou. The same helper collapses a package listed twice
 * into one target — a task such as `ff com.example,@Category` where the category already holds
 * that package would otherwise act on it twice, count it twice in the statistics and run its
 * trigger tasks twice. The loops that use the rule sit behind MMKV and Android toasts, so the list
 * decision is kept pure and tested here; runRootBatch has gone through this helper too.
 */
class BatchSelfSkipTest {

    private val self = "cf.playhi.freezeyou"

    private fun targetsOf(vararg packages: String) =
        FUFUtils.batchTargetsWithoutSelf(self, arrayOf(*packages))

    @Test
    fun `self is dropped wherever it sits in the list`() {
        assertEquals(listOf("com.a", "com.b"), targetsOf(self, "com.a", "com.b"))
        assertEquals(listOf("com.a", "com.b"), targetsOf("com.a", self, "com.b"))
        assertEquals(listOf("com.a", "com.b"), targetsOf("com.a", "com.b", self))
    }

    @Test
    fun `a batch of only self has no targets left`() {
        // The loops return on an empty target list: a freeze of nothing must not report success.
        assertTrue(targetsOf(self).isEmpty())
        assertTrue(targetsOf(self, self).isEmpty())
    }

    @Test
    fun `everything else keeps its order and deduplicates`() {
        assertEquals(listOf("com.b", "com.a"), targetsOf("com.b", "com.a", "com.b"))
    }

    @Test
    fun `multiple duplicates preserve order of first appearance`() {
        assertEquals(
            listOf("com.c", "com.a", "com.b"),
            targetsOf("com.c", "com.a", "com.c", "com.b", "com.a", self)
        )
    }

    @Test
    fun `only the exact id is self, never a name that contains it`() {
        assertEquals(
            listOf("cf.playhi.freezeyou.pro", "x_cf.playhi.freezeyou"),
            targetsOf("cf.playhi.freezeyou.pro", "x_cf.playhi.freezeyou")
        )
    }

    @Test
    fun `a fork recognises itself by the id it runs under, not the upstream name`() {
        // Under another applicationId the fork must skip its own id, while the upstream package
        // is just one more app on the device.
        assertEquals(
            listOf(self),
            FUFUtils.batchTargetsWithoutSelf("com.example.fork", arrayOf("com.example.fork", self))
        )
    }

    @Test
    fun `an empty batch stays empty`() {
        assertTrue(targetsOf().isEmpty())
    }
}
