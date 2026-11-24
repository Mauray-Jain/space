package space.app.data

/** The 3-letter pieces the trigram index is built from ("dentist" → den, ent, nti, tis, ist). */
fun trigrams(word: String): List<String> = word.lowercase().windowed(3).distinct()

/**
 * Typo fallback (PLAN.md §2): keep candidates sharing enough 3-letter pieces with the
 * query, most shared first. At least 2 pieces and a third of them, so one common
 * piece like "ent" doesn't match everything.
 */
fun rankByTrigrams(query: String, candidates: List<Item>): List<Item> {
    val pieces = query.split(Regex("""\s+""")).flatMap { trigrams(it) }.distinct()
    val needed = maxOf(2, (pieces.size + 2) / 3)
    return candidates
        .map { item -> item to pieces.count { it in item.text.lowercase() } }
        .filter { (_, shared) -> shared >= needed }
        .sortedByDescending { (_, shared) -> shared }
        .map { (item, _) -> item }
}
