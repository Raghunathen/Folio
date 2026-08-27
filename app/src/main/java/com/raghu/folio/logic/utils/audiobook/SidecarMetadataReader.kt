package com.raghu.folio.logic.utils.audiobook

import android.content.ContentResolver
import android.util.Xml
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser

/**
 * Reads Audiobookshelf/Calibre-style sidecar files that may sit next to the audio in a book
 * folder: `cover.*`/`folder.*` art, `metadata.json`, a Calibre/OPF `*.opf` package file, and a
 * plain-text `desc.txt`/`description.txt` as a last-resort description.
 */
object SidecarMetadataReader {
    private val COVER_NAMES = setOf(
        "cover.jpg", "cover.jpeg", "cover.png", "cover.webp",
        "folder.jpg", "folder.jpeg", "folder.png",
    )

    fun read(contentResolver: ContentResolver, bookFolder: DocumentFile): SidecarMetadata {
        val children = bookFolder.listFiles()
        var result = SidecarMetadata()

        children.firstOrNull { it.name?.lowercase() in COVER_NAMES }?.let {
            result = result.copy(coverUri = it.uri.toString())
        }

        children.firstOrNull { it.name?.equals("metadata.json", ignoreCase = true) == true }
            ?.let { file -> readJson(contentResolver, file)?.let { result = merge(result, it) } }

        children.firstOrNull { it.name?.endsWith(".opf", ignoreCase = true) == true }
            ?.let { file -> readOpf(contentResolver, file)?.let { result = merge(result, it) } }

        if (result.description == null) {
            children.firstOrNull {
                it.name?.equals("desc.txt", ignoreCase = true) == true ||
                    it.name?.equals("description.txt", ignoreCase = true) == true
            }?.let { file ->
                readText(contentResolver, file)?.let { result = result.copy(description = it.trim()) }
            }
        }

        return result
    }

    // Base wins over extra - callers read files in priority order (metadata.json before .opf).
    private fun merge(base: SidecarMetadata, extra: SidecarMetadata) = base.copy(
        title = base.title ?: extra.title,
        author = base.author ?: extra.author,
        narrator = base.narrator ?: extra.narrator,
        description = base.description ?: extra.description,
        seriesName = base.seriesName ?: extra.seriesName,
        seriesIndex = base.seriesIndex ?: extra.seriesIndex,
    )

    private fun readText(contentResolver: ContentResolver, file: DocumentFile): String? = runCatching {
        contentResolver.openInputStream(file.uri)?.use { it.bufferedReader().readText() }
    }.getOrNull()

    private fun readJson(contentResolver: ContentResolver, file: DocumentFile): SidecarMetadata? = runCatching {
        val json = JSONObject(readText(contentResolver, file) ?: return null)
        SidecarMetadata(
            title = json.optStringOrNull("title"),
            author = json.optStringOrNull("author") ?: json.optStringOrNull("authors"),
            narrator = json.optStringOrNull("narrator") ?: json.optStringOrNull("narrators"),
            description = json.optStringOrNull("description"),
            seriesName = json.optStringOrNull("series") ?: json.optStringOrNull("seriesName"),
            seriesIndex = (json.optStringOrNull("seriesIndex") ?: json.optStringOrNull("sequence"))
                ?.toFloatOrNull(),
        )
    }.getOrNull()

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key).takeIf { it.isNotBlank() } else null

    // Minimal best-effort OPF/package.xml reader: <dc:title>, <dc:creator opf:role="aut"/"nrt">,
    // <dc:description>, and Calibre's <meta name="calibre:series(_index)">.
    private fun readOpf(contentResolver: ContentResolver, file: DocumentFile): SidecarMetadata? = runCatching {
        contentResolver.openInputStream(file.uri)?.use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, null)
            var title: String? = null
            var author: String? = null
            var narrator: String? = null
            var description: String? = null
            var seriesName: String? = null
            var seriesIndex: Float? = null
            var currentTag: String? = null
            var currentRole: String? = null

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        when (currentTag?.substringAfterLast(':')?.lowercase()) {
                            "creator" -> currentRole = parser.attributeValue("role")
                            "meta" -> {
                                val name = parser.attributeValue("name")
                                val content = parser.attributeValue("content")
                                when (name) {
                                    "calibre:series" -> seriesName = content
                                    "calibre:series_index" -> seriesIndex = content?.toFloatOrNull()
                                }
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim()
                        if (!text.isNullOrBlank()) {
                            when (currentTag?.substringAfterLast(':')?.lowercase()) {
                                "title" -> if (title == null) title = text
                                "creator" -> when (currentRole?.lowercase()) {
                                    "nrt" -> narrator = text
                                    else -> if (author == null) author = text
                                }
                                "description" -> if (description == null) description = text
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> currentTag = null
                }
                event = parser.next()
            }
            SidecarMetadata(title, author, narrator, description, seriesName, seriesIndex)
        }
    }.getOrNull()

    private fun XmlPullParser.attributeValue(localName: String): String? =
        (0 until attributeCount).firstNotNullOfOrNull { i ->
            getAttributeName(i).substringAfterLast(':').takeIf { it.equals(localName, ignoreCase = true) }
                ?.let { getAttributeValue(i) }
        }
}

