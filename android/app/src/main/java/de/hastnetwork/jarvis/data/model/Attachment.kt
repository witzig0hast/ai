package de.hastnetwork.jarvis.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors the `Attachment` entity from architecture.md §4. */
@Serializable
data class Attachment(
    val id: String,
    val filename: String,
    @SerialName("content_type") val contentType: String,
    @SerialName("size_bytes") val sizeBytes: Long,
)

/** Response body of `POST /api/files` (architecture.md §5, "Anhänge"). */
@Serializable
data class FileUploadResponse(
    @SerialName("file_id") val fileId: String,
    val filename: String,
    @SerialName("content_type") val contentType: String,
)
