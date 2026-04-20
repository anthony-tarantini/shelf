import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/svelte';
import ReaderOverlay from './ReaderOverlay.svelte';

const mockThemes = {
    sepia: { bg: '#f4e', fg: '#5b4' }
};

describe('ReaderOverlay', () => {
    it('should show loading spinner when isLoading is true', () => {
        render(ReaderOverlay, { 
            props: { bookId: '1', isLoading: true, errorMessage: null, currentTheme: 'sepia', themes: mockThemes, onPrev: vi.fn(), onNext: vi.fn() } 
        });
        expect(screen.getByText('Preparing your book...')).toBeInTheDocument();
    });

    it('should show error message when present', () => {
        render(ReaderOverlay, { 
            props: { bookId: '1', isLoading: false, errorMessage: 'Failed to load', currentTheme: 'sepia', themes: mockThemes, onPrev: vi.fn(), onNext: vi.fn() } 
        });
        expect(screen.getByText('Something went wrong')).toBeInTheDocument();
        expect(screen.getByText('Failed to load')).toBeInTheDocument();
    });

    it('should have tap zones that call navigation functions', async () => {
        const onPrev = vi.fn();
        const onNext = vi.fn();
        render(ReaderOverlay, { 
            props: { bookId: '1', isLoading: false, errorMessage: null, currentTheme: 'sepia', themes: mockThemes, onPrev, onNext } 
        });
        
        const previousButtons = screen.getAllByLabelText('Previous Page');
        const nextButtons = screen.getAllByLabelText('Next Page');

        await fireEvent.click(previousButtons[0]);
        expect(onPrev).toHaveBeenCalled();
        
        await fireEvent.click(nextButtons[0]);
        expect(onNext).toHaveBeenCalled();
    });
});
