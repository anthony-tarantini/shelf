@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.book

import arrow.core.raise.either
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.tarantini.shelf.RaiseContext
import io.tarantini.shelf.catalog.book.domain.*
import io.tarantini.shelf.organization.settings.SettingsService
import io.tarantini.shelf.organization.settings.UserSettingsRoot
import io.tarantini.shelf.processing.storage.StorageService
import io.tarantini.shelf.user.identity.domain.UserId
import kotlin.uuid.Uuid

class BookMetadataUpdateServiceTest :
    StringSpec({
        "updateBookMetadata orchestrates load settings decide persist and event when sync enabled" {
            val repository = mockk<BookRepository>()
            val decider = mockk<BookMetadataDecider>()
            val storageService = mockk<StorageService>(relaxed = true)
            val settingsService = mockk<SettingsService>()
            val eventHandler = mockk<BookDomainEventHandler>()

            val service =
                BookMetadataUpdateService(
                    repository = repository,
                    decider = decider,
                    storageService = storageService,
                    settingsService = settingsService,
                    eventHandler = eventHandler,
                )

            val bookId = BookId.fromRaw(Uuid.random())
            val userId = UserId.fromRaw(java.util.UUID.randomUUID())
            val snapshot = BookMetadataSnapshot(BookRoot.fromRaw(bookId, "Existing Title", null))
            val command = UpdateBookMetadataCommand(title = BookTitle.fromRaw("New Title"))
            val mutation =
                BookMetadataMutation(
                    title = "New Title",
                    bookRecord = BookRecordMutation(title = "New Title", coverPath = null),
                    description = null,
                    publisher = null,
                    publishYear = null,
                    genres = emptyList(),
                    moods = emptyList(),
                    ebookMetadata = null,
                    audiobookMetadata = null,
                    relationships = BookRelationshipsMutation.KeepExisting,
                )
            val decision =
                BookMetadataDecision(
                    mutation = mutation,
                    events = listOf(BookDomainEvent.MetadataSyncRequested(bookId)),
                )

            val calls = mutableListOf<String>()
            coEvery {
                with(any<RaiseContext>()) { repository.loadMetadataSnapshot(bookId) }
            } coAnswers
                {
                    calls += "load"
                    snapshot
                }
            every { decider.decide(snapshot, command, null, syncMetadataToFiles = true) } answers
                {
                    calls += "decide"
                    decision
                }
            coEvery {
                with(any<RaiseContext>()) { repository.applyMetadataMutation(bookId, mutation) }
            } coAnswers
                {
                    calls += "persist"
                    Unit
                }
            coEvery { settingsService.getUserSettings(userId) } coAnswers
                {
                    calls += "settings"
                    UserSettingsRoot(userId, true)
                }
            coEvery { eventHandler.handle(BookDomainEvent.MetadataSyncRequested(bookId)) } coAnswers
                {
                    calls += "event"
                    Unit
                }

            val result = either {
                service.updateBookMetadata(userId = userId, id = bookId, command = command)
            }

            result.fold({ fail("Should not have failed: $it") }, {})
            calls shouldBe listOf("load", "settings", "decide", "persist", "event")
            coVerify(exactly = 1) {
                eventHandler.handle(BookDomainEvent.MetadataSyncRequested(bookId))
            }
        }

        "updateBookMetadata skips domain event when user setting disables sync" {
            val repository = mockk<BookRepository>()
            val decider = mockk<BookMetadataDecider>()
            val storageService = mockk<StorageService>(relaxed = true)
            val settingsService = mockk<SettingsService>()
            val eventHandler = mockk<BookDomainEventHandler>(relaxed = true)

            val service =
                BookMetadataUpdateService(
                    repository = repository,
                    decider = decider,
                    storageService = storageService,
                    settingsService = settingsService,
                    eventHandler = eventHandler,
                )

            val bookId = BookId.fromRaw(Uuid.random())
            val userId = UserId.fromRaw(java.util.UUID.randomUUID())
            val snapshot = BookMetadataSnapshot(BookRoot.fromRaw(bookId, "Existing Title", null))
            val command = UpdateBookMetadataCommand(description = "Updated")
            val mutation =
                BookMetadataMutation(
                    title = "Existing Title",
                    bookRecord = null,
                    description = "Updated",
                    publisher = null,
                    publishYear = null,
                    genres = emptyList(),
                    moods = emptyList(),
                    ebookMetadata = null,
                    audiobookMetadata = null,
                    relationships = BookRelationshipsMutation.KeepExisting,
                )
            val decision = BookMetadataDecision(mutation = mutation, events = emptyList())

            coEvery {
                with(any<RaiseContext>()) { repository.loadMetadataSnapshot(bookId) }
            } returns snapshot
            every { decider.decide(snapshot, command, null, syncMetadataToFiles = false) } returns
                decision
            coEvery {
                with(any<RaiseContext>()) { repository.applyMetadataMutation(bookId, mutation) }
            } returns Unit
            coEvery { settingsService.getUserSettings(userId) } returns
                UserSettingsRoot(userId, false)

            val result = either {
                service.updateBookMetadata(userId = userId, id = bookId, command = command)
            }

            result.fold({ fail("Should not have failed: $it") }, {})
            coVerify(exactly = 0) { eventHandler.handle(any()) }
        }
    })
