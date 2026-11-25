package space.app.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import space.app.Settings
import space.app.data.Item

/**
 * One-way Markdown copy of every item into a folder you picked (Obsidian, Syncthing). Edits made
 * in the folder aren't read back. Copies already synced to other devices survive deletion (known issue).
 */
@Singleton
class MarkdownSync @Inject constructor(@ApplicationContext private val context: Context, private val settings: Settings) {
    private val resolver get() = context.contentResolver

    fun write(item: Item) = inFolder { tree, parent ->
        remove(tree, parent, item.id)
        val doc = DocumentsContract.createDocument(resolver, parent, "text/markdown", fileName(item)) ?: return@inFolder
        resolver.openOutputStream(doc, "wt")?.use { it.write(markdown(item).toByteArray()) }
    }

    /** Part of the single delete path (CLAUDE.md). */
    fun delete(itemId: Long) = inFolder { tree, parent -> remove(tree, parent, itemId) }

    /** Writes everything, e.g. right after a folder is picked. */
    fun writeAll(items: List<Item>) = items.forEach { write(it) }

    private fun remove(tree: Uri, parent: Uri, itemId: Long) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        val suffix = "-$itemId.md"
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1).endsWith(suffix)) DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)))
            }
        }
    }

    private fun inFolder(block: (tree: Uri, parent: Uri) -> Unit) {
        val tree = settings.markdownFolder?.let(Uri::parse) ?: return
        // A folder that was removed or whose permission was taken back is skipped, never fatal.
        runCatching { block(tree, DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))) }
    }
}
