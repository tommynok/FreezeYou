package cf.playhi.freezeyou

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Creating shortcuts for a selection asked the launcher to pin one package at a time, and every
 * answer brought {@code Main} back to the foreground. The loop used to count down into
 * {@code selectedPackages} itself, so leaving the selection mode while a pin dialog was still open
 * made the next resume read an empty list and crash the screen. The batch now runs against a
 * snapshot, and these are the two rules that snapshot is built on: where it stops, and what it
 * returns when the caller has walked past its end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MainBatchShortcutTest {

    @Test
    fun `the batch keeps going while the cursor is inside the snapshot`() {
        val snapshot = listOf("cf.playhi.a", "cf.playhi.b", "cf.playhi.c")
        assertTrue(Main.hasPendingShortcuts(snapshot, 0))
        assertTrue(Main.hasPendingShortcuts(snapshot, 2))
    }

    @Test
    fun `the batch ends on the cursor that walked off the snapshot`() {
        val snapshot = listOf("cf.playhi.a", "cf.playhi.b", "cf.playhi.c")
        assertFalse(Main.hasPendingShortcuts(snapshot, snapshot.size))
        assertFalse(Main.hasPendingShortcuts(snapshot, snapshot.size + 1))
        assertFalse(Main.hasPendingShortcuts(snapshot, -1))
    }

    @Test
    fun `no pending shortcut is read out of a missing or empty snapshot`() {
        assertFalse(Main.hasPendingShortcuts(null, 0))
        assertFalse(Main.hasPendingShortcuts(emptyList(), 0))
    }

    @Test
    fun `the batch pins in the order the user selected`() {
        // Counting down from size - 1 pinned the last ticked application first, so the shortcuts
        // landed on the launcher in the reverse of the order the list was read in.
        val snapshot = listOf("cf.playhi.a", "cf.playhi.b", "cf.playhi.c")
        val pinned = ArrayList<String>()
        var index = 0
        while (Main.hasPendingShortcuts(snapshot, index)) {
            pinned.add(Main.getNextPendingShortcutPackage(snapshot, index))
            index++
        }
        assertEquals(listOf("cf.playhi.a", "cf.playhi.b", "cf.playhi.c"), pinned)
    }

    @Test
    fun `a cursor outside the snapshot reports nothing instead of crashing`() {
        val snapshot = listOf("cf.playhi.a")
        assertNull(Main.getNextPendingShortcutPackage(snapshot, 1))
        assertNull(Main.getNextPendingShortcutPackage(snapshot, -1))
        assertNull(Main.getNextPendingShortcutPackage(null, 0))
        assertNull(Main.getNextPendingShortcutPackage(emptyList(), 0))
    }

    @Test
    fun `clearing the selection does not touch the snapshot the batch reads`() {
        val selection = ArrayList(listOf("cf.playhi.a", "cf.playhi.b", "cf.playhi.c"))
        val snapshot = ArrayList(selection)
        selection.clear()

        assertTrue(selection.isEmpty())
        assertTrue(Main.hasPendingShortcuts(snapshot, 0))
        assertEquals("cf.playhi.a", Main.getNextPendingShortcutPackage(snapshot, 0))
        assertTrue(Main.hasPendingShortcuts(snapshot, 2))
        assertFalse(Main.hasPendingShortcuts(snapshot, 3))
    }
}
