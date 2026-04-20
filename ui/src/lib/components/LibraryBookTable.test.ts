import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/svelte';
import LibraryBookTable from './LibraryBookTable.svelte';
import { MediaType } from '../types/models';

const mockBooks: any[] = [
    {
        book: { id: 'book-1', title: 'Book One', coverPath: null },
        authors: [{ id: 'auth-1', name: 'Author One' }],
        series: [],
        metadata: { editions: [{ edition: { format: MediaType.EBOOK } }] },
        userState: { readStatus: 'UNREAD' }
    },
    {
        book: { id: 'book-2', title: 'Book Two', coverPath: 'path/to/cover' },
        authors: [{ id: 'auth-2', name: 'Author Two' }],
        series: [],
        metadata: { editions: [{ edition: { format: MediaType.AUDIOBOOK } }] },
        userState: { readStatus: 'FINISHED' }
    }
];

describe('LibraryBookTable', () => {
    it('should render book titles and authors', () => {
        render(LibraryBookTable, { props: { books: mockBooks } });
        
        expect(screen.getByText('Book One')).toBeInTheDocument();
        expect(screen.getByText('Author One')).toBeInTheDocument();
        expect(screen.getByText('Book Two')).toBeInTheDocument();
        expect(screen.getByText('Author Two')).toBeInTheDocument();
    });

    it('should render media type badges', () => {
        render(LibraryBookTable, { props: { books: mockBooks } });
        
        expect(screen.getByText('Ebook')).toBeInTheDocument();
        expect(screen.getByText('Audio')).toBeInTheDocument();
    });

    it('should call handleSort when clicking sortable headers', async () => {
        // Since sortBy and sortDir are $bindable, we can't easily check internal state 
        // without a wrapper component or checking the text content of headers if they update.
        render(LibraryBookTable, { props: { books: mockBooks, sortBy: 'title', sortDir: 'ASC' } });
        
        const titleHeader = screen.getByText(/Title/);
        expect(screen.getByText(/Title ↑/)).toBeInTheDocument();
        
        await fireEvent.click(titleHeader);
        // It should toggle to DESC
        expect(screen.getByText(/Title ↓/)).toBeInTheDocument();
    });
});
