package space.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TagsTest {
    @Test
    fun findsHashtagsLowercasedInOrderWithoutRepeats() =
        assertEquals(listOf("work", "q4"), tags("Plan #Work stuff #q4 and #work again"))

    @Test
    fun ignoresBareHashAndNumbersOnly() = assertEquals(emptyList<String>(), tags("issue # 4 and #42"))

    @Test
    fun ignoresAnchorsInsideLinks() = assertEquals(emptyList<String>(), tags("see https://x.dev/page#section"))

    @Test
    fun addAndRemoveTag() {
        assertEquals("buy stamps #errands", withTag("buy stamps", "errands"))
        assertEquals("buy stamps #errands", withTag("buy stamps #errands", "Errands"))
        assertEquals("buy stamps", withoutTag("buy stamps #errands", "errands"))
        assertEquals("buy stamps today", withoutTag("buy #Errands stamps today", "errands"))
    }
}
