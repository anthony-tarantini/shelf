package io.tarantini.shelf.integration.podcast.feed

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.catalog.podcast.domain.FeedParseFailed

class FeedParserTest :
    StringSpec({
        "FeedParser should parse RSS feed with iTunes fields" {
            val xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
                  <channel>
                    <title>Example Podcast</title>
                    <description>Test feed</description>
                    <itunes:image href="https://example.com/cover.jpg" />
                    <item>
                      <guid>ep-1</guid>
                      <title>Episode 1</title>
                      <description>Hello world</description>
                      <enclosure url="https://cdn.example.com/ep1.m4b" type="audio/x-m4b" />
                      <itunes:duration>00:10:05</itunes:duration>
                      <itunes:season>1</itunes:season>
                      <itunes:episode>2</itunes:episode>
                      <pubDate>Mon, 01 Jan 2024 12:00:00 GMT</pubDate>
                    </item>
                  </channel>
                </rss>
                """
                    .trimIndent()

            recover({
                val parsed = feedParser().parse(xml)
                parsed.title shouldBe "Example Podcast"
                parsed.imageUrl shouldBe "https://example.com/cover.jpg"
                parsed.episodes.size shouldBe 1
                parsed.episodes.first().guid shouldBe "ep-1"
                parsed.episodes.first().audioUrl shouldBe "https://cdn.example.com/ep1.m4b"
                parsed.episodes.first().season shouldBe 1
                parsed.episodes.first().episode shouldBe 2
                parsed.episodes.first().duration?.inWholeSeconds shouldBe 605
            }) {
                fail("Should not have failed: $it")
            }
        }

        "FeedParser should parse Atom feed entries" {
            val xml =
                """
                <feed xmlns="http://www.w3.org/2005/Atom">
                  <title>Atom Podcast</title>
                  <subtitle>Atom Description</subtitle>
                  <entry>
                    <id>atom-1</id>
                    <title>Atom Episode</title>
                    <summary>Summary</summary>
                    <updated>2024-01-01T12:00:00Z</updated>
                    <link rel="enclosure" href="https://cdn.example.com/atom1.mp3" type="audio/mpeg" />
                  </entry>
                </feed>
                """
                    .trimIndent()

            recover({
                val parsed = feedParser().parse(xml)
                parsed.title shouldBe "Atom Podcast"
                parsed.episodes.size shouldBe 1
                parsed.episodes.first().guid shouldBe "atom-1"
                parsed.episodes.first().audioUrl shouldBe "https://cdn.example.com/atom1.mp3"
            }) {
                fail("Should not have failed: $it")
            }
        }

        "FeedParser should reject XML with DOCTYPE (XXE hardening)" {
            val xml =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
                <rss version="2.0"><channel><title>&xxe;</title></channel></rss>
                """
                    .trimIndent()

            recover({
                feedParser().parse(xml)
                fail("Should have failed")
            }) {
                it shouldBe FeedParseFailed
            }
        }
    })
