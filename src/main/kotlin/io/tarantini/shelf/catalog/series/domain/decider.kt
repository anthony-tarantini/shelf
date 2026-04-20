package io.tarantini.shelf.catalog.series.domain

data class SeriesMutationDecision(val title: SeriesTitle)

object SeriesMutationDecider {
    fun decideCreate(command: CreateSeriesCommand): SeriesMutationDecision =
        SeriesMutationDecision(title = command.title)

    fun decideUpdate(
        existing: SavedSeriesRoot,
        command: UpdateSeriesCommand,
    ): SeriesMutationDecision {
        val nextTitle = command.title ?: SeriesTitle.fromRaw(existing.name)
        return if (
            canonicalizeSeriesTitle(existing.name) == canonicalizeSeriesTitle(nextTitle.value)
        ) {
            SeriesMutationDecision(title = SeriesTitle.fromRaw(existing.name))
        } else {
            SeriesMutationDecision(title = nextTitle)
        }
    }
}
