package com.cl0udshifter.pdfeasy.ui.reader

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.cl0udshifter.pdfeasy.core.model.CoreResult
import com.cl0udshifter.pdfeasy.core.model.NormalizedRect
import com.cl0udshifter.pdfeasy.core.model.PageDimensions
import com.cl0udshifter.pdfeasy.core.model.TileRequest
import com.cl0udshifter.pdfeasy.core.pdf.PdfSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class RenderedBitmapEntry(
    val bitmap: Bitmap,
    val imageBitmap: ImageBitmap,
    val widthPx: Int,
    val heightPx: Int,
)

@Immutable
data class RenderedDetailTile(
    val pageIndex: Int,
    val cropNormalized: NormalizedRect,
    val entry: RenderedBitmapEntry,
)

data class TileSyncRequest(
    val session: PdfSession,
    val visibleRange: IntRange,
    val pageDimensions: Map<Int, PageDimensions>,
    val viewportWidthPx: Int,
    val zoomScale: Float,
    val primaryCrop: Pair<Int, NormalizedRect>?,
)

private data class DetailTileParams(
    val session: PdfSession,
    val pageIndex: Int,
    val dimensions: PageDimensions?,
    val viewportWidthPx: Int,
    val zoomScale: Float,
    val crop: NormalizedRect,
)

/**
 * Coordena renderizacao progressiva de paginas base e tiles em alta resolucao sem alocar no frame loop.
 */
class ReaderBitmapController(
    private val scope: CoroutineScope,
    private val recycleDelayMs: Long = DEFAULT_RECYCLE_DELAY_MS,
) {
    val basePages = mutableStateMapOf<Int, RenderedBitmapEntry>()
    val detailTiles = mutableStateMapOf<Int, RenderedDetailTile>()

    private val activeBaseJobs = mutableMapOf<Int, Job>()
    private val activeTileJobs = mutableMapOf<Int, Job>()
    private val quarantinedBitmaps = mutableListOf<Bitmap>()
    private var quarantineDrainJob: Job? = null

    fun syncVisiblePages(sync: TileSyncRequest) {
        val session = sync.session
        if (session.pageCount <= 0 || sync.viewportWidthPx <= 0 || sync.visibleRange.isEmpty()) return

        val keepStart = (sync.visibleRange.first - KEEP_RADIUS).coerceAtLeast(0)
        val keepEnd = (sync.visibleRange.last + KEEP_RADIUS).coerceAtMost(session.pageCount - 1)
        evictPagesOutside(session, keepStart..keepEnd)

        val renderStart = (sync.visibleRange.first - PREFETCH_RADIUS).coerceAtLeast(0)
        val renderEnd = (sync.visibleRange.last + PREFETCH_RADIUS).coerceAtMost(session.pageCount - 1)
        val baseWidthPx = computeBasePageWidthPx(sync.viewportWidthPx)

        for (pageIndex in renderStart..renderEnd) {
            if (!basePages.containsKey(pageIndex) && !activeBaseJobs.containsKey(pageIndex)) {
                launchBaseRender(session, pageIndex, sync.pageDimensions[pageIndex], baseWidthPx)
            }
        }

        val primaryCrop = sync.primaryCrop
        if (sync.zoomScale > DETAIL_TILE_ZOOM_THRESHOLD && primaryCrop != null) {
            ensureDetailTile(
                DetailTileParams(
                    session = session,
                    pageIndex = primaryCrop.first,
                    dimensions = sync.pageDimensions[primaryCrop.first],
                    viewportWidthPx = sync.viewportWidthPx,
                    zoomScale = sync.zoomScale,
                    crop = primaryCrop.second,
                ),
            )
        } else if (detailTiles.isNotEmpty()) {
            clearAllDetailTiles(session)
        }
    }

    fun releaseSessionBitmaps(session: PdfSession?) {
        activeBaseJobs.values.forEach { it.cancel() }
        activeBaseJobs.clear()
        activeTileJobs.values.forEach { it.cancel() }
        activeTileJobs.clear()
        quarantineDrainJob?.cancel()
        quarantineDrainJob = null

        val allToRecycle = ArrayList<Bitmap>(basePages.size + detailTiles.size + quarantinedBitmaps.size)
        basePages.values.forEach { allToRecycle.add(it.bitmap) }
        basePages.clear()
        detailTiles.values.forEach { allToRecycle.add(it.entry.bitmap) }
        detailTiles.clear()
        allToRecycle.addAll(quarantinedBitmaps)
        quarantinedBitmaps.clear()

        if (session != null) {
            allToRecycle.forEach(session::recycleBitmap)
        }
    }

    private fun launchBaseRender(
        session: PdfSession,
        pageIndex: Int,
        dimensions: PageDimensions?,
        baseWidthPx: Int,
    ) {
        val aspectRatio = dimensions?.aspectRatio?.takeIf { it > 0f } ?: DEFAULT_ASPECT_RATIO
        val targetHeightPx = (baseWidthPx / aspectRatio).roundToInt().coerceIn(MIN_TILE_DIM_PX, MAX_BASE_HEIGHT_PX)

        activeBaseJobs[pageIndex] =
            scope.launch {
                try {
                    val request =
                        TileRequest(
                            pageIndex = pageIndex,
                            targetWidthPx = baseWidthPx,
                            targetHeightPx = targetHeightPx,
                        )
                    val result = session.renderTile(request)
                    if (result is CoreResult.Success) {
                        val bitmap = result.data
                        val previous =
                            basePages.put(
                                pageIndex,
                                RenderedBitmapEntry(bitmap, bitmap.asImageBitmap(), bitmap.width, bitmap.height),
                            )
                        if (previous != null && previous.bitmap !== bitmap) {
                            scheduleRecycle(session, previous.bitmap)
                        }
                    }
                } finally {
                    activeBaseJobs.remove(pageIndex)
                }
            }
    }

    private fun ensureDetailTile(params: DetailTileParams) {
        val existing = detailTiles[params.pageIndex]
        if (existing != null && coversCropClosely(existing.cropNormalized, params.crop)) return
        if (activeTileJobs.containsKey(params.pageIndex)) return

        val stalePages = detailTiles.keys.filter { it != params.pageIndex }
        stalePages.forEach { stalePage ->
            detailTiles.remove(stalePage)?.let { scheduleRecycle(params.session, it.entry.bitmap) }
        }

        val aspectRatio = params.dimensions?.aspectRatio?.takeIf { it > 0f } ?: DEFAULT_ASPECT_RATIO
        val fullZoomedWidth = params.viewportWidthPx * params.zoomScale
        val fullZoomedHeight = fullZoomedWidth / aspectRatio
        val targetWidth =
            (fullZoomedWidth * (params.crop.right - params.crop.left))
                .roundToInt()
                .coerceIn(MIN_TILE_DIM_PX, MAX_DETAIL_TILE_PX)
        val targetHeight =
            (fullZoomedHeight * (params.crop.bottom - params.crop.top))
                .roundToInt()
                .coerceIn(MIN_TILE_DIM_PX, MAX_DETAIL_TILE_PX)

        activeTileJobs[params.pageIndex] =
            scope.launch {
                try {
                    val request =
                        TileRequest(
                            pageIndex = params.pageIndex,
                            targetWidthPx = targetWidth,
                            targetHeightPx = targetHeight,
                            cropNormalized = params.crop,
                        )
                    val result = params.session.renderTile(request)
                    if (result is CoreResult.Success) {
                        val bitmap = result.data
                        val previous =
                            detailTiles.put(
                                params.pageIndex,
                                RenderedDetailTile(
                                    pageIndex = params.pageIndex,
                                    cropNormalized = params.crop,
                                    entry = RenderedBitmapEntry(
                                        bitmap = bitmap,
                                        imageBitmap = bitmap.asImageBitmap(),
                                        widthPx = bitmap.width,
                                        heightPx = bitmap.height,
                                    ),
                                ),
                            )
                        if (previous != null && previous.entry.bitmap !== bitmap) {
                            scheduleRecycle(params.session, previous.entry.bitmap)
                        }
                    }
                } finally {
                    activeTileJobs.remove(params.pageIndex)
                }
            }
    }

    private fun evictPagesOutside(
        session: PdfSession,
        keepRange: IntRange,
    ) {
        basePages.keys.filter { it !in keepRange }.forEach { page ->
            activeBaseJobs.remove(page)?.cancel()
            basePages.remove(page)?.let { scheduleRecycle(session, it.bitmap) }
        }
        detailTiles.keys.filter { it !in keepRange }.forEach { page ->
            activeTileJobs.remove(page)?.cancel()
            detailTiles.remove(page)?.let { scheduleRecycle(session, it.entry.bitmap) }
        }
    }

    private fun clearAllDetailTiles(session: PdfSession) {
        activeTileJobs.values.forEach { it.cancel() }
        activeTileJobs.clear()
        val removed = detailTiles.values.toList()
        detailTiles.clear()
        removed.forEach { scheduleRecycle(session, it.entry.bitmap) }
    }

    private fun scheduleRecycle(
        session: PdfSession,
        bitmap: Bitmap,
    ) {
        if (recycleDelayMs <= 0L) {
            session.recycleBitmap(bitmap)
            return
        }
        quarantinedBitmaps.add(bitmap)
        if (quarantineDrainJob == null || quarantineDrainJob?.isActive == false) {
            quarantineDrainJob =
                scope.launch {
                    delay(recycleDelayMs)
                    val batch = quarantinedBitmaps.toList()
                    quarantinedBitmaps.clear()
                    batch.forEach(session::recycleBitmap)
                }
        }
    }

    private fun computeBasePageWidthPx(viewportWidthPx: Int): Int =
        (viewportWidthPx * BASE_RES_SCALE).roundToInt().coerceIn(MIN_BASE_WIDTH_PX, MAX_BASE_WIDTH_PX)

    private fun coversCropClosely(
        cached: NormalizedRect,
        requested: NormalizedRect,
    ): Boolean {
        val fitsInside =
            cached.left <= requested.left + CROP_TOLERANCE &&
                cached.top <= requested.top + CROP_TOLERANCE &&
                cached.right >= requested.right - CROP_TOLERANCE &&
                cached.bottom >= requested.bottom - CROP_TOLERANCE
        val sameScale =
            abs((cached.right - cached.left) - (requested.right - requested.left)) < CROP_SCALE_TOLERANCE
        return fitsInside && sameScale
    }

    companion object {
        const val DETAIL_TILE_ZOOM_THRESHOLD = 1.35f
        private const val PREFETCH_RADIUS = 1
        private const val KEEP_RADIUS = 2
        private const val BASE_RES_SCALE = 0.8f
        private const val MIN_BASE_WIDTH_PX = 420
        private const val MAX_BASE_WIDTH_PX = 960
        private const val MAX_BASE_HEIGHT_PX = 1920
        private const val MAX_DETAIL_TILE_PX = 2048
        private const val MIN_TILE_DIM_PX = 64
        private const val DEFAULT_ASPECT_RATIO = 595f / 842f
        private const val CROP_TOLERANCE = 0.02f
        private const val CROP_SCALE_TOLERANCE = 0.2f
        private const val DEFAULT_RECYCLE_DELAY_MS = 32L
    }
}
