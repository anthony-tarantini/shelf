@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.epub

import arrow.core.raise.catch
import arrow.core.raise.context.ensure
import arrow.core.raise.context.ensureNotNull
import arrow.core.raise.context.raise
import arrow.fx.coroutines.ResourceScope
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.app.Identity
import io.tarantini.shelf.catalog.book.domain.BookId
import io.tarantini.shelf.catalog.metadata.domain.ASIN
import io.tarantini.shelf.catalog.metadata.domain.BookFormat
import io.tarantini.shelf.catalog.metadata.domain.BookMetadata
import io.tarantini.shelf.catalog.metadata.domain.ISBN10
import io.tarantini.shelf.catalog.metadata.domain.ISBN13
import io.tarantini.shelf.catalog.metadata.domain.NewEdition
import io.tarantini.shelf.catalog.metadata.domain.NewMetadataRoot
import io.tarantini.shelf.catalog.metadata.domain.ParsedSeries
import io.tarantini.shelf.processing.storage.StoragePath
import io.tarantini.shelf.toYearOrNull
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import javax.xml.namespace.NamespaceContext
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpressionException
import javax.xml.xpath.XPathFactory
import kotlin.io.path.deleteIfExists
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import org.xml.sax.SAXException

val epubNamespaceContext =
    object : NamespaceContext {
        private val namespaces =
            mapOf(
                "container" to "urn:oasis:names:tc:opendocument:xmlns:container",
                "opf" to "http://www.idpf.org/2007/opf",
                "dc" to "http://purl.org/dc/elements/1.1/",
            )

        override fun getNamespaceURI(prefix: String?): String? = namespaces[prefix]

        override fun getPrefix(namespaceURI: String?): String? =
            namespaces.entries.find { it.value == namespaceURI }?.key

        override fun getPrefixes(namespaceURI: String?): Iterator<String> =
            namespaces.entries.filter { it.value == namespaceURI }.map { it.key }.iterator()
    }

interface EpubParser {
    context(_: RaiseContext)
    suspend fun parse(scope: ResourceScope, path: Path, bookId: BookId): Pair<BookMetadata, Path?>
}

fun epubParser() =
    object : EpubParser {
        context(_: RaiseContext)
        override suspend fun parse(
            scope: ResourceScope,
            path: Path,
            bookId: BookId,
        ): Pair<BookMetadata, Path?> {
            return withContext(Dispatchers.IO) {
                val zipFile =
                    catch({
                        scope.install({ ZipFile(path.toFile()) }) { zip, _ ->
                            catch({ zip.close() }) {}
                        }
                    }) { _: IOException ->
                        raise(InvalidOPF)
                    }

                val containerEntry =
                    ensureNotNull(zipFile.getEntry("META-INF/container.xml")) {
                        MissingContainerXML
                    }
                val containerXml = zipFile.getInputStream(containerEntry).use { it.readAllBytes() }

                val opfPathStr = getOpfPath(containerXml)
                val opfEntry = ensureNotNull(zipFile.getEntry(opfPathStr)) { MissingOPF }
                val opfContent = zipFile.getInputStream(opfEntry).use { it.readAllBytes() }

                val doc =
                    catch({ parseXml(opfContent) }) { e: Exception ->
                        when (e) {
                            is SAXException,
                            is IOException -> raise(InvalidOPF)

                            else -> throw e
                        }
                    }

                catch({
                    val metadata = extractMetadata(doc, opfPathStr, bookId, path)
                    val tempCover = extractCover(scope, zipFile, doc, opfPathStr)

                    metadata to tempCover
                }) { e: Exception ->
                    if (e is XPathExpressionException) raise(InvalidOPF)
                    throw e
                }
            }
        }
    }

context(_: RaiseContext)
private fun extractMetadata(
    doc: Document,
    opfPathStr: String,
    bookId: BookId,
    path: Path,
): BookMetadata {
    val xpath =
        XPathFactory.newInstance().newXPath().apply { namespaceContext = epubNamespaceContext }

    val titleRaw =
        xpath.evaluate("//dc:title", doc).takeIf { it.isNotBlank() }
            ?: opfPathStr.substringBeforeLast('/')

    // Authors search
    val authorsNodeList = xpath.evaluate("//dc:creator", doc, XPathConstants.NODESET) as NodeList
    val authors = mutableListOf<String>()
    for (i in 0 until authorsNodeList.length) {
        val content = authorsNodeList.item(i).textContent
        if (content.isNotBlank()) authors.add(content.trim())
    }

    val identifierRaw = xpath.evaluate("//dc:identifier", doc).takeIf { it.isNotBlank() }
    val languageRaw = xpath.evaluate("//dc:language", doc).takeIf { it.isNotBlank() }

    val descriptionRaw = xpath.evaluate("//dc:description", doc).takeIf { it.isNotBlank() }
    val publisherRaw = xpath.evaluate("//dc:publisher", doc).takeIf { it.isNotBlank() }
    val dateRaw = xpath.evaluate("//dc:date", doc).takeIf { it.isNotBlank() }

    // ISBN search
    val isbnRaw =
        (xpath.evaluate("//dc:identifier[@opf:scheme='ISBN']", doc).takeIf { it.isNotBlank() }
            ?: xpath
                .evaluate("//dc:identifier[contains(translate(., 'ISBN', 'isbn'), 'isbn')]", doc)
                .takeIf { it.isNotBlank() })

    // Subjects / Genres
    val subjectsNodeList = xpath.evaluate("//dc:subject", doc, XPathConstants.NODESET) as NodeList
    val genres = mutableListOf<String>()
    for (i in 0 until subjectsNodeList.length) {
        val content = subjectsNodeList.item(i).textContent
        if (content.isNotBlank()) genres.add(content.trim())
    }

    val core =
        NewMetadataRoot(
            id = Identity.Unsaved,
            bookId = bookId,
            title = titleRaw,
            description = descriptionRaw,
            publisher = publisherRaw,
            published = dateRaw?.toYearOrNull(),
            language = languageRaw,
            genres = genres,
            moods = emptyList(),
        )

    val edition =
        NewEdition(
            id = Identity.Unsaved,
            bookId = bookId,
            format = BookFormat.EBOOK,
            path =
                StoragePath.fromRaw("imports/ebook")
                    .resolve(StoragePath.safeSegment(path.fileName?.toString())),
            isbn10 = if (isbnRaw?.length == 10) ISBN10(isbnRaw) else null,
            isbn13 = if (isbnRaw?.length == 13) ISBN13(isbnRaw) else null,
            asin = identifierRaw?.let { if (it.length == 10) ASIN(it) else null },
            size = Files.size(path),
        )

    return BookMetadata(
        core = core,
        edition = edition,
        authors = authors,
        series = extractSeries(xpath, doc),
    )
}

private fun extractSeries(xpath: javax.xml.xpath.XPath, doc: Document): List<ParsedSeries> {
    val collectionNodes =
        xpath.evaluate("//opf:meta[@property='belongs-to-collection']", doc, XPathConstants.NODESET)
            as NodeList

    val collectionSeries = buildList {
        for (i in 0 until collectionNodes.length) {
            val node = collectionNodes.item(i)
            val name = node.textContent?.trim().orEmpty()
            if (name.isBlank()) continue

            val id = node.attributes?.getNamedItem("id")?.nodeValue
            val type =
                id?.let {
                    xpath
                        .evaluate(
                            "//opf:meta[@property='collection-type' and @refines='#$it']",
                            doc,
                        )
                        .trim()
                }

            if (type.isNullOrBlank() || type == "series") {
                val index =
                    id?.let {
                        xpath
                            .evaluate(
                                "//opf:meta[@property='group-position' and @refines='#$it']",
                                doc,
                            )
                            .trim()
                            .takeIf(String::isNotBlank)
                            ?.toDoubleOrNull()
                    }
                add(ParsedSeries(name = name, index = index))
            }
        }
    }

    if (collectionSeries.isNotEmpty()) {
        return collectionSeries
    }

    val calibreSeries =
        xpath.evaluate("//opf:meta[@name='calibre:series']/@content", doc).trim().takeIf {
            it.isNotBlank()
        }
    val calibreIndex =
        xpath
            .evaluate("//opf:meta[@name='calibre:series_index']/@content", doc)
            .trim()
            .takeIf { it.isNotBlank() }
            ?.toDoubleOrNull()

    return calibreSeries?.let { listOf(ParsedSeries(name = it, index = calibreIndex)) }
        ?: emptyList()
}

private suspend fun extractCover(
    scope: ResourceScope,
    zipFile: ZipFile,
    doc: Document,
    opfPathStr: String,
) =
    withContext(Dispatchers.IO) {
        val xpath =
            XPathFactory.newInstance().newXPath().apply { namespaceContext = epubNamespaceContext }

        // Cover extraction
        val coverId = xpath.evaluate("//opf:meta[@name='cover']/@content", doc)
        val coverPathRaw =
            if (coverId.isNotBlank()) {
                xpath.evaluate("//opf:item[@id='$coverId']/@href", doc)
            } else {
                xpath.evaluate("//opf:item[contains(@properties, 'cover-image')]/@href", doc)
            }

        var tempCover: Path? = null
        if (coverPathRaw.isNotBlank()) {
            val opfDir =
                if (opfPathStr.contains("/")) opfPathStr.substringBeforeLast("/") + "/" else ""
            val fullCoverPath = (opfDir + coverPathRaw).normalizePath()
            val entry = zipFile.getEntry(fullCoverPath)
            if (entry != null) {
                tempCover =
                    scope.install({ Files.createTempFile("shelf-cover-", ".jpg") }) { p, _ ->
                        p.deleteIfExists()
                    }
                zipFile.getInputStream(entry).use { input ->
                    Files.copy(input, tempCover, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        tempCover
    }

private fun String.normalizePath(): String {
    val parts = this.split("/")
    val stack = mutableListOf<String>()
    for (part in parts) {
        when (part) {
            "." -> {}
            ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
            else -> if (part.isNotEmpty()) stack.add(part)
        }
    }
    return stack.joinToString("/")
}

fun parseXml(content: ByteArray): Document {
    val factory = DocumentBuilderFactory.newInstance()
    factory.isNamespaceAware = true
    val builder = factory.newDocumentBuilder()
    return builder.parse(content.inputStream())
}

context(_: RaiseContext)
fun getOpfPath(containerXml: ByteArray): String {
    val doc =
        catch({ parseXml(containerXml) }) { e: Exception ->
            when (e) {
                is SAXException,
                is IOException -> raise(InvalidContainerXML)

                else -> throw e
            }
        }
    val xpath =
        XPathFactory.newInstance().newXPath().apply { namespaceContext = epubNamespaceContext }
    val path =
        catch({
            xpath.evaluate(
                "/container:container/container:rootfiles/container:rootfile/@full-path",
                doc,
            )
        }) { _: XPathExpressionException ->
            raise(InvalidContainerXML)
        }
    ensure(path.isNotBlank()) { MissingOPF }
    return path
}
