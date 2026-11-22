package com.cl0udshifter.pdfeasy.core.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BitmapPoolTest {
    private class FakeBitmap(
        var widthPx: Int,
        var heightPx: Int,
        val bufferBytes: Long = widthPx.toLong() * heightPx.toLong() * 4L,
        var disposed: Boolean = false,
    )

    private class FakeAdapter : BitmapAdapter<FakeBitmap> {
        var createdCount = 0

        override fun width(bitmap: FakeBitmap): Int = bitmap.widthPx

        override fun height(bitmap: FakeBitmap): Int = bitmap.heightPx

        override fun allocationBytes(bitmap: FakeBitmap): Long = bitmap.bufferBytes

        override fun isReusable(bitmap: FakeBitmap): Boolean = !bitmap.disposed

        override fun reconfigure(
            bitmap: FakeBitmap,
            widthPx: Int,
            heightPx: Int,
        ): Boolean {
            if (bitmap.disposed || bitmap.bufferBytes < widthPx.toLong() * heightPx.toLong() * 4L) {
                return false
            }
            bitmap.widthPx = widthPx
            bitmap.heightPx = heightPx
            return true
        }

        override fun create(
            widthPx: Int,
            heightPx: Int,
        ): FakeBitmap {
            createdCount++
            return FakeBitmap(widthPx, heightPx)
        }

        override fun dispose(bitmap: FakeBitmap) {
            bitmap.disposed = true
        }
    }

    @Test
    fun `exact match reuse avoids creating a new bitmap`() {
        val adapter = FakeAdapter()
        val pool = LruBitmapPool(maxBytes = 1024L * 1024L, adapter = adapter)

        val first = pool.acquire(100, 100)
        pool.recycle(first)
        assertEquals(1, pool.pooledItemCount)

        val reused = pool.acquire(100, 100)
        assertSame(first, reused)
        assertEquals(1, adapter.createdCount)
        assertEquals(0, pool.pooledItemCount)
    }

    @Test
    fun `reconfigure reuses larger bitmap within 2x byte limit and skips oversized ones`() {
        val adapter = FakeAdapter()
        val pool = LruBitmapPool(maxBytes = 10L * 1024L * 1024L, adapter = adapter)

        val medium = pool.acquire(100, 100)
        pool.recycle(medium)
        val reconfigured = pool.acquire(80, 90)
        assertSame(medium, reconfigured)
        assertEquals(80, reconfigured.widthPx)
        assertEquals(90, reconfigured.heightPx)

        val huge = pool.acquire(400, 400)
        pool.recycle(huge)
        val tiny = pool.acquire(20, 20)
        assertNotSame(huge, tiny)
        assertEquals(3, adapter.createdCount)
    }

    @Test
    fun `lru eviction disposes oldest entry when pool exceeds byte budget`() {
        val adapter = FakeAdapter()
        val oneTileBytes = 100L * 100L * 4L
        val pool = LruBitmapPool(maxBytes = oneTileBytes * 2L, adapter = adapter)

        val oldest = pool.acquire(100, 100)
        val middle = pool.acquire(100, 100)
        val newest = pool.acquire(100, 100)

        pool.recycle(oldest)
        pool.recycle(middle)
        pool.recycle(newest)

        assertTrue(oldest.disposed)
        assertEquals(2, pool.pooledItemCount)
        assertEquals(oneTileBytes * 2L, pool.pooledByteCount)
    }

    @Test
    fun `recycling same bitmap twice is ignored and clear disposes all`() {
        val adapter = FakeAdapter()
        val pool = LruBitmapPool(maxBytes = 1024L * 1024L, adapter = adapter)

        val bitmap = pool.acquire(50, 50)
        pool.recycle(bitmap)
        pool.recycle(bitmap)

        assertEquals(1, pool.pooledItemCount)
        assertEquals(50L * 50L * 4L, pool.pooledByteCount)

        pool.clear()
        assertTrue(bitmap.disposed)
        assertEquals(0, pool.pooledItemCount)
        assertEquals(0L, pool.pooledByteCount)
    }
}
