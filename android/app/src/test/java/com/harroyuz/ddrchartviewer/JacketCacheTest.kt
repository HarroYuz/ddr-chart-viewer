package com.harroyuz.ddrchartviewer

import com.harroyuz.ddrchartviewer.data.local.JacketCache
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class JacketCacheTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun existingIdNeverDownloadsAgainEvenAfterRepositoryRestart() = runBlocking {
        val calls = AtomicInteger()
        val cache = JacketCache(temp.root, { calls.incrementAndGet(); "image".toByteArray() })
        cache.get("abc")
        cache.get("abc")
        val reopened = JacketCache(temp.root, { error("Network must not be called for an existing ID") })
        assertEquals("image", reopened.get("abc").toString(Charsets.UTF_8))
        assertEquals(1, calls.get())
    }
    @Test fun simultaneousRequestsOnlyDownloadOnce() = runBlocking {
        val calls = AtomicInteger()
        val cache = JacketCache(temp.root, { calls.incrementAndGet(); "image".toByteArray() })
        coroutineScope { (1..20).map { async(Dispatchers.Default) { cache.get("sameID") } }.awaitAll() }
        assertEquals(1, calls.get())
    }
    @Test fun failedDownloadCanBeRetriedAndDoesNotCommitAnId() = runBlocking {
        val calls = AtomicInteger()
        val cache = JacketCache(temp.root, { if (calls.incrementAndGet() == 1) throw IOException("offline"); "image".toByteArray() })
        assertTrue(runCatching { cache.get("abc") }.isFailure)
        assertFalse(temp.root.resolve("abc.jpg").exists())
        cache.get("abc")
        assertEquals(2, calls.get())
    }
    @Test fun invalidImageIsNotCached() = runBlocking {
        val cache = JacketCache(temp.root, { "html error".toByteArray() }, { error("not an image") })
        assertTrue(runCatching { cache.get("abc") }.isFailure)
        assertFalse(temp.root.resolve("abc.jpg").exists())
    }
    @Test fun idCannotEscapeCacheDirectory() = runBlocking {
        val cache = JacketCache(temp.root, { error("No network") })
        assertTrue(runCatching { cache.get("../secret") }.isFailure)
    }
}
