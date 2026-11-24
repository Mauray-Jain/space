package space.app.data

// A tag starts after a space or the start of the text (so "page#section" in a link isn't one)
// and has at least one letter.
private val tagPattern = Regex("""(?<=^|\s)#([\p{L}\p{N}_-]*\p{L}[\p{L}\p{N}_-]*)""")

/** The #tags in an item's text, lowercased, first-seen order. */
fun tags(text: String): List<String> = tagPattern.findAll(text).map { it.groupValues[1].lowercase() }.distinct().toList()

fun withTag(text: String, tag: String): String =
    if (tag.lowercase() in tags(text)) text else "${text.trimEnd()} #${tag.lowercase()}"

fun withoutTag(text: String, tag: String): String =
    tagPattern.replace(text) { if (it.groupValues[1].equals(tag, ignoreCase = true)) "" else it.value }
        .replace(Regex("""[ \t]{2,}"""), " ")
        .trim()
