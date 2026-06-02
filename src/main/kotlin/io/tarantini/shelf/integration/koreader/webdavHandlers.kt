package io.tarantini.shelf.integration.koreader

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveStream
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

internal suspend fun RoutingContext.respondWebdavPropfind(statsFile: java.nio.file.Path) {
    val depth = call.request.headers["Depth"] ?: "1"
    val requestUri = call.request.uri
    val dirHref = if (requestUri.endsWith("/")) requestUri else "$requestUri/"
    val fileHref = "${dirHref}statistics.sqlite"
    val fileExists = statsFile.exists()
    val fileLength = if (fileExists) statsFile.toFile().length() else 0
    val fileLastModified =
        if (fileExists) java.time.Instant.ofEpochMilli(statsFile.toFile().lastModified()).toString()
        else java.time.Instant.EPOCH.toString()

    logger.info("KOReader webdav PROPFIND depth={} uri={}", depth, requestUri)

    val dirResponse =
        """
        <D:response>
            <D:href>$dirHref</D:href>
            <D:propstat>
                <D:prop>
                    <D:displayname>koreader</D:displayname>
                    <D:resourcetype><D:collection/></D:resourcetype>
                    <D:getlastmodified>$fileLastModified</D:getlastmodified>
                </D:prop>
                <D:status>HTTP/1.1 200 OK</D:status>
            </D:propstat>
        </D:response>
        """
            .trimIndent()

    val fileResponse =
        if (fileExists) {
            """
            <D:response>
                <D:href>$fileHref</D:href>
                <D:propstat>
                    <D:prop>
                        <D:displayname>statistics.sqlite</D:displayname>
                        <D:resourcetype/>
                        <D:getcontentlength>$fileLength</D:getcontentlength>
                        <D:getcontenttype>application/x-sqlite3</D:getcontenttype>
                        <D:getlastmodified>$fileLastModified</D:getlastmodified>
                    </D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
            </D:response>
            """
                .trimIndent()
        } else {
            ""
        }

    val body = if (depth == "0") dirResponse else "$dirResponse\n$fileResponse"

    val xml =
        """<?xml version="1.0" encoding="utf-8" ?>
<D:multistatus xmlns:D="DAV:">
$body
</D:multistatus>
"""

    call.respondText(xml, ContentType.parse("application/xml"), HttpStatusCode.MultiStatus)
    logger.info("KOReader webdav PROPFIND responded 207")
}

internal suspend fun RoutingContext.respondWebdavGet(statsFile: java.nio.file.Path) {
    if (statsFile.exists()) {
        logger.info("KOReader webdav GET serving statistics.sqlite")
        call.respondOutputStream(ContentType.parse("application/x-sqlite3")) {
            statsFile.inputStream().use { it.copyTo(this) }
        }
        logger.info("KOReader webdav GET responded 200")
    } else {
        logger.info("KOReader webdav GET missing statistics.sqlite")
        call.respond(HttpStatusCode.NotFound)
    }
}

internal suspend fun RoutingContext.respondWebdavPut(statsFile: java.nio.file.Path) {
    val bytesWritten =
        call.receiveStream().use { input ->
            statsFile.outputStream().use { output -> input.copyTo(output) }
        }
    val finalSize = if (statsFile.exists()) statsFile.toFile().length() else -1
    logger.info(
        "KOReader webdav PUT stored statistics.sqlite bytesWritten={} fileSizeOnDisk={}",
        bytesWritten,
        finalSize,
    )
    call.respond(HttpStatusCode.Created)
}

internal suspend fun RoutingContext.respondWebdavOptions() {
    call.response.header("Allow", "GET, PUT, PROPFIND, OPTIONS")
    call.response.header("DAV", "1")
    call.respond(HttpStatusCode.OK)
    logger.info("KOReader webdav OPTIONS responded 200")
}
