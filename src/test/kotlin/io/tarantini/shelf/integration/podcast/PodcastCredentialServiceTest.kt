@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package io.tarantini.shelf.integration.podcast

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.IntegrationSpec
import io.tarantini.shelf.app.id
import io.tarantini.shelf.catalog.podcast.domain.FeedToken
import io.tarantini.shelf.catalog.podcast.domain.FeedUrl
import io.tarantini.shelf.catalog.podcast.domain.PodcastRoot
import io.tarantini.shelf.catalog.podcast.podcastMutationRepository
import io.tarantini.shelf.integration.podcast.feed.FeedFetchCredentials
import io.tarantini.shelf.integration.security.EncryptionService

class PodcastCredentialServiceTest :
    IntegrationSpec({
        fun unique(prefix: String) = "$prefix-${System.nanoTime()}"

        "credential service should encrypt and round-trip credentials" {
            testWithDeps { deps ->
                val mutationRepo = podcastMutationRepository(deps.database.podcastQueries)
                val credentialService =
                    podcastCredentialService(
                        deps.database.credentialsQueries,
                        EncryptionService("test-encryption-secret"),
                    )

                val seriesId = deps.database.seriesQueries.insert(unique("series")).executeAsOne()

                val podcast =
                    recover({
                        mutationRepo.createPodcast(
                            PodcastRoot.new(
                                seriesId = seriesId,
                                feedUrl = FeedUrl.fromRaw("https://example.com/feed.xml"),
                                feedToken = FeedToken.generate(),
                            )
                        )
                    }) {
                        fail("Should not have failed: $it")
                    }

                recover({
                    credentialService.saveFeedCredentials(
                        podcast.id.id,
                        FeedFetchCredentials.Basic("alice", "secret"),
                    )
                    credentialService.hasFeedCredentials(podcast.id.id) shouldBe true
                    credentialService.getFeedCredentials(podcast.id.id) shouldBe
                        FeedFetchCredentials.Basic("alice", "secret")
                }) {
                    fail("Should not have failed: $it")
                }

                val row =
                    deps.database.credentialsQueries.selectByPodcastId(podcast.id.id).executeAsOne()
                row.credential_type shouldBe "HTTP_BASIC"
                row.encrypted_value.contentEquals("alice:secret".encodeToByteArray()) shouldBe false

                recover({
                    credentialService.clearFeedCredentials(podcast.id.id)
                    credentialService.hasFeedCredentials(podcast.id.id) shouldBe false
                    credentialService.getFeedCredentials(podcast.id.id) shouldBe null
                }) {
                    fail("Should not have failed: $it")
                }
            }
        }
    })
