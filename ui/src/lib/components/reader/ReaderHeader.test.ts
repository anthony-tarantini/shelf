import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/svelte';
import ReaderHeader from './ReaderHeader.svelte';

const mockThemes = {
    light: { bg: '#fff', fg: '#000', ui: '', border: '' },
    sepia: { bg: '#f4e', fg: '#5b4', ui: '', border: '' },
    dark: { bg: '#1a1', fg: '#d1d', ui: '', border: '' }
};

describe('ReaderHeader', () => {
    const defaultProps = {
        bookId: '123',
        title: 'Test Book',
        currentTheme: 'sepia' as const,
        themes: mockThemes,
        onPrev: vi.fn(),
        onNext: vi.fn(),
        onToggleToc: vi.fn()
    };

    it('should render the book title', () => {
        render(ReaderHeader, { props: defaultProps });
        expect(screen.getByText('Test Book')).toBeInTheDocument();
    });

    it('should call navigation functions when buttons are clicked', async () => {
        render(ReaderHeader, { props: defaultProps });
        
        await fireEvent.click(screen.getByTitle('Previous Page'));
        expect(defaultProps.onPrev).toHaveBeenCalled();
        
        await fireEvent.click(screen.getByTitle('Next Page'));
        expect(defaultProps.onNext).toHaveBeenCalled();
    });

    it('should call onToggleToc when contents button is clicked', async () => {
        render(ReaderHeader, { props: defaultProps });
        await fireEvent.click(screen.getByTitle('Table of Contents'));
        expect(defaultProps.onToggleToc).toHaveBeenCalled();
    });

    it('should render theme selector buttons', () => {
        render(ReaderHeader, { props: defaultProps });
        expect(screen.getAllByTitle('Light Mode')).toHaveLength(2);
        expect(screen.getAllByTitle('Sepia Mode')).toHaveLength(2);
        expect(screen.getAllByTitle('Dark Mode')).toHaveLength(2);
    });
});
