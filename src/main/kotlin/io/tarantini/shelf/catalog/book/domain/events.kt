package io.tarantini.shelf.catalog.book.domain

sealed interface BookDomainEvent {
    data class MetadataSyncRequested(val bookId: BookId) : BookDomainEvent
}

interface BookDomainEventHandler {
    suspend fun handle(event: BookDomainEvent)
}
