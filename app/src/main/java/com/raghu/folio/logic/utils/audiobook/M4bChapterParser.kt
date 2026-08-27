package com.raghu.folio.logic.utils.audiobook

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedInputStream
import java.io.InputStream

data class M4bChapterEntry(val startMs: Long, val title: String)

/**
 * Minimal MP4/M4B box parser that extracts Nero-style chapters (the "chpl" atom nested under
 * moov/udta), as produced by common audiobook tools such as m4b-tool. Only reads the `moov` box
 * into memory (bounded by [MAX_MOOV_SIZE_BYTES]) rather than the whole file, since audiobook
 * files themselves can be gigabytes. Does not attempt to parse the alternative QuickTime "chap"
 * text-track chapter format - unsupported files simply yield no chapters (the book remains fully
 * playable/seekable without them).
 */
object M4bChapterParser {
    private const val MAX_MOOV_SIZE_BYTES = 32L * 1024 * 1024

    fun parseChapters(contentResolver: ContentResolver, uri: Uri): List<M4bChapterEntry> = runCatching {
        contentResolver.openInputStream(uri)?.use { stream ->
            val moov = readTopLevelBoxPayload(BufferedInputStream(stream), "moov", MAX_MOOV_SIZE_BYTES)
                ?: return@use emptyList()
            val udta = findChildBoxPayload(moov, "udta") ?: return@use emptyList()
            val chpl = findChildBoxPayload(udta, "chpl") ?: return@use emptyList()
            parseChpl(chpl)
        } ?: emptyList()
    }.getOrDefault(emptyList())

    /** Scans top-level boxes in [stream] until [targetType] is found, returning its payload. */
    private fun readTopLevelBoxPayload(stream: InputStream, targetType: String, maxPayloadSize: Long): ByteArray? {
        val header = ByteArray(8)
        while (true) {
            if (!readFully(stream, header, 8)) return null
            var size = readU32BE(header, 0)
            val type = String(header, 4, 4, Charsets.US_ASCII)
            var headerSize = 8
            if (size == 1L) {
                val ext = ByteArray(8)
                if (!readFully(stream, ext, 8)) return null
                size = readU64BE(ext, 0)
                headerSize = 16
            } else if (size == 0L) {
                // Box extends to EOF - nothing structured left to find after it.
                return null
            }
            val payloadSize = size - headerSize
            if (payloadSize < 0) return null
            if (type == targetType) {
                if (payloadSize > maxPayloadSize) return null
                val payload = ByteArray(payloadSize.toInt())
                return if (readFully(stream, payload, payload.size)) payload else null
            }
            if (!skipFully(stream, payloadSize)) return null
        }
    }

    /** Finds a direct child box of [targetType] within an in-memory [container] box's payload. */
    private fun findChildBoxPayload(container: ByteArray, targetType: String): ByteArray? {
        var offset = 0
        while (offset + 8 <= container.size) {
            var size = readU32BE(container, offset)
            val type = String(container, offset + 4, 4, Charsets.US_ASCII)
            var headerSize = 8
            if (size == 1L) {
                if (offset + 16 > container.size) return null
                size = readU64BE(container, offset + 8)
                headerSize = 16
            } else if (size == 0L) {
                size = (container.size - offset).toLong()
            }
            if (size < headerSize || offset + size > container.size) return null
            if (type == targetType) {
                return container.copyOfRange(offset + headerSize, (offset + size).toInt())
            }
            offset += size.toInt()
        }
        return null
    }

    private fun parseChpl(bytes: ByteArray): List<M4bChapterEntry> {
        if (bytes.size < 5) return emptyList()
        var offset = 0
        val version = bytes[offset].toInt() and 0xFF
        offset += 1 + 3 // version (1) + flags (3)
        if (version != 0) {
            if (offset + 4 > bytes.size) return emptyList()
            offset += 4 // version-specific reserved field
        }
        if (offset >= bytes.size) return emptyList()
        val chapterCount = bytes[offset].toInt() and 0xFF
        offset += 1

        val chapters = mutableListOf<M4bChapterEntry>()
        for (i in 0 until chapterCount) {
            if (offset + 9 > bytes.size) break
            val start = readU64BE(bytes, offset)
            offset += 8
            val titleLen = bytes[offset].toInt() and 0xFF
            offset += 1
            val available = (bytes.size - offset).coerceAtLeast(0)
            val len = minOf(titleLen, available)
            val title = String(bytes, offset, len, Charsets.UTF_8)
            offset += len
            // Start times are in 100-nanosecond units (10,000,000 units/sec).
            chapters += M4bChapterEntry(startMs = start / 10_000L, title = title)
        }
        return chapters
    }

    private fun readU32BE(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)

    private fun readU64BE(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }
        return value
    }

    private fun readFully(stream: InputStream, buffer: ByteArray, len: Int): Boolean {
        var read = 0
        while (read < len) {
            val n = stream.read(buffer, read, len - read)
            if (n == -1) return false
            read += n
        }
        return true
    }

    private fun skipFully(stream: InputStream, count: Long): Boolean {
        var remaining = count
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
            val n = stream.read(buffer, 0, toRead)
            if (n == -1) return false
            remaining -= n
        }
        return true
    }
}
