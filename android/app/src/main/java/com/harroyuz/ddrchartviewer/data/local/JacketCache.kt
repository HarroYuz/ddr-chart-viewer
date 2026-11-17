package com.harroyuz.ddrchartviewer.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/** Immutable-by-ID assets: no TTL, conditional GET, or background revalidation. */
class JacketCache(
    private val directory: File,
    private val download: (String) -> ByteArray,
    private val validate: (ByteArray) -> Unit = {},
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    suspend fun get(id: String): ByteArray = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9]+"))) { "曲绘 ID 无效" }
        locks.getOrPut(id) { Mutex() }.withLock {
            val file = File(directory, "$id.jpg")
            if (file.isFile) return@withLock file.readBytes()
            val bytes = download(id)
            require(bytes.isNotEmpty()) { "曲绘为空" }
            validate(bytes)
            directory.mkdirs()
            val staging = File.createTempFile("jacket-", ".tmp", directory)
            try {
                staging.outputStream().use { it.write(bytes); it.fd.sync() }
                Files.move(staging.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally { staging.delete() }
            bytes
        }
    }
}
