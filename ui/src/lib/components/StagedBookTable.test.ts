import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/svelte';
import StagedBookTable from './StagedBookTable.svelte';
import { MediaType, type StagedBook } from '../types/models';

vi.mock('../api/client.ts', () => ({
    api: {
        post: vi.fn(),
        delete: vi.fn()
    }
}));

vi.mock('$app/navigation', () => ({
    invalidateAll: vi.fn()
}));

const mockBooks: StagedBook[] = [
    {
        id: '1',
        userId: 'u1',
        title: 'Book A',
        authors: ['Author A'],
        authorSuggestions: {},
        selectedAuthorIds: {},
        storagePath: 'p1',
        mediaType: MediaType.EBOOK,
        chapters: [],
        size: 100,
        genres: [],
        moods: [],
        series: [],
        createdAt: '2024-01-01T00:00:00Z'
    },
    {
        id: '2',
        userId: 'u1',
        title: 'Book B',
        authors: ['Author B'],
        authorSuggestions: {},
        selectedAuthorIds: {},
        storagePath: 'p2',
        mediaType: MediaType.AUDIOBOOK,
        chapters: [],
        size: 200,
        genres: [],
        moods: [],
        series: [],
        createdAt: '2024-01-02T00:00:00Z'
    }
];

describe('StagedBookTable', () => {
    it('should render all books', () => {
        render(StagedBookTable, { props: { books: mockBooks } });
        expect(screen.getByText('Book A')).toBeInTheDocument();
        expect(screen.getByText('Book B')).toBeInTheDocument();
        
        // Check for media type badges (using text from MediaTypeBadge)
        expect(screen.getByText('Ebook')).toBeInTheDocument();
        expect(screen.getByText('Audio')).toBeInTheDocument();
    });

    it('should expand row when title is clicked', async () => {
        render(StagedBookTable, { props: { books: mockBooks } });
        
        const titleButton = screen.getByRole('button', { name: 'Book A' });
        await fireEvent.click(titleButton);
        
        // Expanded view should show tabs from StagedBookExpansion
        expect(await screen.findByText('Edit Details')).toBeInTheDocument();
        expect(await screen.findByText('External Metadata')).toBeInTheDocument();
    });

    it('should toggle selection via checkbox', async () => {
        render(StagedBookTable, { 
            props: { 
                books: mockBooks
            } 
        });

        const checkboxes = screen.getAllByRole('checkbox');
        await fireEvent.click(checkboxes[1]);

        expect(checkboxes[1]).toBeChecked();

        await fireEvent.click(checkboxes[0]);
        expect(checkboxes[1]).toBeChecked();
        expect(checkboxes[2]).toBeChecked();
    });

    it('should toggle sort direction when clicking the same header twice', async () => {
        render(StagedBookTable, {
            props: {
                books: mockBooks
            }
        });

        const titleHeader = screen.getByText(/Title/);
        await fireEvent.click(titleHeader);
        expect(screen.getByText(/Title ↑/)).toBeInTheDocument();

        await fireEvent.click(screen.getByText(/Title ↑/));
        expect(screen.getByText(/Title ↓/)).toBeInTheDocument();
    });
});
