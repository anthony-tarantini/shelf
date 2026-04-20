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
    val depth = call.request.headers["Depth"] ?: "<missing>"
    logger.info("KOReader webdav PROPFIND depth={}", depth)
    val xml =
        """
        <?xml version="1.0" encoding="utf-8" ?>
        <D:multistatus xmlns:D="DAV:">
            <D:response>
                <D:href>${call.request.uri}</D:href>
                <D:propstat>
                    <D:prop>
                        <D:displayname>statistics.sqlite</D:displayname>
                        <D:getcontentlength>${if (statsFile.exists()) statsFile.toFile().length() else 0}</D:getcontentlength>
                        <D:resourcetype/>
                    </D:prop>
                    <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
            </D:response>
        </D:multistatus>
    """
            .trimIndent()
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
    call.receiveStream().use { input ->
        statsFile.outputStream().use { output -> input.copyTo(output) }
    }
    logger.info("KOReader webdav PUT stored statistics.sqlite")
    call.respond(HttpStatusCode.Created)
}

internal suspend fun RoutingContext.respondWebdavOptions() {
    call.response.header("Allow", "GET, PUT, PROPFIND, OPTIONS")
    call.response.header("DAV", "1")
    call.respond(HttpStatusCode.OK)
    logger.info("KOReader webdav OPTIONS responded 200")
}
