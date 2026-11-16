package com.example.features

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.example.document.CanonicalDocument
import com.example.document.LayerKeyframe
import com.example.document.RecipeGraph
import com.example.document.RecipeNode
import com.example.document.RecipeNodeKind
import com.example.document.RecipeStageResult
import com.example.document.TimelineFrame
import com.example.render.SparseTileRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

@Serializable
data class SpriteSheetFrameMeta(
    val frameId: String,
    val label: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val durationMs: Int
)

@Serializable
data class SpriteSheetExportResult(
    val sheetWidth: Int,
    val sheetHeight: Int,
    val fps: Int,
    val frames: List<SpriteSheetFrameMeta>,
    val videoExportNote: String
)

data class RecipeRunReport(
    val recipeId: String,
    val orderedNodeIds: List<String>,
    val computedNodeIds: List<String>,
    val reusedNodeIds: List<String>,
    val updatedGraph: RecipeGraph,
    val canceled: Boolean,
    val producedLayer: com.example.document.DocumentLayer? = null
)

data class LivingPreviewSnapshot(
    val revision: Long,
    val bitmap: Bitmap?,
    val isPausedForBattery: Boolean,
    val statusText: String
)

/**
 * Implements:
 * - F05 Time Canvas (keyframes, onion skin, sprite sheet PNG + JSON metadata export)
 * - F04 Creative Recipes (acyclic DAG topological evaluation + selective descendant-only invalidation + caching)
 * - F03 Living Preview (debounced background preview with stale revision cancellation & low-power pause)
 */
object TimeAndRecipeEngine {

    // --- F05 Time Canvas ---
    fun addTimelineFrame(doc: CanonicalDocument): CanonicalDocument {
        val idx = doc.timeline.frames.size + 1
        val keyframes = doc.layers.map {
            LayerKeyframe(layerId = it.id, visible = it.visible, offsetX = it.transform.offsetX, offsetY = it.transform.offsetY, opacity = it.opacity)
        }
        val frame = TimelineFrame(
            id = "frame_${UUID.randomUUID().toString().take(6)}",
            label = "Frame $idx",
            durationMs = (1000f / max(1, doc.timeline.fps)).roundToInt(),
            layerKeyframes = keyframes
        )
        return doc.copy(
            timeline = doc.timeline.copy(
                frames = doc.timeline.frames + frame,
                currentFrameIndex = doc.timeline.frames.size
            )
        )
    }

    fun selectTimelineFrame(doc: CanonicalDocument, frameIndex: Int): CanonicalDocument {
        val clamped = frameIndex.coerceIn(0, max(0, doc.timeline.frames.lastIndex))
        val targetFrame = doc.timeline.frames.getOrNull(clamped)
        val updatedLayers = if (targetFrame != null && targetFrame.layerKeyframes.isNotEmpty()) {
            val kfMap = targetFrame.layerKeyframes.associateBy { it.layerId }
            doc.layers.map { l ->
                val kf = kfMap[l.id]
                if (kf != null) {
                    l.copy(
                        visible = kf.visible,
                        opacity = kf.opacity,
                        transform = l.transform.copy(offsetX = kf.offsetX, offsetY = kf.offsetY)
                    )
                } else l
            }
        } else doc.layers

        return doc.copy(
            layers = updatedLayers,
            timeline = doc.timeline.copy(currentFrameIndex = clamped)
        )
    }

    fun captureCurrentLayersToFrame(doc: CanonicalDocument): CanonicalDocument {
        val idx = doc.timeline.currentFrameIndex.coerceIn(0, max(0, doc.timeline.frames.lastIndex))
        val frames = doc.timeline.frames.toMutableList()
        if (frames.isEmpty()) return doc
        val kfs = doc.layers.map {
            LayerKeyframe(
                layerId = it.id,
                visible = it.visible,
                offsetX = it.transform.offsetX,
                offsetY = it.transform.offsetY,
                opacity = it.opacity
            )
        }
        frames[idx] = frames[idx].copy(layerKeyframes = kfs)
        return doc.copy(timeline = doc.timeline.copy(frames = frames))
    }

    fun toggleOnionSkin(doc: CanonicalDocument): CanonicalDocument =
        doc.copy(timeline = doc.timeline.copy(onionSkinEnabled = !doc.timeline.onionSkinEnabled))

    fun setTimelineFps(doc: CanonicalDocument, fps: Int): CanonicalDocument =
        doc.copy(timeline = doc.timeline.copy(fps = fps.coerceIn(1, 24)))

    fun duplicateTimelineFrame(doc: CanonicalDocument, frameIndex: Int = doc.timeline.currentFrameIndex): CanonicalDocument {
        val frames = doc.timeline.frames
        if (frames.isEmpty()) return addTimelineFrame(doc)
        val idx = frameIndex.coerceIn(0, frames.lastIndex)
        val src = frames[idx]
        val clone = src.copy(
            id = "frame_${UUID.randomUUID().toString().take(6)}",
            label = "${src.label} Copy",
            layerKeyframes = src.layerKeyframes.map { it.copy() }
        )
        val updated = frames.toMutableList().apply { add(idx + 1, clone) }
        return doc.copy(
            timeline = doc.timeline.copy(
                frames = updated,
                currentFrameIndex = idx + 1
            )
        )
    }

    fun deleteTimelineFrame(doc: CanonicalDocument, frameIndex: Int): CanonicalDocument {
        if (doc.timeline.frames.size <= 1) return doc
        val idx = frameIndex.coerceIn(0, doc.timeline.frames.lastIndex)
        val updated = doc.timeline.frames.toMutableList().apply { removeAt(idx) }
        val nextCur = doc.timeline.currentFrameIndex.coerceIn(0, updated.lastIndex)
        return doc.copy(
            timeline = doc.timeline.copy(
                frames = updated,
                currentFrameIndex = nextCur,
                loopStartIndex = doc.timeline.loopStartIndex.coerceIn(0, updated.lastIndex),
                loopEndIndex = if (doc.timeline.loopEndIndex < 0) -1 else doc.timeline.loopEndIndex.coerceIn(0, updated.lastIndex)
            )
        )
    }

    fun setTimelineLoopRange(doc: CanonicalDocument, startIndex: Int, endIndex: Int): CanonicalDocument {
        val last = max(0, doc.timeline.frames.lastIndex)
        val s = startIndex.coerceIn(0, last)
        val e = endIndex.coerceIn(s, last)
        return doc.copy(timeline = doc.timeline.copy(loopStartIndex = s, loopEndIndex = e))
    }

    fun effectiveLoopRange(doc: CanonicalDocument): IntRange {
        val last = max(0, doc.timeline.frames.lastIndex)
        val s = doc.timeline.loopStartIndex.coerceIn(0, last)
        val rawEnd = if (doc.timeline.loopEndIndex < 0) last else doc.timeline.loopEndIndex
        val e = rawEnd.coerceIn(s, last)
        return s..e
    }

    fun updateOnionSkinConfig(
        doc: CanonicalDocument,
        enabled: Boolean = doc.timeline.onionSkinEnabled,
        beforeOpacity: Float = doc.timeline.onionSkinBeforeOpacity,
        afterEnabled: Boolean = doc.timeline.onionSkinAfterEnabled,
        afterOpacity: Float = doc.timeline.onionSkinAfterOpacity
    ): CanonicalDocument {
        return doc.copy(
            timeline = doc.timeline.copy(
                onionSkinEnabled = enabled,
                onionSkinBeforeOpacity = beforeOpacity.coerceIn(0.05f, 0.7f),
                onionSkinAfterEnabled = afterEnabled,
                onionSkinAfterOpacity = afterOpacity.coerceIn(0.05f, 0.7f)
            )
        )
    }

    fun updateLayerKeyframe(
        doc: CanonicalDocument,
        frameIndex: Int,
        layerId: String,
        offsetX: Float,
        offsetY: Float,
        opacity: Float,
        visible: Boolean
    ): CanonicalDocument {
        val frames = doc.timeline.frames.toMutableList()
        if (frameIndex !in frames.indices) return doc
        val target = frames[frameIndex]
        val existing = target.layerKeyframes.any { it.layerId == layerId }
        val updatedKf = LayerKeyframe(layerId, visible, offsetX, offsetY, opacity.coerceIn(0f, 1f))
        val newList = if (existing) {
            target.layerKeyframes.map { if (it.layerId == layerId) updatedKf else it }
        } else {
            target.layerKeyframes + updatedKf
        }
        frames[frameIndex] = target.copy(layerKeyframes = newList)
        return selectTimelineFrame(doc.copy(timeline = doc.timeline.copy(frames = frames)), frameIndex)
    }

    fun exportSpriteSheet(
        doc: CanonicalDocument,
        renderer: SparseTileRenderer,
        frameWidth: Int = 256,
        frameHeight: Int = 256
    ): Pair<Bitmap, SpriteSheetExportResult> {
        val frames = doc.timeline.frames.ifEmpty { listOf(TimelineFrame("f1", "Frame 1")) }
        val sheetW = frameWidth * frames.size
        val sheetBmp = Bitmap.createBitmap(sheetW, frameHeight, Bitmap.Config.ARGB_8888)
        val sheetCanvas = Canvas(sheetBmp)
        val artRegion = renderer.computeArtworkBoundingBox(doc, padding = 32)
            ?: RectF(-360f, -240f, 360f, 240f)

        val metas = mutableListOf<SpriteSheetFrameMeta>()
        frames.forEachIndexed { idx, frame ->
            val frameDoc = selectTimelineFrame(doc, idx).let { d ->
                // Render clean frame without ghosting onion skin in exported sprite
                d.copy(timeline = d.timeline.copy(onionSkinEnabled = false, onionSkinAfterEnabled = false))
            }
            val raw = renderer.renderFlattenedRegion(frameDoc, artRegion, includePaper = false, includeVectors = true)
            val destRect = RectF(
                (idx * frameWidth).toFloat(), 0f,
                ((idx + 1) * frameWidth).toFloat(), frameHeight.toFloat()
            )
            sheetCanvas.drawBitmap(raw, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            raw.recycle()
            metas.add(
                SpriteSheetFrameMeta(
                    frameId = frame.id,
                    label = frame.label,
                    x = idx * frameWidth,
                    y = 0,
                    width = frameWidth,
                    height = frameHeight,
                    durationMs = frame.durationMs
                )
            )
        }

        val result = SpriteSheetExportResult(
            sheetWidth = sheetW,
            sheetHeight = frameHeight,
            fps = doc.timeline.fps,
            frames = metas,
            videoExportNote = "PNG sprite sheet + per-frame PNGs + frame timing JSON exported. Direct MP4 video encoding is excluded in sandboxed headless builds (no hardware MediaCodec muxer guarantee)."
        )
        return sheetBmp to result
    }

    // --- F04 Creative Recipes (DAG with selective descendant-only invalidation) ---
    fun topologicalSort(graph: RecipeGraph): Result<List<RecipeNode>> = runCatching {
        val nodeMap = graph.nodes.associateBy { it.id }
        val inDegree = LinkedHashMap<String, Int>()
        val dependents = LinkedHashMap<String, MutableList<String>>()
        graph.nodes.forEach { n ->
            inDegree[n.id] = 0
            dependents[n.id] = mutableListOf()
        }
        graph.nodes.forEach { n ->
            n.inputNodeIds.forEach { upId ->
                require(nodeMap.containsKey(upId)) { "Missing upstream recipe node: $upId" }
                inDegree[n.id] = (inDegree[n.id] ?: 0) + 1
                dependents[upId]?.add(n.id)
            }
        }
        val queue = ArrayDeque<String>()
        inDegree.forEach { (id, deg) -> if (deg == 0) queue.addLast(id) }

        val ordered = mutableListOf<RecipeNode>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            ordered.add(nodeMap.getValue(id))
            dependents[id]?.forEach { downId ->
                val nd = (inDegree[downId] ?: 1) - 1
                inDegree[downId] = nd
                if (nd == 0) queue.addLast(downId)
            }
        }
        require(ordered.size == graph.nodes.size) { "Cycle detected in Recipe DAG!" }
        ordered
    }

    /**
     * Evaluates the recipe DAG in topological order.
     * Any node whose input document hash, parameters, and upstream cacheKeys are unchanged
     * is REUSED directly from cache; modifying a downstream stage only invalidates that stage
     * and its descendants—never recomputing unchanged upstream nodes!
     */
    // Session-local, size-bounded PNG-tile caches. Serialized result hashes are not treated as
    // executable artifacts after an app restart; missing payloads always recompute.
    private const val MAX_RECIPE_CACHE_BYTES = 24L * 1024L * 1024L
    private val stageArtifacts = LinkedHashMap<String, com.example.document.DocumentLayer>(24, 0.75f, true)
    private var stageCacheBytes = 0L

    private fun cachedStage(key: String): com.example.document.DocumentLayer? =
        synchronized(stageArtifacts) { stageArtifacts[key] }

    private fun rememberStage(key: String, layer: com.example.document.DocumentLayer) {
        val bytes = layer.tiles.values.sumOf { it.size.toLong() }
        if (bytes > MAX_RECIPE_CACHE_BYTES) return
        synchronized(stageArtifacts) {
            stageArtifacts.remove(key)?.let { old ->
                stageCacheBytes -= old.tiles.values.sumOf { it.size.toLong() }
            }
            stageArtifacts[key] = layer
            stageCacheBytes += bytes
            val it = stageArtifacts.entries.iterator()
            while (stageCacheBytes > MAX_RECIPE_CACHE_BYTES && it.hasNext()) {
                val eldest = it.next()
                stageCacheBytes -= eldest.value.tiles.values.sumOf { tile -> tile.size.toLong() }
                it.remove()
            }
        }
    }

    /**
     * F04: actually executes each local pixel operator on immutable sparse PNG tiles.
     * Only a completed result is returned for a later transactional accept-as-layer.
     * Previously persisted hash-only successes are recomputed because they have no pixels.
     */
    fun executeRecipeGraph(
        doc: CanonicalDocument,
        recipeId: String = doc.recipes.firstOrNull()?.id ?: "recipe_default",
        shouldCancel: () -> Boolean = { false }
    ): RecipeRunReport {
        val graph = doc.recipes.find { it.id == recipeId } ?: RecipeGraph()
        val ordered = topologicalSort(graph).getOrThrow()
        val source = doc.layers.find { it.id == doc.activeLayerId && !it.id.startsWith("recipe_output_") }
            ?: doc.layers.firstOrNull { it.tiles.isNotEmpty() && !it.id.startsWith("recipe_output_") }
        val baseHash = sha256Short(
            "${doc.documentId}:${source?.id}:${source?.pixelContentHash()}:${source?.transform}"
        )

        val newResults = LinkedHashMap<String, RecipeStageResult>(graph.results)
        val outputLayers = linkedMapOf<String, com.example.document.DocumentLayer>()
        val computed = mutableListOf<String>()
        val reused = mutableListOf<String>()
        var canceled = false

        for (node in ordered) {
            if (shouldCancel()) {
                canceled = true
                break
            }
            val paramError = validateNodeParameters(node)
            val upstreamError = node.inputNodeIds.firstOrNull { upId ->
                newResults[upId]?.status?.startsWith("ERROR") == true
            }
            if (paramError != null || upstreamError != null || source == null) {
                val err = paramError ?: when {
                    source == null -> "No editable source layer"
                    else -> "Blocked by upstream error in '$upstreamError'"
                }
                newResults[node.id] = RecipeStageResult(
                    node.id, "invalid", "ERROR: $err", "ERR", Instant.now().toString()
                )
                computed.add(node.id)
                continue
            }

            val upstreamKeys = node.inputNodeIds.map { newResults[it]?.cacheKey ?: "" }
            val cacheKey = computeNodeCacheKey(baseHash, node, upstreamKeys)
            val prev = graph.results[node.id]
            val cached = cachedStage(cacheKey)
            if (prev != null && prev.cacheKey == cacheKey && prev.status == "SUCCEEDED" &&
                cached != null && cached.pixelContentHash() == prev.outputHash
            ) {
                outputLayers[node.id] = cached
                newResults[node.id] = prev
                reused.add(node.id)
                continue
            }

            try {
                val inputs = if (node.inputNodeIds.isEmpty()) listOf(source)
                else node.inputNodeIds.map { outputLayers[it] ?: error("Missing computed upstream image: $it") }
                val src = RecipePixelOperators.composite(inputs, doc, shouldCancel)
                val artifact = RecipePixelOperators.apply(src, node, doc, shouldCancel)
                if (shouldCancel()) throw RecipePixelKernels.Canceled()
                rememberStage(cacheKey, artifact)
                outputLayers[node.id] = artifact
                newResults[node.id] = RecipeStageResult(
                    node.id, cacheKey, "SUCCEEDED", artifact.pixelContentHash(), Instant.now().toString()
                )
                computed.add(node.id)
            } catch (_: RecipePixelKernels.Canceled) {
                canceled = true
                break
            } catch (error: Exception) {
                newResults[node.id] = RecipeStageResult(
                    node.id, cacheKey, "ERROR: ${error.message ?: error.javaClass.simpleName}",
                    "ERR", Instant.now().toString()
                )
                computed.add(node.id)
            }
        }

        val updatedGraph = graph.copy(results = newResults)
        val finalNode = ordered.lastOrNull()
        val finalArtifact = if (!canceled && finalNode != null &&
            newResults[finalNode.id]?.status == "SUCCEEDED"
        ) outputLayers[finalNode.id] else null
        return RecipeRunReport(
            recipeId = updatedGraph.id,
            orderedNodeIds = ordered.map { it.id },
            computedNodeIds = computed,
            reusedNodeIds = reused,
            updatedGraph = updatedGraph,
            canceled = canceled,
            producedLayer = finalArtifact?.copy(
                id = "recipe_output_${UUID.randomUUID().toString().take(12)}",
                name = "Recipe: ${graph.name}",
                visible = true
            )
        )
    }

    private fun validateNodeParameters(node: RecipeNode): String? {
        node.parameters["roughness"]?.let { raw ->
            val v = raw.toFloatOrNull() ?: return "roughness '$raw' is not numeric"
            if (v !in 0f..1f) return "roughness $v outside [0.0, 1.0]"
        }
        node.parameters["threshold"]?.let { raw ->
            val v = raw.toIntOrNull() ?: return "threshold '$raw' is not an integer"
            if (v !in 1..255) return "threshold $v outside [1, 255]"
        }
        node.parameters["weight"]?.let { raw ->
            val v = raw.toFloatOrNull() ?: return "weight '$raw' is not numeric"
            if (v <= 0f || v > 20f) return "weight $v outside (0, 20]"
        }
        return null
    }

    fun updateRecipeNodeParameter(
        doc: CanonicalDocument,
        recipeId: String,
        nodeId: String,
        paramKey: String,
        paramValue: String
    ): CanonicalDocument {
        val updatedRecipes = doc.recipes.map { graph ->
            if (graph.id != recipeId) graph
            else graph.copy(
                nodes = graph.nodes.map { n ->
                    if (n.id == nodeId) n.copy(parameters = n.parameters + (paramKey to paramValue)) else n
                }
            )
        }
        return doc.copy(recipes = updatedRecipes)
    }

    fun addRecipeNode(
        doc: CanonicalDocument,
        recipeId: String = doc.recipes.firstOrNull()?.id ?: "recipe_default",
        kind: RecipeNodeKind,
        title: String,
        inputNodeIds: List<String> = emptyList(),
        posX: Float? = null,
        posY: Float? = null
    ): CanonicalDocument {
        val defaultParams = when (kind) {
            RecipeNodeKind.CLEANUP -> mapOf("threshold" to "120")
            RecipeNodeKind.LINE_WORK -> mapOf("weight" to "1.5")
            RecipeNodeKind.FLAT_COLOR -> mapOf("palette" to "copper")
            RecipeNodeKind.MATERIAL_PREVIEW -> mapOf("roughness" to "0.35")
            RecipeNodeKind.EXPORT_PREP -> mapOf("format" to "atlas")
        }
        val graph = doc.recipes.find { it.id == recipeId } ?: RecipeGraph(id = recipeId)
        val nextX = posX ?: (40f + graph.nodes.size * 185f)
        val nextY = posY ?: (60f + (graph.nodes.size % 2) * 110f)
        val newNode = RecipeNode(
            id = "n_${UUID.randomUUID().toString().take(6)}",
            kind = kind,
            title = title.ifBlank { kind.name },
            parameters = defaultParams,
            inputNodeIds = inputNodeIds.filter { id -> graph.nodes.any { it.id == id } },
            posX = nextX,
            posY = nextY
        )
        val nextGraph = graph.copy(nodes = graph.nodes + newNode)
        val updatedRecipes = if (doc.recipes.any { it.id == recipeId }) {
            doc.recipes.map { if (it.id == recipeId) nextGraph else it }
        } else {
            doc.recipes + nextGraph
        }
        return doc.copy(recipes = updatedRecipes)
    }

    fun connectRecipeNodes(
        doc: CanonicalDocument,
        recipeId: String,
        fromUpstreamId: String,
        toDownstreamId: String
    ): Result<CanonicalDocument> {
        if (fromUpstreamId == toDownstreamId) {
            return Result.failure(IllegalArgumentException("Cannot connect node '$fromUpstreamId' to itself (cycle)."))
        }
        val graph = doc.recipes.find { it.id == recipeId } ?: return Result.failure(IllegalArgumentException("Recipe not found."))
        if (graph.nodes.none { it.id == fromUpstreamId } || graph.nodes.none { it.id == toDownstreamId }) {
            return Result.failure(IllegalArgumentException("Missing endpoint node."))
        }
        val candidateGraph = graph.copy(
            nodes = graph.nodes.map { n ->
                if (n.id == toDownstreamId && !n.inputNodeIds.contains(fromUpstreamId)) {
                    n.copy(inputNodeIds = n.inputNodeIds + fromUpstreamId)
                } else n
            }
        )
        val topo = topologicalSort(candidateGraph)
        if (topo.isFailure) {
            return Result.failure(IllegalArgumentException("Connection rejected: creates cycle in Creative Recipe DAG."))
        }
        return Result.success(doc.copy(recipes = doc.recipes.map { if (it.id == recipeId) candidateGraph else it }))
    }

    fun disconnectRecipeNodes(
        doc: CanonicalDocument,
        recipeId: String,
        fromUpstreamId: String,
        toDownstreamId: String
    ): CanonicalDocument {
        val updated = doc.recipes.map { g ->
            if (g.id != recipeId) g
            else g.copy(nodes = g.nodes.map { n ->
                if (n.id == toDownstreamId) n.copy(inputNodeIds = n.inputNodeIds - fromUpstreamId) else n
            })
        }
        return doc.copy(recipes = updated)
    }

    fun moveRecipeNode(
        doc: CanonicalDocument,
        recipeId: String,
        nodeId: String,
        posX: Float,
        posY: Float
    ): CanonicalDocument {
        val updated = doc.recipes.map { g ->
            if (g.id != recipeId) g
            else g.copy(nodes = g.nodes.map { n ->
                if (n.id == nodeId) n.copy(posX = posX.coerceIn(0f, 1600f), posY = posY.coerceIn(0f, 900f)) else n
            })
        }
        return doc.copy(recipes = updated)
    }

    fun removeRecipeNode(doc: CanonicalDocument, recipeId: String, nodeId: String): CanonicalDocument {
        val updated = doc.recipes.map { g ->
            if (g.id != recipeId || g.nodes.size <= 1) g
            else {
                val filtered = g.nodes.filterNot { it.id == nodeId }.map { n ->
                    n.copy(inputNodeIds = n.inputNodeIds.filterNot { it == nodeId })
                }
                g.copy(nodes = filtered, results = g.results - nodeId)
            }
        }
        return doc.copy(recipes = updated)
    }

    fun computeInvalidatedNodeIds(doc: CanonicalDocument, recipeId: String): Set<String> {
        val graph = doc.recipes.find { it.id == recipeId } ?: return emptySet()
        val ordered = topologicalSort(graph).getOrNull() ?: return graph.nodes.map { it.id }.toSet()
        // Predict exactly the same source fingerprint as executeRecipeGraph; a
        // metadata-only document hash invalidated every node after the real-pixel upgrade.
        val source = doc.layers.find { it.id == doc.activeLayerId && !it.id.startsWith("recipe_output_") }
            ?: doc.layers.firstOrNull { it.tiles.isNotEmpty() && !it.id.startsWith("recipe_output_") }
        val baseHash = sha256Short(
            "${doc.documentId}:${source?.id}:${source?.pixelContentHash()}:${source?.transform}"
        )
        val predictedKeys = LinkedHashMap<String, String>()
        val invalidated = LinkedHashSet<String>()
        for (node in ordered) {
            if (validateNodeParameters(node) != null || node.inputNodeIds.any { it in invalidated }) {
                invalidated.add(node.id)
                predictedKeys[node.id] = "invalid"
                continue
            }
            val upKeys = node.inputNodeIds.map { predictedKeys[it] ?: "" }
            val key = computeNodeCacheKey(baseHash, node, upKeys)
            predictedKeys[node.id] = key
            val prev = graph.results[node.id]
            if (prev == null || prev.cacheKey != key || prev.status != "SUCCEEDED") {
                invalidated.add(node.id)
            }
        }
        return invalidated
    }

    fun saveRecipePresetCopy(doc: CanonicalDocument, sourceRecipeId: String, newName: String): CanonicalDocument {
        val src = doc.recipes.find { it.id == sourceRecipeId } ?: doc.recipes.firstOrNull() ?: RecipeGraph()
        val copyId = "recipe_${UUID.randomUUID().toString().take(6)}"
        val saved = src.copy(
            id = copyId,
            name = newName.ifBlank { "${src.name} Preset" }
        )
        // Put active preset first so executeRecipeGraph() defaults to it seamlessly, keeping all saved presets in list
        val others = doc.recipes.filterNot { it.id == copyId }
        return doc.copy(recipes = listOf(saved) + others)
    }

    fun selectActiveRecipe(doc: CanonicalDocument, recipeId: String): CanonicalDocument {
        val target = doc.recipes.find { it.id == recipeId } ?: return doc
        val rest = doc.recipes.filterNot { it.id == recipeId }
        return doc.copy(recipes = listOf(target) + rest)
    }

    fun ensureNodeLayout(graph: RecipeGraph): RecipeGraph {
        val allZero = graph.nodes.all { it.posX == 0f && it.posY == 0f }
        if (!allZero) return graph
        val laidOut = graph.nodes.mapIndexed { idx, node ->
            node.copy(posX = 24f + idx * 190f, posY = if (idx % 2 == 0) 36f else 150f)
        }
        return graph.copy(nodes = laidOut)
    }

    private fun computeNodeCacheKey(baseHash: String, node: RecipeNode, upstreamKeys: List<String>): String {
        val paramStr = node.parameters.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }
        val upStr = upstreamKeys.joinToString("|")
        return sha256Short("pixel-kernels-v1:$baseHash:${node.id}:${node.kind.name}:$paramStr:$upStr")
    }

    private fun sha256Short(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }
}

/**
 * F03 Living Preview Engine:
 * Debounced reactive preview generator that automatically cancels stale jobs on rapid edits
 * and honors a low-power battery-saver pause toggle.
 */
class LivingPreviewEngine(
    private val scope: CoroutineScope,
    private val renderer: SparseTileRenderer
) {
    private val _state = MutableStateFlow(
        LivingPreviewSnapshot(
            revision = 0L,
            bitmap = null,
            isPausedForBattery = false,
            statusText = "Living Preview idle"
        )
    )
    val state: StateFlow<LivingPreviewSnapshot> = _state.asStateFlow()

    private var activeJob: Job? = null

    fun setLowPowerPaused(paused: Boolean) {
        if (paused) activeJob?.cancel()
        _state.value = _state.value.copy(
            isPausedForBattery = paused,
            statusText = if (paused) "Living Preview paused (Battery Saver)" else "Living Preview resumed"
        )
    }

    fun schedulePreview(doc: CanonicalDocument) {
        if (_state.value.isPausedForBattery) {
            _state.value = _state.value.copy(
                revision = doc.revision,
                statusText = "Paused in low-power mode (rev ${doc.revision})"
            )
            return
        }
        activeJob?.cancel()
        activeJob = scope.launch {
            delay(120) // Coalesce rapid stylus ticks
            val targetRev = doc.revision
            val preview = withContext(Dispatchers.Default) {
                val region = renderer.computeArtworkBoundingBox(doc, padding = 32)
                    ?: RectF(-400f, -280f, 400f, 280f)
                val full = renderer.renderFlattenedRegion(doc, region, includePaper = true, includeVectors = true)
                val maxSide = 360f
                val s = minOf(1f, maxSide / max(full.width, full.height))
                val scaled = Bitmap.createScaledBitmap(
                    full,
                    max(1, (full.width * s).roundToInt()),
                    max(1, (full.height * s).roundToInt()),
                    true
                )
                full.recycle()
                scaled
            }
            _state.value = LivingPreviewSnapshot(
                revision = targetRev,
                bitmap = preview,
                isPausedForBattery = false,
                statusText = "Synced rev $targetRev"
            )
        }
    }
}
