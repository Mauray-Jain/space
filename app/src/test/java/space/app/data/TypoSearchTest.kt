package space.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TypoSearchTest {
    private fun item(id: Long, text: String) = Item(id = id, type = ItemType.NOTE, text = text, createdAt = 0)

    @Test
    fun trigramsOfEachWord() = assertEquals(listOf("den", "ent", "nts", "tsi", "sit"), trigrams("dentsit"))

    // EDGE_CASES.md §10: "dentsit" finds "dentist", above things sharing a single piece.
    @Test
    fun ranksByHowManyPiecesMatch() {
        val ranked = rankByTrigrams("dentsit", listOf(item(1, "parent meeting"), item(2, "dentist thursday")))
        assertEquals(listOf(2L), ranked.map { it.id })
    }
}
