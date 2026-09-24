package cf.playhi.freezeyou.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The request codes of the timed tasks that currently have an alarm registered, kept as one string
 * of `id,` entries outside the database — because an alarm outlives the row it was made from, and
 * cancelling by rows cannot reach one whose row is gone. An entry wrongly dropped here leaves the
 * system waking the application for a task that no longer exists; an entry wrongly kept cancels an
 * alarm that should have stayed.
 *
 * The ids are numbers, which is the whole reason this is worth a test: removing "1" from "11,1," by
 * replacing text would eat half of the eleven.
 */
class TaskIdListTest {

    @Test
    fun `an id is appended once and not twice`() {
        assertEquals("7,", TasksUtils.addIdToList("", 7))
        assertEquals("7,", TasksUtils.addIdToList("7,", 7))
        assertEquals("7,8,", TasksUtils.addIdToList("7,", 8))
    }

    @Test
    fun `an absent list is treated as empty`() {
        assertEquals("3,", TasksUtils.addIdToList(null, 3))
        assertEquals("", TasksUtils.removeIdFromList(null, 3))
        assertEquals("", TasksUtils.removeIdFromList("", 3))
    }

    @Test
    fun `a shorter id does not take a longer one with it`() {
        assertEquals("11,", TasksUtils.removeIdFromList("11,1,", 1))
        assertEquals("1,", TasksUtils.removeIdFromList("11,1,", 11))
        assertEquals("1,12,21,", TasksUtils.removeIdFromList("1,12,2,21,", 2))
    }

    @Test
    fun `removing an id that is not there changes nothing`() {
        assertEquals("4,5,", TasksUtils.removeIdFromList("4,5,", 6))
    }

    @Test
    fun `removing the last id empties the list`() {
        assertEquals("", TasksUtils.removeIdFromList("9,", 9))
    }

    @Test
    fun `a list that lost its shape is still readable`() {
        // Stray separators have shown up in these stored lists before; they must not become ids.
        assertEquals("4,", TasksUtils.removeIdFromList(",,4,,5,,", 5))
    }
}
