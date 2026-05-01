@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.integration.koreader.stats

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.catalog.metadata.domain.EditionId
import io.tarantini.shelf.integration.koreader.stats.domain.EditionMatch
import io.tarantini.shelf.integration.koreader.stats.domain.EditionMatcher
import io.tarantini.shelf.integration.koreader.stats.domain.Md5Hash
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

class EditionMatcherTest :
    StringSpec({
        val md5 = Md5Hash.fromRawOrNull("a".repeat(32))!!
        val editionId = EditionId.fromRaw(Uuid.random().toJavaUuid())

        "null md5 returns Unmatched" {
            EditionMatcher.decide(null) { editionId } shouldBe EditionMatch.Unmatched
        }

        "lookup hit returns Matched" {
            EditionMatcher.decide(md5) { editionId } shouldBe EditionMatch.Matched(editionId)
        }

        "lookup miss returns Unmatched" {
            EditionMatcher.decide(md5) { null } shouldBe EditionMatch.Unmatched
        }
    })
