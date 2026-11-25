package space.app.sync

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.json.JSONArray
import org.json.JSONObject
import space.app.data.Item
import space.app.data.ItemRepository
import space.app.data.ItemType

/**
 * Export/import of everything as one .zip you save where you like (PLAN.md §4): notes as Markdown
 * to read anywhere, every item as JSON to import back, and each item's files. A password
 * encrypts it with AES-256 (opens in 7-Zip too). The file is yours: deleting an item later
 * can't reach it (EDGE_CASES.md §9).
 */
object Backup {
    private const val ITEMS = "items.json"

    suspend fun export(repo: ItemRepository, out: OutputStream, password: String?) = withContext(Dispatchers.IO) {
        val items = repo.all()
        ZipOutputStream(out, password?.takeIf { it.isNotEmpty() }?.toCharArray()).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipParameters().apply {
                    fileNameInZip = name
                    if (!password.isNullOrEmpty()) {
                        isEncryptFiles = true; encryptionMethod = EncryptionMethod.AES; aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                    }
                })
                zip.write(bytes)
                zip.closeEntry()
            }
            put(ITEMS, JSONArray(items.map(::toJson)).toString(1).toByteArray())
            items.forEach { item ->
                put("markdown/${fileName(item)}", markdown(item).toByteArray())
                val dir = repo.filesDir(item.id)
                dir.walkTopDown().filter { it.isFile }.forEach { put("files/${item.id}/${it.relativeTo(dir).path}", it.readBytes()) }
            }
        }
    }

    /** Returns how many items were added. Items already here (same text and creation time) are skipped. A wrong password adds nothing. */
    suspend fun import(repo: ItemRepository, input: InputStream, password: String?): Int = withContext(Dispatchers.IO) {
        // Read the whole file first, so a wrong password or a broken file changes nothing.
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input, password?.takeIf { it.isNotEmpty() }?.toCharArray()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.fileName] = zip.readBytes()
            }
        }
        val json = entries[ITEMS] ?: error("Not a Space export")
        val existing = repo.all().map { it.createdAt to it.text }.toSet()
        val items = JSONArray(json.decodeToString()).let { a -> (0 until a.length()).map { fromJson(a.getJSONObject(it)) } }
        var added = 0
        for (item in items) {
            if (item.createdAt to item.text in existing) continue
            val id = repo.insertCopy(item)
            val prefix = "files/${item.id}/"
            entries.filterKeys { it.startsWith(prefix) }.forEach { (name, bytes) ->
                val target = File(repo.filesDir(id), name.removePrefix(prefix))
                // Never write outside the item's folder, whatever the file says.
                if (target.canonicalPath.startsWith(repo.filesDir(id).canonicalPath + File.separator)) {
                    target.parentFile?.mkdirs(); target.writeBytes(bytes)
                }
            }
            added++
        }
        added
    }

    private fun toJson(i: Item) = JSONObject().apply {
        put("id", i.id); put("type", i.type.name); put("text", i.text); put("createdAt", i.createdAt); put("updatedAt", i.updatedAt)
        put("pinned", i.pinned); put("archived", i.archived); put("done", i.done); put("notified", i.notified); put("event", i.event)
        i.remindAt?.let { put("remindAt", it.toString()) }
        i.repeat?.let { put("repeat", it) }
        i.snoozedUntil?.let { put("snoozedUntil", it.toString()) }
        i.ocrText?.let { put("ocrText", it) }
    }

    private fun fromJson(o: JSONObject) = Item(
        id = o.getLong("id"),
        type = ItemType.valueOf(o.getString("type")),
        text = o.getString("text"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.optLong("updatedAt", o.getLong("createdAt")),
        pinned = o.optBoolean("pinned"),
        archived = o.optBoolean("archived"),
        done = o.optBoolean("done"),
        notified = o.optBoolean("notified"),
        event = o.optBoolean("event"),
        remindAt = o.optString("remindAt").takeIf { it.isNotEmpty() }?.let(LocalDateTime::parse),
        repeat = o.optString("repeat").takeIf { it.isNotEmpty() },
        snoozedUntil = o.optString("snoozedUntil").takeIf { it.isNotEmpty() }?.let(LocalDateTime::parse),
        ocrText = o.optString("ocrText").takeIf { it.isNotEmpty() },
    )
}
