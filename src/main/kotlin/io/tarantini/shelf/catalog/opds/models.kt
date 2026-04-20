@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.opds

import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.*

const val ATOM_NS = "http://www.w3.org/2005/Atom"
const val OPDS_NS = "http://opds-spec.org/2010/catalog"
const val DC_NS = "http://purl.org/dc/terms/"
const val OS_NS = "http://a9.com/-/spec/opensearch/1.1/"

@Serializable
@XmlSerialName("feed", ATOM_NS, "")
data class OpdsFeed(
    @XmlElement(true) val id: String,
    @XmlElement(true) val title: String,
    @XmlElement(true) val updated: String,
    @XmlElement(true) val author: OpdsAuthor? = null,
    @XmlElement(true) val link: List<OpdsLink> = emptyList(),
    @XmlElement(true) val entry: List<OpdsEntry> = emptyList(),
)

@Serializable
@XmlSerialName("entry", ATOM_NS, "")
data class OpdsEntry(
    @XmlElement(true) val id: String,
    @XmlElement(true) val title: String,
    @XmlElement(true) val updated: String,
    @XmlElement(true) val author: List<OpdsAuthor> = emptyList(),
    @XmlElement(true) val content: OpdsContent? = null,
    @XmlElement(true) val summary: String? = null,
    @XmlElement(true) val link: List<OpdsLink> = emptyList(),
    @XmlSerialName("identifier", DC_NS, "dc") @XmlElement(true) val identifier: String? = null,
    @XmlSerialName("language", DC_NS, "dc") @XmlElement(true) val language: String? = null,
    @XmlSerialName("publisher", DC_NS, "dc") @XmlElement(true) val publisher: String? = null,
    @XmlSerialName("issued", DC_NS, "dc") @XmlElement(true) val issued: String? = null,
)

@Serializable
@XmlSerialName("author", ATOM_NS, "")
data class OpdsAuthor(@XmlElement(true) val name: String, @XmlElement(true) val uri: String? = null)

@Serializable
@XmlSerialName("link", ATOM_NS, "")
data class OpdsLink(
    val href: String,
    val rel: String? = null,
    val type: String? = null,
    val title: String? = null,
)

@Serializable
@XmlSerialName("content", ATOM_NS, "")
data class OpdsContent(
    val type: String? = null,
    val src: String? = null,
    @XmlValue(true) val value: String? = null,
)

@Serializable
@XmlSerialName("OpenSearchDescription", OS_NS, "")
data class OpenSearchDescription(
    @XmlElement(true) val ShortName: String,
    @XmlElement(true) val Description: String,
    @XmlElement(true) val InputEncoding: String = "UTF-8",
    @XmlElement(true) val OutputEncoding: String = "UTF-8",
    @XmlElement(true) val Url: List<OpenSearchUrl> = emptyList(),
)

@Serializable
@XmlSerialName("Url", OS_NS, "")
data class OpenSearchUrl(val type: String, val template: String)

object OpdsRel {
    const val SELF = "self"
    const val START = "start"
    const val UP = "up"
    const val NEXT = "next"
    const val PREVIOUS = "previous"
    const val SEARCH = "search"
    const val SUBSECTION = "subsection"
    const val ACQUISITION = "http://opds-spec.org/acquisition"
    const val IMAGE = "http://opds-spec.org/image"
    const val THUMBNAIL = "http://opds-spec.org/image/thumbnail"
    const val NAVIGATION = "http://opds-spec.org/navigation"
}

object OpdsMimeType {
    const val ATOM = "application/atom+xml;profile=opds-catalog;kind=navigation"
    const val ATOM_ACQUISITION = "application/atom+xml;profile=opds-catalog;kind=acquisition"
    const val OPENSEARCH = "application/opensearchdescription+xml"
    const val EPUB = "application/epub+zip"
    const val PDF = "application/pdf"
    const val MOBI = "application/x-mobipocket-ebook"
    const val AZW3 = "application/vnd.amazon.mobi8-ebook"
    const val CBZ = "application/x-cbz"
    const val PNG = "image/png"
    const val JPEG = "image/jpeg"
    const val WEBP = "image/webp"
}
