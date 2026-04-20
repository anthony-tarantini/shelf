import type {
    AuthorRoot,
    BookAggregate,
    BookSeriesEntry,
    MediaType as MediaTypeEnum,
    StagedEditionMetadata,
    StagedSeries,
} from './models';
import {MediaType} from './models';

/**
 * A flat view of book metadata used by MetadataManager and MetadataState.
 * Both StagedBook and BookAggregate can be projected into this shape.
 */
export interface MetadataBookView {
    id: string;
    title: string;
    authors: string[];
    description?: string;
    publisher?: string;
    publishYear?: number;
    genres: string[];
    moods: string[];
    series: StagedSeries[];
    ebookMetadata?: StagedEditionMetadata;
    audiobookMetadata?: StagedEditionMetadata;
    coverPath?: string;
    selectedAuthorIds: Record<string, string | null>;
    authorSuggestions: Record<string, AuthorRoot[]>;
}

/**
 * Adapts a BookAggregate (library book) into the flat MetadataBookView shape.
 */
export function bookAggregateToView(agg: BookAggregate): MetadataBookView {
    const ebookEdition = agg.metadata?.editions?.find(e => e.edition.format === MediaType.EBOOK)?.edition;
    const audiobookEdition = agg.metadata?.editions?.find(e => e.edition.format === MediaType.AUDIOBOOK)?.edition;

    return {
        id: agg.book.id,
        title: agg.book.title,
        authors: (agg.authors ?? []).map(a => a.name),
        description: agg.metadata?.metadata?.description,
        publisher: agg.metadata?.metadata?.publisher,
        publishYear: agg.metadata?.metadata?.published,
        genres: agg.metadata?.metadata?.genres ?? [],
        moods: agg.metadata?.metadata?.moods ?? [],
        series: (agg.series ?? []).map((s: BookSeriesEntry) => ({name: s.name, index: s.index})),
        ebookMetadata: ebookEdition ? {
            isbn10: ebookEdition.isbn10,
            isbn13: ebookEdition.isbn13,
            asin: ebookEdition.asin,
        } : undefined,
        audiobookMetadata: audiobookEdition ? {
            isbn10: audiobookEdition.isbn10,
            isbn13: audiobookEdition.isbn13,
            asin: audiobookEdition.asin,
            narrator: audiobookEdition.narrator,
        } : undefined,
        coverPath: agg.book.coverPath,
        selectedAuthorIds: Object.fromEntries((agg.authors ?? []).map(a => [a.name, a.id])),
        authorSuggestions: {},
    };
}
