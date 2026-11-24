package space.app.capture

/** Joins what another app shared (e.g. a page title and its link) into the text of one capture. */
fun sharedText(subject: String?, text: String?): String {
    val s = subject?.trim().orEmpty()
    val t = text?.trim().orEmpty()
    return when {
        s.isEmpty() -> t
        t.isEmpty() || s in t -> t.ifEmpty { s }
        else -> "$s\n$t"
    }
}
