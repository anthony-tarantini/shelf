package io.tarantini.shelf.user.activity.domain

object ActivityMutationDecider {
    fun decideProgressUpdate(
        existing: ReadingProgress?,
        command: SaveReadingProgressCommand,
    ): ReadingProgress = command.progress.mergeWith(existing)

    fun decideReadStatusUpdate(command: SaveReadStatusCommand): ReadStatus = command.status
}
