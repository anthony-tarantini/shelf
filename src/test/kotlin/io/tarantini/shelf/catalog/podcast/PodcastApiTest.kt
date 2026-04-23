@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.catalog.podcast

import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.Request
import io.tarantini.shelf.app.Response
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.podcast.domain.PodcastDashboard
import io.tarantini.shelf.catalog.podcast.domain.PodcastRequest
import io.tarantini.shelf.catalog.podcast.domain.SavedPodcastRoot
import io.tarantini.shelf.catalog.series.domain.SavedSeriesRoot
import io.tarantini.shelf.catalog.series.domain.SeriesRequest
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PodcastApiTest :
    IntegrationSpec({
        "podcast routes should require authentication" {
            testApp { client ->
                client.get("/api/podcasts").status shouldBe HttpStatusCode.Unauthorized
            }
        }

        "podcast routes support CRUD" {
            testApp { client ->
                val token = registerUser(client, "podcast-api@example.com", "podcastapi")

                val seriesTitle = "Podcast Series ${Uuid.random()}"
                val seriesResponse =
                    client.post("/api/series") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(Request(SeriesRequest(title = seriesTitle)))
                    }
                seriesResponse.status shouldBe HttpStatusCode.Created
                val seriesId =
                    seriesResponse.body<Response<SavedSeriesRoot>>().data.id.id.value.toString()

                val createResponse =
                    client.post("/api/podcasts") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(
                            Request(
                                PodcastRequest(
                                    seriesId = seriesId,
                                    feedUrl = "https://example.com/${Uuid.random()}.xml",
                                    autoSanitize = true,
                                    autoFetch = true,
                                    fetchIntervalMinutes = 60,
                                )
                            )
                        )
                    }
                createResponse.status shouldBe HttpStatusCode.Created
                val created = createResponse.body<Response<SavedPodcastRoot>>().data
                val podcastId = created.id.id.value.toString()
                created.seriesId.value.toString() shouldBe seriesId

                val listResponse =
                    client.get("/api/podcasts") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                listResponse.status shouldBe HttpStatusCode.OK
                val dashboard = listResponse.body<Response<PodcastDashboard>>().data
                dashboard.podcasts.any { it.id.value.toString() == podcastId } shouldBe true

                val updateResponse =
                    client.put("/api/podcasts/$podcastId") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody(
                            Request(
                                PodcastRequest(
                                    autoSanitize = false,
                                    autoFetch = false,
                                    fetchIntervalMinutes = 30,
                                )
                            )
                        )
                    }
                updateResponse.status shouldBe HttpStatusCode.OK
                val updated = updateResponse.body<Response<SavedPodcastRoot>>().data
                updated.autoSanitize shouldBe false
                updated.autoFetch shouldBe false
                updated.fetchIntervalMinutes shouldBe 30

                val getResponse =
                    client.get("/api/podcasts/$podcastId") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                getResponse.status shouldBe HttpStatusCode.OK
                val aggregate = getResponse.body<Response<JsonObject>>().data
                aggregate
                    .getValue("podcast")
                    .jsonObject
                    .getValue("id")
                    .jsonPrimitive
                    .content shouldBe podcastId

                val deleteResponse =
                    client.delete("/api/podcasts/$podcastId") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                deleteResponse.status shouldBe HttpStatusCode.NoContent

                val missingResponse =
                    client.get("/api/podcasts/$podcastId") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                missingResponse.status shouldBe HttpStatusCode.NotFound
            }
        }
    })
