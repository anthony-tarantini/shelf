package io.tarantini.shelf.organization.library.domain

data class LibraryMutationDecision(val title: LibraryTitle)

object LibraryMutationDecider {
    fun decideCreate(command: CreateLibraryCommand): LibraryMutationDecision =
        LibraryMutationDecision(title = command.title)

    fun decideUpdate(
        existing: SavedLibraryRoot,
        command: UpdateLibraryCommand,
    ): LibraryMutationDecision {
        val nextTitle = command.title ?: LibraryTitle.fromRaw(existing.title)
        return if (
            canonicalizeLibraryTitle(existing.title) == canonicalizeLibraryTitle(nextTitle.value)
        ) {
            LibraryMutationDecision(title = LibraryTitle.fromRaw(existing.title))
        } else {
            LibraryMutationDecision(title = nextTitle)
        }
    }
}
