package io.tarantini.shelf.integration.koreader.stats

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.tarantini.shelf.integration.koreader.stats.domain.Md5Hash

class Md5HashTest :
    StringSpec({
        "valid 32 hex lowercase parses" {
            Md5Hash.fromRawOrNull("0123456789abcdef0123456789abcdef")?.value shouldBe
                "0123456789abcdef0123456789abcdef"
        }

        "uppercase folds to lowercase" {
            Md5Hash.fromRawOrNull("0123456789ABCDEF0123456789ABCDEF")?.value shouldBe
                "0123456789abcdef0123456789abcdef"
        }

        "whitespace is trimmed" {
            Md5Hash.fromRawOrNull("  0123456789abcdef0123456789abcdef  ")?.value shouldBe
                "0123456789abcdef0123456789abcdef"
        }

        "non-hex returns null" { Md5Hash.fromRawOrNull("z".repeat(32)).shouldBeNull() }

        "wrong length returns null" { Md5Hash.fromRawOrNull("abcdef").shouldBeNull() }

        "null input returns null" { Md5Hash.fromRawOrNull(null).shouldBeNull() }
    })
