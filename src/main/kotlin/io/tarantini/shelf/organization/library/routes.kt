@file:OptIn(ExperimentalSerializationApi::class)

package io.tarantini.shelf.organization.library

import arrow.core.raise.context.ensure
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.resources.delete
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.routing.Route
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.respond
import io.tarantini.shelf.catalog.book.BookService
import io.tarantini.shelf.organization.library.domain.EmptyLibraryTitle
import io.tarantini.shelf.organization.library.domain.LibraryId
import io.tarantini.shelf.organization.library.domain.LibraryRequest
import io.tarantini.shelf.user.auth.JwtService
import io.tarantini.shelf.user.auth.jwtAuth
import kotlinx.serialization.ExperimentalSerializationApi

fun Route.libraryRoutes(
    jwtService: JwtService,
    libraryService: LibraryService,
    bookService: BookService,
) {

    get<LibraryResource> {
        jwtAuth(jwtService) { context ->
            with(context) { respond({ libraryService.getLibrariesForUser() }) }
        }
    }

    put<LibraryResource.Id> { resource ->
        jwtAuth(jwtService) { context ->
            respond({
                val request = call.receive<Request<LibraryRequest>>().data
                val libraryId = LibraryId(resource.id)
                ensure(!request.title.isNullOrEmpty()) { EmptyLibraryTitle }
                with(context) { libraryService.updateLibrary(libraryId, request.title) }
            })
        }
    }

    post<LibraryResource> {
        jwtAuth(jwtService) { context ->
            with(context) {
                respond(
                    {
                        val request = call.receive<Request<LibraryRequest>>().data
                        ensure(!request.title.isNullOrEmpty()) { EmptyLibraryTitle }
                        libraryService.createLibrary(request.title)
                    },
                    HttpStatusCode.Created,
                )
            }
        }
    }

    get<LibraryResource.Id.Books> { resource ->
        jwtAuth(jwtService) { context ->
            respond({
                val id = LibraryId(resource.id)
                with(context) {
                    libraryService.getLibraryById(id)
                    bookService.getBooksForLibraries(listOf(id)).getOrDefault(id, emptyList())
                }
            })
        }
    }

    get<LibraryResource.Id> { resource ->
        jwtAuth(jwtService) { context ->
            respond({
                val id = LibraryId(resource.id)
                with(context) { libraryService.getLibraryById(id) }
            })
        }
    }

    get<LibraryResource.Id.Details> { resource ->
        jwtAuth(jwtService) { context ->
            respond({
                val id = LibraryId(resource.id)
                with(context) { libraryService.getLibraryAggregate(id) }
            })
        }
    }

    delete<LibraryResource.Id> { resource ->
        jwtAuth(jwtService) { context ->
            respond(
                {
                    val id = LibraryId(resource.id)
                    with(context) { libraryService.deleteLibrary(id) }
                },
                HttpStatusCode.NoContent,
            )
        }
    }
}
