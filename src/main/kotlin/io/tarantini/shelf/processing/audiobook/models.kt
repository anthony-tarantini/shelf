package io.tarantini.shelf.processing.audiobook

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FfprobeOutput(
    val streams: List<FfprobeStream> = emptyList(),
    val format: FfprobeFormat? = null,
    val chapters: List<FfprobeChapter> = emptyList(),
)

@Serializable
data class FfprobeChapter(
    val id: Long,
    @SerialName("time_base") val timeBase: String,
    @SerialName("start_time") val startTime: Double,
    @SerialName("end_time") val endTime: Double,
    val tags: Map<String, String>? = null,
) {
    val title = tags?.get("title")?.takeIf { it.isNotBlank() } ?: "Chapter $id"
}

@Serializable
data class FfprobeFormat(val tags: Map<String, String>? = null, val duration: String? = null)

@Serializable
data class FfprobeStream(
    val disposition: Map<String, Int>? = null,
    @SerialName("codec_type") val codecType: String? = null,
)
