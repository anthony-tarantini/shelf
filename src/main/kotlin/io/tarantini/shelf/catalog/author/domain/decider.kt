package io.tarantini.shelf.catalog.author.domain

data class AuthorMutationDecision(val name: AuthorName)

object AuthorMutationDecider {
    fun decideCreate(command: CreateAuthorCommand): AuthorMutationDecision =
        AuthorMutationDecision(name = command.name)

    fun decideUpdate(
        existing: SavedAuthorRoot,
        command: UpdateAuthorCommand,
    ): AuthorMutationDecision =
        if (canonicalizeAuthorName(existing.name) == canonicalizeAuthorName(command.name.value)) {
            AuthorMutationDecision(name = AuthorName.fromRaw(existing.name))
        } else {
            AuthorMutationDecision(name = command.name)
        }
}
