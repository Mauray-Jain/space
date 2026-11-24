package space.app.data

data class Subtask(val text: String, val done: Boolean)

/** A task's subtasks are Markdown checklist lines in its text ("- [ ] passport"). */
data class Checklist(val body: String, val subtasks: List<Subtask>) {
    fun toText(): String =
        (listOf(body.trim()) + subtasks.filter { it.text.isNotBlank() }.map { "- [${if (it.done) "x" else " "}] ${it.text.trim()}" })
            .filter { it.isNotEmpty() }
            .joinToString("\n")

    companion object {
        private val line = Regex("""^- \[([ xX])] (.*)$""")

        fun parse(text: String): Checklist {
            val (subtaskLines, bodyLines) = text.lines().partition { line.matches(it.trim()) }
            return Checklist(
                body = bodyLines.joinToString("\n").trim(),
                subtasks = subtaskLines.map {
                    val m = line.matchEntire(it.trim())!!
                    Subtask(m.groupValues[2], m.groupValues[1] != " ")
                },
            )
        }
    }
}
