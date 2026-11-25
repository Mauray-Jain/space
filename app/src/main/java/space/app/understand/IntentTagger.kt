package space.app.understand

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer
import java.text.Normalizer

/** Words as the model saw them in training (training/generate.py: WORD). */
private val WORD = Regex("""[\p{L}\p{N}]+|[^\s\p{L}\p{N}]""")

private val INTENTS = listOf("note", "task", "reminder", "event", "list", "question_list", "question_search")
private val TAGS = listOf("O", "B-TITLE", "I-TITLE", "B-ITEM", "I-ITEM", "B-SEARCH", "I-SEARCH")
private const val MAX_LEN = 64

/** The model's reading: an intent, and a tag per word with where that word is in the text. */
class Reading(val intent: String, private val text: String, private val words: List<IntRange>, private val tags: List<String>) {
    /**
     * Everything from the first word with [tag] to the last, as written: a title or search phrase is one
     * stretch, so a word the model skipped inside it ("pick [up] dry cleaning") stays.
     */
    fun stretch(tag: String): String {
        val tagged = tags.indices.filter { tags[it].substringAfter('-') == tag && tags[it] != "O" }
        if (tagged.isEmpty()) return ""
        return text.substring(words[tagged.first()].first, words[tagged.last()].last + 1)
    }

    /** The text of each tagged span (e.g. each list item), in the original case. */
    fun spans(tag: String): List<String> {
        val out = mutableListOf<IntRange>()
        tags.forEachIndexed { i, t ->
            when {
                t == "B-$tag" || (t == "I-$tag" && (i == 0 || tags[i - 1].substringAfter('-') != tag)) -> out += words[i]
                t == "I-$tag" -> out[out.lastIndex] = out.last().first..words[i].last
            }
        }
        return out.map { text.substring(it) }
    }
}

/**
 * MiniLM-L6 fine-tuned on generated sentences (training/), int8, ~23 MB on ONNX Runtime: picks the
 * intent and tags each word as title, list item or search words. Uncased WordPiece, like BERT.
 */
class IntentTagger(model: ByteArray, vocab: List<String>) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(model)
    private val ids = vocab.withIndex().associate { (i, t) -> t to i }
    private val cls = ids.getValue("[CLS]")
    private val sep = ids.getValue("[SEP]")
    private val unk = ids.getValue("[UNK]")

    fun read(text: String): Reading {
        val words = WORD.findAll(text).map { it.range }.toList()
        val tokens = mutableListOf(cls)
        val first = IntArray(words.size) { -1 } // each word's first token
        for ((i, range) in words.withIndex()) {
            val pieces = wordPieces(text.substring(range))
            if (tokens.size + pieces.size > MAX_LEN - 1) break
            first[i] = tokens.size
            tokens += pieces
        }
        tokens += sep
        val n = tokens.size.toLong()
        val input = OnnxTensor.createTensor(env, LongBuffer.wrap(tokens.map { it.toLong() }.toLongArray()), longArrayOf(1, n))
        val mask = OnnxTensor.createTensor(env, LongBuffer.wrap(LongArray(tokens.size) { 1 }), longArrayOf(1, n))
        return input.use { mask.use {
            session.run(mapOf("input_ids" to input, "attention_mask" to mask)).use { out ->
                val intent = (out.get("intent_logits").get() as OnnxTensor).floatBuffer.let { b -> (0 until INTENTS.size).maxBy { b.get(it) } }
                val t = (out.get("tag_logits").get() as OnnxTensor).floatBuffer
                val tags = words.indices.map { w ->
                    val at = first[w]
                    if (at < 0) "O" else TAGS[(TAGS.indices).maxBy { t.get(at * TAGS.size + it) }]
                }
                Reading(INTENTS[intent], text, words, tags)
            }
        } }
    }

    /** Lowercase, accents stripped, then greedy longest-match WordPiece (BERT uncased). */
    private fun wordPieces(word: String): List<Int> {
        val w = Normalizer.normalize(word.lowercase(), Normalizer.Form.NFD).replace(Regex("""\p{Mn}"""), "")
        if (w.isEmpty()) return emptyList()
        if (w.length > 100) return listOf(unk)
        val out = mutableListOf<Int>()
        var start = 0
        while (start < w.length) {
            var end = w.length
            var piece: Int? = null
            while (start < end) {
                piece = ids[(if (start > 0) "##" else "") + w.substring(start, end)]
                if (piece != null) break
                end--
            }
            if (piece == null) return listOf(unk)
            out += piece
            start = end
        }
        return out
    }

    override fun close() = session.close()
}
