package com.harroyuz.ddrchartviewer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.harroyuz.ddrchartviewer.data.local.JacketCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class JacketRepository(context: Context) {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    private val memory = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val slots = Semaphore(3)
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val disk = JacketCache(File(context.filesDir, "jackets"), { id ->
        client.newCall(Request.Builder().url("https://3icecream.com/img/banners/f/$id.jpg").build()).execute().use {
            check(it.isSuccessful) { "曲绘请求失败（${it.code}）" }
            it.body?.bytes() ?: error("曲绘响应为空")
        }
    }, { bytes ->
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192) { "曲绘图片格式无效" }
    })

    suspend fun load(id: String): Bitmap? = withContext(Dispatchers.IO) {
        memory.get(id)?.let { return@withContext it }
        if (System.currentTimeMillis() - (failedAt[id] ?: 0) < 300_000) return@withContext null
        slots.withPermit {
            memory.get(id)?.let { return@withPermit it }
            try {
                val bytes = disk.get(id)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val options = BitmapFactory.Options().apply { inSampleSize = 1; while (bounds.outWidth / inSampleSize > 512 || bounds.outHeight / inSampleSize > 512) inSampleSize *= 2 }
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("曲绘解码失败")
                memory.put(id, bitmap)
                bitmap
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
              catch (_: Exception) { failedAt[id] = System.currentTimeMillis(); null }
        }
    }
}
