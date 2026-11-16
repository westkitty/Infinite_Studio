package com.example.document

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DocumentCommand(
    val description: String,
    val changedObjectIds: List<String> = emptyList(),
    val processingMode: String = "LOCAL_EDIT",
    val providerId: String? = null,
    val transform: (CanonicalDocument) -> CanonicalDocument
)

data class HistoryMetrics(
    val stackSize: Int,
    val currentIndex: Int,
    val estimatedBytes: Long,
    val canUndo: Boolean,
    val canRedo: Boolean
)

/**
 * Reversible transaction manager for CanonicalDocument.
 * Because layer tiles are immutable PNG ByteArrays, structural sharing ensures
 * untouched tiles consume zero additional memory across history revisions and branches.
 */
class TransactionEngine(
    initialDocument: CanonicalDocument,
    private val maxSteps: Int = 24,
    private val maxHistoryBytes: Long = 48L * 1024L * 1024L
) {
    private val undoStack = ArrayList<CanonicalDocument>().apply { add(initialDocument) }
    private var cursor = 0

    private val _document = MutableStateFlow(initialDocument)
    val document: StateFlow<CanonicalDocument> = _document.asStateFlow()

    fun current(): CanonicalDocument = _document.value

    fun commit(command: DocumentCommand): CanonicalDocument {
        val base = undoStack[cursor]
        val transformed = command.transform(base)
        val nextRev = base.revision + 1L
        val record = RevisionProvenance(
            revision = nextRev,
            summary = command.description,
            sourceHash = transformed.documentContentHash().take(16),
            processingMode = command.processingMode,
            providerId = command.providerId
        )
        val committed = transformed.copy(
            revision = nextRev,
            revisions = (transformed.revisions + record).takeLast(64)
        )

        if (cursor < undoStack.size - 1) {
            undoStack.subList(cursor + 1, undoStack.size).clear()
        }
        undoStack.add(committed)
        enforceMemoryBounds()
        cursor = undoStack.size - 1
        _document.value = committed
        return committed
    }

    fun resetRoot(newDoc: CanonicalDocument) {
        undoStack.clear()
        undoStack.add(newDoc)
        cursor = 0
        _document.value = newDoc
    }

    fun undo(): CanonicalDocument? {
        if (cursor <= 0) return null
        cursor--
        val doc = undoStack[cursor]
        _document.value = doc
        return doc
    }

    fun redo(): CanonicalDocument? {
        if (cursor >= undoStack.size - 1) return null
        cursor++
        val doc = undoStack[cursor]
        _document.value = doc
        return doc
    }

    fun canUndo(): Boolean = cursor > 0

    fun canRedo(): Boolean = cursor < undoStack.size - 1

    fun metrics(): HistoryMetrics {
        val estBytes = estimateHistoryBytes()
        return HistoryMetrics(
            stackSize = undoStack.size,
            currentIndex = cursor,
            estimatedBytes = estBytes,
            canUndo = canUndo(),
            canRedo = canRedo()
        )
    }

    private fun enforceMemoryBounds() {
        while (undoStack.size > maxSteps) {
            undoStack.removeAt(0)
        }
        while (estimateHistoryBytes() > maxHistoryBytes && undoStack.size > 2) {
            undoStack.removeAt(0)
        }
    }

    private fun estimateHistoryBytes(): Long {
        // Count unique ByteArray identities so shared tiles are accurately accounted once,
        // plus per-snapshot overhead.
        val seen = HashSet<Int>()
        var total = 0L
        undoStack.forEach { doc ->
            total += 2048L
            doc.layers.forEach { layer ->
                layer.tiles.values.forEach { bytes ->
                    if (seen.add(System.identityHashCode(bytes))) {
                        total += bytes.size
                    }
                }
            }
        }
        return total
    }
}
