package io.tarantini.shelf.processing.epub

import arrow.core.raise.catch
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import io.tarantini.shelf.RaiseContext
import java.io.IOException
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXException

data class EpubMetadataUpdates(
    val title: String? = null,
    val authors: List<String>? = null,
    val description: String? = null,
    val publisher: String? = null,
    val publishYear: Int? = null,
)

interface EpubWriter {
    context(_: RaiseContext)
    suspend fun updateMetadata(path: Path, updates: EpubMetadataUpdates)
}

fun epubWriter(): EpubWriter =
    object : EpubWriter {
        context(_: RaiseContext)
        override suspend fun updateMetadata(path: Path, updates: EpubMetadataUpdates) {
            withContext(Dispatchers.IO) {
                val uri = URI.create("jar:${path.toUri()}")
                val env = mapOf("create" to "false")

                catch({
                    FileSystems.newFileSystem(uri, env).use { fs ->
                        val containerPath = fs.getPath("/META-INF/container.xml")
                        ensure(Files.exists(containerPath)) { MissingContainerXML }
                        
                        val containerXml = Files.readAllBytes(containerPath)
                        val opfPathStr = getOpfPath(containerXml)
                        val opfPath = fs.getPath(opfPathStr)
                        ensure(Files.exists(opfPath)) { MissingOPF }

                        val opfBytes = Files.readAllBytes(opfPath)
                        val doc = parseXml(opfBytes)

                        applyUpdates(doc, updates)

                        // Write back the modified OPF
                        Files.newOutputStream(opfPath).use { os ->
                            val transformer = TransformerFactory.newInstance().newTransformer()
                            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
                            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
                            transformer.transform(DOMSource(doc), StreamResult(os))
                        }
                    }
                }) { e: Exception ->
                    when (e) {
                        is SAXException,
                        is IOException -> raise(InvalidOPF)
                        else -> throw e
                    }
                }
            }
        }
    }

private fun applyUpdates(doc: Document, updates: EpubMetadataUpdates) {
    val metadataNodes = doc.getElementsByTagName("metadata")
    if (metadataNodes.length == 0) return
    val metadata = metadataNodes.item(0) as Element

    val dcNamespace = epubNamespaceContext.getNamespaceURI("dc")

    updates.title?.let { updateOrCreateNode(doc, metadata, dcNamespace, "dc:title", it) }
    
    updates.authors?.let { authorList ->
        // For authors, we typically want to replace existing dc:creator tags
        removeNodes(metadata, "dc:creator")
        authorList.forEach { author ->
            val creator = doc.createElementNS(dcNamespace, "dc:creator")
            creator.textContent = author
            metadata.appendChild(creator)
        }
    }

    updates.description?.let { updateOrCreateNode(doc, metadata, dcNamespace, "dc:description", it) }
    updates.publisher?.let { updateOrCreateNode(doc, metadata, dcNamespace, "dc:publisher", it) }
    updates.publishYear?.let { updateOrCreateNode(doc, metadata, dcNamespace, "dc:date", it.toString()) }
}

private fun updateOrCreateNode(
    doc: Document,
    parent: Element,
    namespace: String?,
    tagName: String,
    value: String
) {
    val nodes = doc.getElementsByTagName(tagName)
    if (nodes.length > 0) {
        nodes.item(0).textContent = value
    } else {
        val newNode = doc.createElementNS(namespace, tagName)
        newNode.textContent = value
        parent.appendChild(newNode)
    }
}

private fun removeNodes(parent: Element, tagName: String) {
    val nodes = parent.getElementsByTagName(tagName)
    val toRemove = mutableListOf<org.w3c.dom.Node>()
    for (i in 0 until nodes.length) {
        toRemove.add(nodes.item(i))
    }
    toRemove.forEach { parent.removeChild(it) }
}
