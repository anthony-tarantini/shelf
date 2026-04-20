package io.tarantini.shelf.user.activity.domain

data class SaveReadingProgressCommand(val progress: ReadingProgress)

data class SaveReadStatusCommand(val status: ReadStatus)

fun ReadingProgress.toCommand(): SaveReadingProgressCommand =
    SaveReadingProgressCommand(progress = normalized())

fun ReadStatusRequest.toCommand(): SaveReadStatusCommand = SaveReadStatusCommand(status = status)
