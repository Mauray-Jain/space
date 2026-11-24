package space.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChecklistTest {
    @Test
    fun splitsBodyAndSubtasks() {
        val parsed = Checklist.parse("Pack for trip\n- [ ] passport\n- [x] charger")
        assertEquals("Pack for trip", parsed.body)
        assertEquals(listOf(Subtask("passport", false), Subtask("charger", true)), parsed.subtasks)
    }

    @Test
    fun textWithoutSubtasksIsAllBody() {
        val parsed = Checklist.parse("call the bank\nabout the card")
        assertEquals("call the bank\nabout the card", parsed.body)
        assertEquals(emptyList<Subtask>(), parsed.subtasks)
    }

    @Test
    fun upperCaseXCountsAsDone() =
        assertEquals(listOf(Subtask("milk", true)), Checklist.parse("- [X] milk").subtasks)

    @Test
    fun roundTrips() {
        val text = "Pack for trip\n- [ ] passport\n- [x] charger"
        assertEquals(text, Checklist.parse(text).toText())
    }

    @Test
    fun blankSubtasksAreDropped() =
        assertEquals("Trip\n- [ ] a", Checklist("Trip", listOf(Subtask("a", false), Subtask("  ", false))).toText())
}
