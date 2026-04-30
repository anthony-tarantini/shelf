package io.tarantini.shelf.testing

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.absolutePathString
import kotlin.io.path.outputStream
import kotlin.io.path.writeText

object MediaFixtureFactory {
    data class EpubSpec(
        val title: String,
        val author: String,
        val seriesName: String? = null,
        val seriesIndex: Double? = null,
        val calibreSeriesName: String? = null,
        val calibreSeriesIndex: Double? = null,
        val language: String = "en",
        val description: String = "Fixture description",
        val includeCover: Boolean = true,
    )

    data class AudioSpec(
        val title: String,
        val artist: String,
        val album: String,
        val genre: String,
        val date: String,
        val comment: String,
        val durationSeconds: Int = 1,
    )

    fun createMinimalEpub(path: Path, spec: EpubSpec): Path {
        Files.createDirectories(path.parent)
        ZipOutputStream(path.outputStream().buffered()).use { zip ->
            val mimeBytes = "application/epub+zip".toByteArray()
            val mimeEntry =
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mimeBytes.size.toLong()
                    compressedSize = mimeBytes.size.toLong()
                    crc = crc32(mimeBytes)
                }
            zip.putNextEntry(mimeEntry)
            zip.write(mimeBytes)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """
                    .trimIndent()
                    .toByteArray()
            )
            zip.closeEntry()

            val seriesMeta =
                if (spec.seriesName != null) {
                    val indexMeta =
                        spec.seriesIndex?.let {
                            "<meta refines=\"#series-1\" property=\"group-position\">$it</meta>"
                        } ?: ""
                    """
                    <meta id="series-1" property="belongs-to-collection">${escapeXml(spec.seriesName)}</meta>
                    <meta refines="#series-1" property="collection-type">series</meta>
                    $indexMeta
                    """
                        .trimIndent()
                } else {
                    ""
                }
            val calibreSeriesMeta =
                spec.calibreSeriesName?.let {
                    """<meta name="calibre:series" content="${escapeXml(it)}"/>"""
                } ?: ""
            val calibreSeriesIndexMeta =
                spec.calibreSeriesIndex?.let {
                    """<meta name="calibre:series_index" content="$it"/>"""
                } ?: ""

            val coverMeta =
                if (spec.includeCover) "<meta name=\"cover\" content=\"cover-image\"/>" else ""
            val coverItem =
                if (spec.includeCover) {
                    "<item id=\"cover-image\" href=\"images/cover.png\" media-type=\"image/png\" properties=\"cover-image\"/>"
                } else {
                    ""
                }

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """
                <package version="3.0" unique-identifier="bookid" xmlns="http://www.idpf.org/2007/opf">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:identifier id="bookid">B012345678</dc:identifier>
                    <dc:title>${escapeXml(spec.title)}</dc:title>
                    <dc:creator>${escapeXml(spec.author)}</dc:creator>
                    <dc:language>${escapeXml(spec.language)}</dc:language>
                    <dc:description>${escapeXml(spec.description)}</dc:description>
                    $coverMeta
                    $seriesMeta
                    $calibreSeriesMeta
                    $calibreSeriesIndexMeta
                  </metadata>
                  <manifest>
                    <item id="nav" href="toc.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                    $coverItem
                  </manifest>
                  <spine>
                    <itemref idref="nav"/>
                  </spine>
                </package>
                """
                    .trimIndent()
                    .toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/toc.xhtml"))
            zip.write(
                "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><h1>${escapeXml(spec.title)}</h1></body></html>"
                    .toByteArray()
            )
            zip.closeEntry()

            if (spec.includeCover) {
                zip.putNextEntry(ZipEntry("OEBPS/images/cover.png"))
                zip.write(tinyPngBytes())
                zip.closeEntry()
            }
        }
        return path
    }

    fun createTaggedMp3(path: Path, spec: AudioSpec) {
        Files.createDirectories(path.parent)
        val process =
            ProcessBuilder(
                    "ffmpeg",
                    "-y",
                    "-f",
                    "lavfi",
                    "-i",
                    "anullsrc=r=44100:cl=mono",
                    "-t",
                    spec.durationSeconds.toString(),
                    "-metadata",
                    "title=${spec.title}",
                    "-metadata",
                    "artist=${spec.artist}",
                    "-metadata",
                    "album=${spec.album}",
                    "-metadata",
                    "genre=${spec.genre}",
                    "-metadata",
                    "date=${spec.date}",
                    "-metadata",
                    "comment=${spec.comment}",
                    path.absolutePathString(),
                )
                .redirectErrorStream(true)
                .start()
        require(process.waitFor() == 0) { "ffmpeg failed to generate tagged mp3 fixture" }
    }

    fun createLibationManifest(path: Path, json: String) {
        Files.createDirectories(path.parent)
        path.writeText(json.trimIndent())
    }

    fun createBinaryFile(path: Path, contents: ByteArray) {
        Files.createDirectories(path.parent)
        Files.write(path, contents)
    }

    private fun escapeXml(input: String): String =
        input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun crc32(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value

    private fun tinyPngBytes(): ByteArray =
        byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x00,
            0x00,
            0x00,
            0x0D,
            0x49,
            0x48,
            0x44,
            0x52,
            0x00,
            0x00,
            0x00,
            0x01,
            0x00,
            0x00,
            0x00,
            0x01,
            0x08,
            0x02,
            0x00,
            0x00,
            0x00,
            0x90.toByte(),
            0x77,
            0x53,
            0xDE.toByte(),
            0x00,
            0x00,
            0x00,
            0x0C,
            0x49,
            0x44,
            0x41,
            0x54,
            0x08,
            0xD7.toByte(),
            0x63,
            0xF8.toByte(),
            0xCF.toByte(),
            0xC0.toByte(),
            0x00,
            0x00,
            0x03,
            0x01,
            0x01,
            0x00,
            0x18,
            0xDD.toByte(),
            0x8D.toByte(),
            0xB1.toByte(),
            0x00,
            0x00,
            0x00,
            0x00,
            0x49,
            0x45,
            0x4E,
            0x44,
            0xAE.toByte(),
            0x42,
            0x60,
            0x82.toByte(),
        )
}
