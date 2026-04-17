import { browser } from '$app/environment';
import type {StagedBook, StagedSeries, StagedEditionMetadata} from '$lib/types/models';

export class MetadataState {
    // 1. We define the state fields
    title = $state('');
    authors = $state<string[]>([]);
    description = $state('');
    publisher = $state('');
    publishYear = $state<number>();
    genres = $state<string[]>([]);
    series = $state<StagedSeries[]>([]);
    ebookMetadata = $state<StagedEditionMetadata>();
    audiobookMetadata = $state<StagedEditionMetadata>();
    coverUrl = $state<string>();

    // Use a private field to store the "Source of Truth" getter
    readonly #getBook: () => StagedBook;

    constructor(bookGetter: () => StagedBook) {
        this.#getBook = bookGetter;

        // 2. We use an effect INSIDE the class to keep things in sync.
        // Whenever the book returned by the getter changes, we reset the state.
        if (browser) {
            try {
                $effect(() => {
                    this.refresh();
                });
            } catch (e) {
                // In some contexts (like pure unit tests) $effect might fail if not inside a component.
                // We ignore this and expect manual refresh() in such tests.
            }
        }
    }

    /**
     * Resets the editable state to match the current source book
     */
    refresh() {
        const book = this.#getBook(); // Call the closure to get the latest proxy
        const snapshot = $state.snapshot(book); // Take a non-reactive snapshot to work with

        this.title = snapshot.title;
        this.authors = [...snapshot.authors];
        this.description = snapshot.description || '';
        this.publisher = snapshot.publisher || '';
        this.publishYear = snapshot.publishYear;
        this.genres = [...snapshot.genres];
        this.series = snapshot.series ? JSON.parse(JSON.stringify(snapshot.series)) : [];
        this.ebookMetadata = snapshot.ebookMetadata ? { ...snapshot.ebookMetadata } : undefined;
        this.audiobookMetadata = snapshot.audiobookMetadata ? { ...snapshot.audiobookMetadata } : undefined;
        this.coverUrl = snapshot.coverPath;
    }

    /**
     * Toggles an author in the merged list
     */
    toggleAuthor(name: string) {
        if (this.authors.includes(name)) {
            this.authors = this.authors.filter(a => a !== name);
        } else {
            this.authors.push(name);
        }
    }

    /**
     * Toggles a genre in the merged list
     */
    toggleGenre(name: string) {
        if (this.genres.includes(name)) {
            this.genres = this.genres.filter(g => g !== name);
        } else {
            this.genres.push(name);
        }
    }
    
    /**
     * Adds a series if it doesn't already exist.
     */
    addSeries(name: string, index?: number) {
        if (!this.series.find(s => s.name === name)) {
            this.series.push({ name, index });
        }
    }
    
    /**
     * Removes a series by name.
     */
    removeSeries(name: string) {
        this.series = this.series.filter(s => s.name !== name);
    }

    /**
     * Updates the index of an existing series.
     */
    updateSeriesIndex(name: string, index?: number) {
        const s = this.series.find(s => s.name === name);
        if (s) {
            s.index = index;
        }
    }

    getUpdatePayload() {
        const book = this.#getBook();
        // Use the snapshot of the ID map to ensure we don't accidentally mutate the prop
        const originalIds = $state.snapshot(book.selectedAuthorIds) || {};

        const cleanAuthorIds: Record<string, string | null> = {};
        for (const author of this.authors) {
            cleanAuthorIds[author] = originalIds[author] ?? null;
        }

        return {
            title: this.title,
            authors: this.authors,
            selectedAuthorIds: cleanAuthorIds,
            description: this.description,
            publisher: this.publisher,
            publishYear: this.publishYear,
            genres: this.genres,
            moods: book.moods,
            series: this.series,
            ebookMetadata: this.ebookMetadata,
            audiobookMetadata: this.audiobookMetadata,
            coverUrl: this.coverUrl !== book.coverPath ? this.coverUrl : null
        };
    }
}