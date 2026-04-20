package io.tarantini.shelf.integration.hardcover

import arrow.core.raise.recover
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.network.NetworkTransport
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.catalog.metadata.domain.ExternalContributor
import io.tarantini.shelf.providers.hardcover.FetchMetadataByBookIdsQuery
import io.tarantini.shelf.providers.hardcover.SearchBooksQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class HardcoverClientTest :
    StringSpec({
        "searchBookMetadataByName should map results correctly" {
            val searchData =
                SearchBooksQuery.Data(search = SearchBooksQuery.Search(ids = listOf(123)))

            val bookData =
                FetchMetadataByBookIdsQuery.Data(
                    books =
                        listOf(
                            FetchMetadataByBookIdsQuery.Book(
                                id = 123,
                                title = "Foundation",
                                release_year = 1951,
                                ratings_count = 1000,
                                rating = 4.5,
                                description = "A great book",
                                book_series =
                                    listOf(
                                        FetchMetadataByBookIdsQuery.Book_series(
                                            position = 1.5,
                                            series =
                                                FetchMetadataByBookIdsQuery.Series(
                                                    name = "Foundation Universe",
                                                    id = 7,
                                                    description = "Classic sci-fi saga",
                                                ),
                                        )
                                    ),
                                genres = emptyList(),
                                contributions =
                                    listOf(
                                        FetchMetadataByBookIdsQuery.Contribution(
                                            author =
                                                FetchMetadataByBookIdsQuery.Author(
                                                    id = 1,
                                                    name = "Isaac Asimov",
                                                    image = null,
                                                ),
                                            contribution = "author",
                                        )
                                    ),
                                image = null,
                                default_ebook_edition = null,
                                default_audio_edition = null,
                            )
                        )
                )

            val transport =
                object : NetworkTransport {
                    @Suppress("UNCHECKED_CAST")
                    override fun <D : Operation.Data> execute(
                        request: com.apollographql.apollo.api.ApolloRequest<D>
                    ): Flow<ApolloResponse<D>> {
                        val data =
                            when (request.operation) {
                                is SearchBooksQuery -> searchData
                                is FetchMetadataByBookIdsQuery -> bookData
                                else -> null
                            }
                                as D?
                        return flowOf(
                            ApolloResponse.Builder(request.operation, request.requestUuid)
                                .data(data)
                                .build()
                        )
                    }

                    override fun dispose() {}
                }

            val apolloClient = ApolloClient.Builder().networkTransport(transport).build()
            val client = hardcover(apolloClient)

            recover({
                val results = client.searchBookMetadataByName("Foundation")
                results.size shouldBe 1
                results[0].title shouldBe "Foundation"
                results[0].releaseYear shouldBe 1951
                (results[0].contributors[0] as ExternalContributor.ExternalAuthor).name shouldBe
                    "Isaac Asimov"
                results[0].seriesName.first().position shouldBe 1.5
            }) {
                fail("Should not have failed: $it")
            }
        }
    })
