package io.tarantini.shelf.processing.sanitization.domain

import io.tarantini.shelf.app.AppError

sealed interface SanitizationError : AppError

sealed interface SanitizationPersistenceError : SanitizationError

object SanitizationJobNotFound : SanitizationPersistenceError

sealed interface SanitizationValidationError : SanitizationError

object EmptySanitizationJobId : SanitizationValidationError

object InvalidSanitizationJobId : SanitizationValidationError

object InvalidAudioTimestamp : SanitizationValidationError

object InvalidAdSegment : SanitizationValidationError

object InvalidSanitizationTransition : SanitizationError

object FfmpegExecutionFailed : SanitizationError

object TranscriptionFailed : SanitizationError

object OriginalFileNotFound : SanitizationError
