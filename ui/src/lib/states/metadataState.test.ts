import { describe, it, expect } from 'vitest';
import { MetadataState } from './metadataState.svelte';
import { MediaType, type StagedBook } from '$lib/types/models';

const mockBook: StagedBook = {
    id: '1',
    userId: 'user-1',
    title: 'Original Title',
    authors: ['Author 1'],
    authorSuggestions: {},
    selectedAuthorIds: { 'Author 1': 'id-1' },
    storagePath: '/path/to/book',
    coverPath: '/path/to/cover',
    description: 'Description',
    publisher: 'Publisher',
    publishYear: 2024,
    genres: ['Genre 1'],
    moods: [],
    series: [],
    mediaType: MediaType.EBOOK,
    chapters: [],
    size: 1000,
    createdAt: new Date().toISOString()
};

describe('MetadataState', () => {
    it('should initialize with book data', () => {
        const state = new MetadataState(() => mockBook);
        state.refresh(); // Manual refresh as $effect doesn't run in pure unit tests easily

        expect(state.title).toBe(mockBook.title);
        expect(state.authors).toEqual(mockBook.authors);
        expect(state.publishYear).toBe(mockBook.publishYear);
    });

    it('should toggle authors', () => {
        const state = new MetadataState(() => mockBook);
        state.refresh();

        state.toggleAuthor('Author 2');
        expect(state.authors).toContain('Author 2');
        
        state.toggleAuthor('Author 1');
        expect(state.authors).not.toContain('Author 1');
    });

    it('should toggle genres', () => {
        const state = new MetadataState(() => mockBook);
        state.refresh();

        state.toggleGenre('Genre 2');
        expect(state.genres).toContain('Genre 2');
        
        state.toggleGenre('Genre 1');
        expect(state.genres).not.toContain('Genre 1');
    });

    it('should generate correct update payload', () => {
        const state = new MetadataState(() => mockBook);
        state.refresh();

        state.title = 'New Title';
        state.toggleAuthor('Author 2');
        state.coverUrl = 'new-cover.jpg';

        const payload = state.getUpdatePayload();
        expect(payload.title).toBe('New Title');
        expect(payload.authors).toEqual(['Author 1', 'Author 2']);
        expect(payload.selectedAuthorIds).toEqual({
            'Author 1': 'id-1',
            'Author 2': null
        });
        expect(payload.coverUrl).toBe('new-cover.jpg');
    });
});
