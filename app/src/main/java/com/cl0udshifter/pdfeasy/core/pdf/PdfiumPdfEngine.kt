package com.cl0udshifter.pdfeasy.core.pdf

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.cl0udshifter.pdfeasy.core.model.CoreResult
import com.cl0udshifter.pdfeasy.core.model.NormalizedRect
import com.cl0udshifter.pdfeasy.core.model.PageDimensions
import com.cl0udshifter.pdfeasy.core.model.PdfCoreError
import com.cl0udshifter.pdfeasy.core.model.TextSearchMatch
import com.cl0udshifter.pdfeasy.core.model.TileRequest
import io.legere.pdfiumandroid.FindResult
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfPage
import io.legere.pdfiumandroid.PdfPasswordException
import io.legere.pdfiumandroid.PdfTextPage
import io.legere.pdfiumandroid.PdfiumCore
import io.legere.pdfiumandroid.util.AlreadyClosedBehavior
import io.legere.pdfiumandroid.util.Config
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

class PdfiumPdfEngine(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PdfEngine {
    private val appContext: Context = context.applicationContext
    private val pdfiumCore: PdfiumCore by lazy {
        PdfiumCore(
            context = appContext,
            config = Config(alreadyClosedBehavior = AlreadyClosedBehavior.IGNORE),
        )
    }

    override suspend fun openSession(
        uriString: String,
        password: String?,
    ): CoreResult<PdfSession> =
        withContext(ioDispatcher) {
            when (val fdResult = openFileDescriptor(uriString)) {
                is CoreResult.Failure -> fdResult
                is CoreResult.Success -> openDocumentFromDescriptor(uriString, fdResult.data, password)
            }
        }

    private fun openDocumentFromDescriptor(
        uriString: String,
        descriptor: ParcelFileDescriptor,
        password: String?,
    ): CoreResult<PdfSession> =
        try {
            val document = pdfiumCore.newDocument(descriptor, password)
            CoreResult.Success(
                PdfiumPdfSession(
                    documentUri = uriString,
                    document = document,
                    bitmapPool = AndroidBitmapPool(),
                ),
            )
        } catch (_: PdfPasswordException) {
            runCatching { descriptor.close() }
            val error =
                if (password == null) {
                    PdfCoreError.PasswordRequired
                } else {
                    PdfCoreError.InvalidPassword
                }
            CoreResult.Failure(error)
        } catch (error: IOException) {
            runCatching { descriptor.close() }
            CoreResult.Failure(
                PdfCoreError.CorruptedOrUnsupportedPdf(error.message ?: "Corrupted or unsupported PDF"),
            )
        } catch (error: IllegalArgumentException) {
            runCatching { descriptor.close() }
            CoreResult.Failure(
                PdfCoreError.CorruptedOrUnsupportedPdf(error.message ?: "Invalid PDF document"),
            )
        }

    private fun openFileDescriptor(uriString: String): CoreResult<ParcelFileDescriptor> {
        val uri = Uri.parse(uriString)
        return try {
            val descriptor =
                when (uri.scheme) {
                    ContentResolver.SCHEME_CONTENT ->
                        appContext.contentResolver.openFileDescriptor(uri, MODE_READ)
                    ContentResolver.SCHEME_FILE ->
                        uri.path?.let(::File)
                            ?.takeIf { it.exists() && it.isFile }
                            ?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
                    else -> null
                }
            if (descriptor != null) {
                CoreResult.Success(descriptor)
            } else {
                CoreResult.Failure(PdfCoreError.FileNotFoundOrMoved)
            }
        } catch (_: SecurityException) {
            CoreResult.Failure(PdfCoreError.PermissionDenied)
        } catch (_: FileNotFoundException) {
            CoreResult.Failure(PdfCoreError.FileNotFoundOrMoved)
        } catch (_: IllegalArgumentException) {
            CoreResult.Failure(PdfCoreError.FileNotFoundOrMoved)
        } catch (error: IOException) {
            CoreResult.Failure(PdfCoreError.IoFailure(error.message ?: "Failed to open PDF descriptor"))
        }
    }

    private companion object {
        const val MODE_READ = "r"
    }
}

internal class PdfiumPdfSession(
    override val documentUri: String,
    private val document: PdfDocument,
    private val bitmapPool: AndroidBitmapPool,
) : PdfSession {
    private val closed = AtomicBoolean(false)
    private val nativeLock = Any()
    private val executor =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "pdfeasy-pdfium-session").apply { isDaemon = true }
        }
    private val sessionDispatcher: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()

    override val pageCount: Int = document.getPageCount().coerceAtLeast(0)

    override suspend fun getPageDimensions(pageIndex: Int): CoreResult<PageDimensions> {
        if (pageIndex !in 0 until pageCount) {
            return CoreResult.Failure(
                PdfCoreError.CorruptedOrUnsupportedPdf("Page index out of bounds: $pageIndex"),
            )
        }
        return runOnSession {
            document.openPage(pageIndex).use { page ->
                val widthPt = page.getPageWidthPoint()
                val heightPt = page.getPageHeightPoint()
                if (widthPt <= 0 || heightPt <= 0) {
                    CoreResult.Failure(
                        PdfCoreError.CorruptedOrUnsupportedPdf("Invalid page dimensions for page $pageIndex"),
                    )
                } else {
                    CoreResult.Success(
                        PageDimensions(
                            pageIndex = pageIndex,
                            widthPoints = widthPt,
                            heightPoints = heightPt,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun renderTile(request: TileRequest): CoreResult<Bitmap> {
        val region = TileGeometry.computeRenderRegion(request)
        val isInvalidPage = request.pageIndex !in 0 until pageCount
        if (region == null || isInvalidPage) {
            return CoreResult.Failure(
                PdfCoreError.CorruptedOrUnsupportedPdf("Invalid tile request for page ${request.pageIndex}"),
            )
        }

        return runOnSession {
            val bitmap =
                acquireBitmapSafely(request.targetWidthPx, request.targetHeightPx)
                    ?: return@runOnSession CoreResult.Failure(
                        PdfCoreError.IoFailure("Out of memory allocating tile bitmap"),
                    )
            document.openPage(request.pageIndex).use { page ->
                page.renderPageBitmap(
                    bitmap = bitmap,
                    startX = region.startX,
                    startY = region.startY,
                    drawSizeX = region.drawSizeX,
                    drawSizeY = region.drawSizeY,
                    renderAnnot = true,
                    textMask = false,
                    canvasColor = Color.WHITE,
                    pageBackgroundColor = Color.WHITE,
                )
            }
            CoreResult.Success(bitmap)
        }
    }

    override fun recycleBitmap(bitmap: Bitmap) {
        if (closed.get()) {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        } else {
            bitmapPool.recycle(bitmap)
        }
    }

    override fun search(query: String): Flow<TextSearchMatch> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty() || pageCount <= 0) {
            return emptyFlow()
        }
        return flow {
            for (pageIndex in 0 until pageCount) {
                currentCoroutineContext().ensureActive()
                if (closed.get()) break
                val pageMatches =
                    when (val result = runOnSession { extractMatchesOnPage(pageIndex, normalizedQuery) }) {
                        is CoreResult.Success -> result.data
                        is CoreResult.Failure -> emptyList()
                    }
                for (match in pageMatches) {
                    currentCoroutineContext().ensureActive()
                    emit(match)
                }
            }
        }
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            synchronized(nativeLock) {
                runCatching { document.close() }
            }
            bitmapPool.clear()
            executor.shutdown()
        }
    }

    private suspend fun <T> runOnSession(block: () -> CoreResult<T>): CoreResult<T> {
        if (closed.get()) {
            return CoreResult.Failure(PdfCoreError.IoFailure("PDF session is already closed"))
        }
        return try {
            withContext(sessionDispatcher) {
                synchronized(nativeLock) {
                    if (closed.get()) {
                        CoreResult.Failure(PdfCoreError.IoFailure("PDF session is already closed"))
                    } else {
                        block()
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            CoreResult.Failure(PdfCoreError.IoFailure("PDF session is already closed"))
        }
    }

    private fun acquireBitmapSafely(
        widthPx: Int,
        heightPx: Int,
    ): Bitmap? =
        try {
            bitmapPool.acquire(widthPx, heightPx)
        } catch (_: OutOfMemoryError) {
            bitmapPool.clear()
            try {
                bitmapPool.acquire(widthPx, heightPx)
            } catch (_: OutOfMemoryError) {
                null
            }
        }

    private fun extractMatchesOnPage(
        pageIndex: Int,
        query: String,
    ): CoreResult<List<TextSearchMatch>> =
        CoreResult.Success(
            PdfPageTextSearcher.searchPage(
                document = document,
                pageIndex = pageIndex,
                query = query,
                isCancelled = closed::get,
            ),
        )
}

private object PdfPageTextSearcher {
    private const val VIRTUAL_DEVICE_SCALE = 10_000

    fun searchPage(
        document: PdfDocument,
        pageIndex: Int,
        query: String,
        isCancelled: () -> Boolean,
    ): List<TextSearchMatch> =
        document.openPage(pageIndex).use { page ->
            page.openTextPage().use { textPage ->
                val totalChars = textPage.textPageCountChars()
                val findResult =
                    if (totalChars > 0) textPage.findStart(query, emptySet(), 0) else null
                if (findResult == null) {
                    emptyList()
                } else {
                    collectMatches(
                        pageIndex = pageIndex,
                        page = page,
                        textPage = textPage,
                        findResult = findResult,
                        isCancelled = isCancelled,
                    )
                }
            }
        }

    private fun collectMatches(
        pageIndex: Int,
        page: PdfPage,
        textPage: PdfTextPage,
        findResult: FindResult,
        isCancelled: () -> Boolean,
    ): List<TextSearchMatch> {
        val totalChars = textPage.textPageCountChars()
        val matches = mutableListOf<TextSearchMatch>()
        var matchIndex = 0
        try {
            while (!isCancelled() && findResult.findNext()) {
                val startIndex = findResult.getSchResultIndex()
                val matchLength = findResult.getSchCount()
                if (matchLength > 0) {
                    matches.add(
                        TextSearchMatch(
                            pageIndex = pageIndex,
                            matchIndexInPage = matchIndex,
                            snippet = extractMatchSnippet(textPage, totalChars, startIndex, matchLength),
                            boundsNormalized =
                                extractNormalizedBounds(page, textPage, startIndex, matchLength),
                        ),
                    )
                    matchIndex++
                }
            }
        } finally {
            findResult.closeFind()
        }
        return matches
    }

    private fun extractNormalizedBounds(
        page: PdfPage,
        textPage: PdfTextPage,
        startIndex: Int,
        matchLength: Int,
    ): List<NormalizedRect> {
        val rectCount = textPage.textPageCountRects(startIndex, matchLength)
        if (rectCount <= 0) return emptyList()
        val bounds = ArrayList<NormalizedRect>(rectCount)
        for (rectIndex in 0 until rectCount) {
            val rawRect = textPage.textPageGetRect(rectIndex) ?: continue
            val deviceRect =
                page.mapRectToDevice(
                    startX = 0,
                    startY = 0,
                    sizeX = VIRTUAL_DEVICE_SCALE,
                    sizeY = VIRTUAL_DEVICE_SCALE,
                    rotate = 0,
                    coords = rawRect,
                )
            TileGeometry
                .normalizeDeviceRect(
                    leftPx = deviceRect.left,
                    topPx = deviceRect.top,
                    rightPx = deviceRect.right,
                    bottomPx = deviceRect.bottom,
                    referenceSizePx = VIRTUAL_DEVICE_SCALE,
                )?.let(bounds::add)
        }
        return bounds
    }

    private fun extractMatchSnippet(
        textPage: PdfTextPage,
        totalChars: Int,
        startIndex: Int,
        matchLength: Int,
    ): String {
        val window = SearchSnippetExtractor.computeWindow(totalChars, startIndex, matchLength)
        if (window.isEmpty()) return ""
        val length = window.last - window.first + 1
        val rawSlice = textPage.textPageGetText(window.first, length).orEmpty()
        return SearchSnippetExtractor.formatSnippet(
            rawSlice = rawSlice,
            truncatedStart = window.first > 0,
            truncatedEnd = window.last < totalChars - 1,
        )
    }
}
