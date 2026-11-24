package space.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingTest {
    @Test
    fun knownWordsGetTheirSection() {
        assertEquals(Section.PRODUCE, sectionOf("Bananas"))
        assertEquals(Section.DAIRY, sectionOf("milk"))
        assertEquals(Section.DAIRY, sectionOf("greek yogurt"))
        assertEquals(Section.BAKERY, sectionOf("bread"))
        assertEquals(Section.HOUSEHOLD, sectionOf("dish soap"))
    }

    @Test
    fun unknownWordsGoToOther() = assertEquals(Section.OTHER, sectionOf("birthday candles"))

    @Test
    fun splitsOnCommasAndNewlines() =
        assertEquals(listOf("milk", "eggs", "bread"), splitShoppingItems("milk, eggs\n bread ,"))
}
