package com.cl0udshifter.pdfeasy.core.pdf

import android.graphics.Bitmap
import android.graphics.Color

internal interface BitmapAdapter<T : Any> {
    fun width(bitmap: T): Int

    fun height(bitmap: T): Int

    fun allocationBytes(bitmap: T): Long

    fun isReusable(bitmap: T): Boolean

    fun reconfigure(
        bitmap: T,
        widthPx: Int,
        heightPx: Int,
    ): Boolean

    fun create(
        widthPx: Int,
        heightPx: Int,
    ): T

    fun dispose(bitmap: T)
}

/**
 * Pool LRU de reutilizacao de bitmaps para evitar alocacoes e pausas de GC durante rolagem a 120FPS.
 */
internal class LruBitmapPool<T : Any>(
    private val maxBytes: Long,
    private val adapter: BitmapAdapter<T>,
) {
    private val lock = Any()
    private val entries = ArrayList<PooledEntry<T>>()
    private var currentBytes: Long = 0L

    val pooledByteCount: Long
        get() = synchronized(lock) { currentBytes }

    val pooledItemCount: Int
        get() = synchronized(lock) { entries.size }

    fun acquire(
        widthPx: Int,
        heightPx: Int,
    ): T {
        require(widthPx > 0 && heightPx > 0) {
            "Bitmap dimensions must be positive: ${widthPx}x$heightPx"
        }
        val requiredBytes = widthPx.toLong() * heightPx.toLong() * BYTES_PER_ARGB8888_PIXEL
        val reused =
            synchronized(lock) {
                takeExactMatch(widthPx, heightPx) ?: takeReconfigurableMatch(widthPx, heightPx, requiredBytes)
            }
        return reused ?: adapter.create(widthPx, heightPx)
    }

    fun recycle(bitmap: T) {
        if (!adapter.isReusable(bitmap)) return
        val bytes = adapter.allocationBytes(bitmap)
        if (bytes <= 0L || bytes > maxBytes) {
            adapter.dispose(bitmap)
            return
        }

        val evicted = ArrayList<T>()
        synchronized(lock) {
            if (entries.none { it.bitmap === bitmap }) {
                entries.add(PooledEntry(bitmap, bytes))
                currentBytes += bytes
                while (currentBytes > maxBytes && entries.isNotEmpty()) {
                    val oldest = entries.removeAt(0)
                    currentBytes -= oldest.bytes
                    evicted.add(oldest.bitmap)
                }
            }
        }
        evicted.forEach(adapter::dispose)
    }

    fun clear() {
        val toDispose =
            synchronized(lock) {
                val snapshot = entries.map { it.bitmap }
                entries.clear()
                currentBytes = 0L
                snapshot
            }
        toDispose.forEach(adapter::dispose)
    }

    private fun takeExactMatch(
        widthPx: Int,
        heightPx: Int,
    ): T? {
        for (index in entries.lastIndex downTo 0) {
            val candidate = entries[index].bitmap
            val matches =
                adapter.isReusable(candidate) &&
                    adapter.width(candidate) == widthPx &&
                    adapter.height(candidate) == heightPx
            if (matches) {
                val removed = entries.removeAt(index)
                currentBytes -= removed.bytes
                return candidate
            }
        }
        return null
    }

    private fun takeReconfigurableMatch(
        widthPx: Int,
        heightPx: Int,
        requiredBytes: Long,
    ): T? {
        val maxCandidateBytes = requiredBytes * MAX_REUSE_SIZE_MULTIPLIER
        var bestIndex = -1
        var bestBytes = Long.MAX_VALUE

        for (index in entries.indices) {
            val entry = entries[index]
            val candidate = entry.bitmap
            val eligible =
                adapter.isReusable(candidate) &&
                    entry.bytes in requiredBytes..maxCandidateBytes
            if (eligible && entry.bytes < bestBytes) {
                bestBytes = entry.bytes
                bestIndex = index
            }
        }

        val result: T?
        if (bestIndex >= 0) {
            val removed = entries.removeAt(bestIndex)
            currentBytes -= removed.bytes
            if (adapter.reconfigure(removed.bitmap, widthPx, heightPx)) {
                result = removed.bitmap
            } else {
                adapter.dispose(removed.bitmap)
                result = null
            }
        } else {
            result = null
        }
        return result
    }

    private data class PooledEntry<T>(
        val bitmap: T,
        val bytes: Long,
    )

    private companion object {
        const val BYTES_PER_ARGB8888_PIXEL = 4L
        const val MAX_REUSE_SIZE_MULTIPLIER = 2L
    }
}

internal class AndroidBitmapPool(
    maxBytes: Long = defaultMaxBytes(),
) {
    private val pool = LruBitmapPool(maxBytes = maxBytes, adapter = AndroidBitmapAdapter)

    fun acquire(
        widthPx: Int,
        heightPx: Int,
    ): Bitmap {
        val bitmap = pool.acquire(widthPx, heightPx)
        bitmap.eraseColor(Color.WHITE)
        return bitmap
    }

    fun recycle(bitmap: Bitmap) {
        pool.recycle(bitmap)
    }

    fun clear() {
        pool.clear()
    }

    private object AndroidBitmapAdapter : BitmapAdapter<Bitmap> {
        private const val BYTES_PER_PIXEL = 4

        override fun width(bitmap: Bitmap): Int = bitmap.width

        override fun height(bitmap: Bitmap): Int = bitmap.height

        override fun allocationBytes(bitmap: Bitmap): Long = bitmap.allocationByteCount.toLong()

        override fun isReusable(bitmap: Bitmap): Boolean =
            !bitmap.isRecycled && bitmap.isMutable && bitmap.config == Bitmap.Config.ARGB_8888

        override fun reconfigure(
            bitmap: Bitmap,
            widthPx: Int,
            heightPx: Int,
        ): Boolean {
            val requiredBytes = widthPx * heightPx * BYTES_PER_PIXEL
            val canFit = !bitmap.isRecycled && bitmap.isMutable && bitmap.allocationByteCount >= requiredBytes
            if (!canFit) return false
            return runCatching {
                bitmap.reconfigure(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                true
            }.getOrDefault(false)
        }

        override fun create(
            widthPx: Int,
            heightPx: Int,
        ): Bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)

        override fun dispose(bitmap: Bitmap) {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    companion object {
        private const val BYTES_PER_MEGABYTE = 1024L * 1024L
        private const val MIN_POOL_BYTES = 16L * BYTES_PER_MEGABYTE
        private const val MAX_POOL_BYTES = 64L * BYTES_PER_MEGABYTE
        private const val HEAP_FRACTION = 8L

        fun defaultMaxBytes(): Long =
            (Runtime.getRuntime().maxMemory() / HEAP_FRACTION).coerceIn(MIN_POOL_BYTES, MAX_POOL_BYTES)
    }
}
