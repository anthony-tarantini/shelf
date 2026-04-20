import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import ReaderFooter from './ReaderFooter.svelte';

const mockThemes = {
    sepia: { bg: '#f4e', fg: '#5b4', border: '#000' }
};

describe('ReaderFooter', () => {
    it('should render progress percentage', () => {
        render(ReaderFooter, { 
            props: { progress: 0.45, currentTheme: 'sepia', themes: mockThemes, onPrev: vi.fn(), onNext: vi.fn() } 
        });
        expect(screen.getAllByText('45%')).toHaveLength(2);
    });

    it('should render progress-only footer without duplicate mobile navigation buttons', () => {
        render(ReaderFooter, { 
            props: { progress: 0.5, currentTheme: 'sepia', themes: mockThemes, onPrev: vi.fn(), onNext: vi.fn() } 
        });

        expect(screen.queryByRole('button', { name: 'Previous Page', hidden: true })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Next Page', hidden: true })).not.toBeInTheDocument();
    });
});
