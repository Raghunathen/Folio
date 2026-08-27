package com.raghu.folio.logic.utils.audiobook

/** Book metadata gathered from files inside the book folder besides the audio itself. */
data class SidecarMetadata(
    val title: String? = null,
    val author: String? = null,
    val narrator: String? = null,
    val description: String? = null,
    val seriesName: String? = null,
    val seriesIndex: Float? = null,
    val coverUri: String? = null,
)
