package space.app.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedTextTest {
    @Test
    fun textOnly() = assertEquals("hello", sharedText(subject = null, text = "hello"))

    @Test
    fun subjectAndTextOnSeparateLines() =
        assertEquals("Article title\nhttps://example.com", sharedText("Article title", "https://example.com"))

    @Test
    fun subjectAlreadyInTextIsNotRepeated() =
        assertEquals("Title https://x.y", sharedText("Title", "Title https://x.y"))

    @Test
    fun blanksAreDropped() = assertEquals("", sharedText("  ", null))
}
