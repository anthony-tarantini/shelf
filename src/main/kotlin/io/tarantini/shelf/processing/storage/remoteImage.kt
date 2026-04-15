package io.tarantini.shelf.processing.storage

import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import javax.imageio.ImageIO

data object InvalidImageUrl : StorageError

data object InvalidImage : StorageError

data object ImageTooLarge : StorageError

private const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
private val allowedContentTypes = setOf("image/jpeg", "image/png", "image/webp")
private val allowedExtensions = setOf("jpg", "jpeg", "png", "webp")

fun inferImageExtension(contentType: String?, fallbackPath: String? = null): String? =
    when {
        contentType == null ->
            fallbackPath?.substringAfterLast('.', "")?.substringBefore('?')?.lowercase()
        contentType.equals("image/jpeg", ignoreCase = true) -> "jpg"
        contentType.equals("image/png", ignoreCase = true) -> "png"
        contentType.equals("image/webp", ignoreCase = true) -> "webp"
        else -> null
    }

context(_: RaiseContext)
fun validateImage(bytes: ByteArray, extension: String): ByteArray {
    ensure(extension in allowedExtensions) { InvalidImage }
    ensure(bytes.isNotEmpty()) { InvalidImage }
    ensure(bytes.size <= MAX_IMAGE_BYTES) { ImageTooLarge }
    if (extension != "webp") {
        ensureNotNull(ImageIO.read(ByteArrayInputStream(bytes))) { InvalidImage }
    }
    return bytes
}

context(_: RaiseContext)
fun readBoundedImageBytes(input: java.io.InputStream): ByteArray =
    input.use { stream ->
        val data = stream.readNBytes(MAX_IMAGE_BYTES + 1)
        ensure(data.size <= MAX_IMAGE_BYTES) { ImageTooLarge }
        data
    }

/**
 * Downloads and validates a remote image from the given URL. Only HTTPS URLs with hosts matching
 * [allowedHosts] are accepted.
 *
 * @return a pair of (validated image bytes, file extension like "jpg")
 */
context(_: RaiseContext)
fun fetchRemoteImage(url: String, allowedHosts: List<String>): Pair<ByteArray, String> {
    val uri = URI.create(url)
    val host = uri.host?.lowercase()
    ensure(uri.scheme.equals("https", ignoreCase = true)) { InvalidImageUrl }
    ensure(!host.isNullOrBlank()) { InvalidImageUrl }
    ensure(allowedHosts.any { host == it || host.endsWith(".$it") }) { InvalidImageUrl }

    val connection =
        (uri.toURL().openConnection() as? HttpURLConnection)?.apply {
            connectTimeout = 5_000
            readTimeout = 10_000
            instanceFollowRedirects = false
        } ?: raise(InvalidImageUrl)

    ensure(connection.responseCode in 200..299) { InvalidImageUrl }

    val contentType = connection.contentType?.substringBefore(';')?.lowercase()
    ensure(contentType in allowedContentTypes) { InvalidImageUrl }
    val contentLength = connection.contentLengthLong
    ensure(contentLength < 0 || contentLength <= MAX_IMAGE_BYTES) { ImageTooLarge }
    val bytes = readBoundedImageBytes(connection.inputStream)

    val extension = inferImageExtension(contentType, uri.path) ?: raise(InvalidImageUrl)
    return validateImage(bytes, extension) to extension
}
