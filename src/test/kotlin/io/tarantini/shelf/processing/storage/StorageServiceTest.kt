@file:OptIn(ExperimentalUuidApi::class)

package io.tarantini.shelf.processing.storage

import arrow.core.raise.recover
import io.kotest.assertions.fail
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.uuid.ExperimentalUuidApi

class StorageServiceTest :
    StringSpec({
        val tempDir = Files.createTempDirectory("shelf-storage-test")
        val service = localStorageService(tempDir.toString())

        afterProject { tempDir.toFile().deleteRecursively() }

        "save and read file bytes" {
            recover({
                val path = StoragePath("test/file.txt")
                val content = FileBytes("hello world".toByteArray())

                service.save(path, content)

                service.exists(path) shouldBe true
                val readContent = service.read(path)
                String(readContent.value) shouldBe "hello world"
            }) {
                fail("Should not have failed: $it")
            }
        }

        "delete file" {
            recover({
                val path = StoragePath("test/to-delete.txt")
                service.save(path, FileBytes("delete me".toByteArray()))

                service.exists(path) shouldBe true
                service.delete(path)
                service.exists(path) shouldBe false
            }) {
                fail("Should not have failed: $it")
            }
        }

        "generate thumbnail" {
            recover({
                val originalPath = StoragePath("test/image.png")
                // Use a real image from the project for Thumbnailator
                val imageFile = Paths.get("ui/static/apple-touch-icon.png")
                // Note: Test runner usually runs from project root.
                if (Files.exists(imageFile)) {
                    val content = FileBytes(Files.readAllBytes(imageFile))
                    service.save(originalPath, content)

                    val thumbPath = service.generateThumbnail(originalPath, 100)

                    thumbPath.value shouldBe "test/image_thumb.jpg"
                    service.exists(thumbPath) shouldBe true
                }
            }) {
                fail("Should not have failed: $it")
            }
        }

        "reject path traversal at value boundary" {
            recover({
                StoragePath("../escape.txt")
                fail("Should have failed")
            }) {
                it shouldBe UnauthorizedAccess
            }
        }

        "reject path traversal when trusted raw values escape the storage root" {
            recover({
                service.save(StoragePath.fromRaw("../escape.txt"), FileBytes("oops".toByteArray()))
                fail("Should have failed")
            }) {
                it shouldBe UnauthorizedAccess
            }
        }
    })
