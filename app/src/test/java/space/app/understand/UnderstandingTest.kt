package space.app.understand

import java.io.File
import java.time.LocalDateTime
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.fail
import org.junit.Test

/**
 * The milestone 4 test set (testdata/README.md). Rows marked known_weak are reported, not required.
 * Never tune rules just to pass a known_weak row, and never train on this file (CLAUDE.md).
 */
class UnderstandingTest {
    private val understander = File(System.getProperty("models.dir")!!).let { dir ->
        Understander(File(dir, "intent-minilm.onnx").readBytes(), File(dir, "intent-vocab.txt").readLines())
    }

    private val rows = File("../testdata/understanding_test.jsonl").readLines().filter { it.isNotBlank() }
        .map { Json.parseToJsonElement(it).jsonObject }

    @Test
    fun testSet() {
        val failures = mutableListOf<String>()
        val weakPassed = mutableListOf<String>()
        val weakFailed = mutableListOf<String>()
        for (row in rows) {
            val id = row.str("id")!!
            val weak = row["known_weak"]?.jsonPrimitive?.booleanOrNull == true
            val got = understander.understand(
                input = row.str("input")!!,
                now = LocalDateTime.parse(row.str("now")),
                locale = Locale.forLanguageTag(row.str("locale") ?: "en-IN"),
                ocr = row["ocr"]?.jsonArray?.map { OcrLine(it.jsonObject.str("text")!!, it.jsonObject["h"]!!.jsonPrimitive.intOrNull!!) },
            )
            val expect = row["expect"]!!
            val expected = if (expect is JsonArray) expect.map { it.jsonObject } else listOf(expect.jsonObject)
            val problems = if (expected.size != got.size) {
                listOf("expected ${expected.size} results, got ${got.size}: $got")
            } else {
                expected.zip(got).flatMap { (e, g) -> compare(e, g) }
            }
            val line = "$id ${row.str("input")!!.replace("\n", " | ")} -> ${problems.joinToString("; ")}"
            when {
                problems.isEmpty() && weak -> weakPassed += id
                problems.isEmpty() -> {}
                weak -> weakFailed += line
                else -> failures += line
            }
        }
        println("known_weak: ${weakPassed.size} passed, ${weakFailed.size} failed")
        weakFailed.forEach { println("  weak  $it") }
        failures.forEach { println("  FAIL  $it") }
        println("required: ${rows.size - weakPassed.size - weakFailed.size - failures.size} passed, ${failures.size} failed")
        if (failures.isNotEmpty()) fail("${failures.size} required rows failed:\n" + failures.joinToString("\n"))
    }

    private fun compare(e: JsonObject, g: Understood): List<String> = e.entries.mapNotNull { (key, value) ->
        val actual: Any? = when (key) {
            "type" -> g.type.name.lowercase()
            "title" -> g.title
            "when" -> g.at?.toString()?.take(16)
            "repeat" -> g.repeat
            "items" -> g.items
            "alts" -> g.alts.map { it.toString().take(16) }
            "target" -> g.target
            "search" -> g.search
            "phones" -> g.phones
            "emails" -> g.emails
            "links" -> g.links
            "name" -> g.name
            "company" -> g.company
            "job" -> g.job
            else -> return@mapNotNull "unknown field $key"
        }
        val ok = when (key) {
            "title", "search" -> value.str() != null && actual is String && norm(actual) == norm(value.str()!!)
            else -> plain(value) == actual
        }
        if (ok) null else "$key: expected ${plain(value)}, got $actual"
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("""\s+"""), " ").trim().trimEnd('.', '!', '?', ',')

    private fun plain(v: JsonElement): Any? = when (v) {
        is JsonNull -> null
        is JsonPrimitive -> v.content
        is JsonArray -> v.map { plain(it) }
        is JsonObject -> v.toString()
    }

    private fun JsonObject.str(key: String) = this[key]?.str()
    private fun JsonElement.str() = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
}
