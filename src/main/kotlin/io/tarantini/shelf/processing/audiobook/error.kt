package io.tarantini.shelf.processing.audiobook

import io.tarantini.shelf.app.AppError

sealed interface AudiobookError : AppError

object InvalidAudioFile : AudiobookError

object MissingAudioMetadata : AudiobookError

object AudioMetadataReadFailed : AudiobookError
