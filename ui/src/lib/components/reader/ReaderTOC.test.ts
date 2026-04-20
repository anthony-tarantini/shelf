import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/svelte';
import ReaderTOC from './ReaderTOC.svelte';

const mockThemes = {
    sepia: { bg: '#f4e', fg: '#5b4', border: '#000' }
};

describe('ReaderTOC', () => {
    const mockToc = [
        { label: 'Chapter 1', href: 'ch1.xhtml' },
        { label: 'Chapter 2', href: 'ch2.xhtml' }
    ];

    it('should not render anything when closed', () => {
        const { container } = render(ReaderTOC, { 
            props: { toc: mockToc, currentTheme: 'sepia', themes: mockThemes, isOpen: false, onJumpTo: vi.fn() } 
        });
        expect(container.querySelector('aside')).not.toBeInTheDocument();
    });

    it('should render TOC items when open', () => {
        render(ReaderTOC, { 
            props: { toc: mockToc, currentTheme: 'sepia', themes: mockThemes, isOpen: true, onJumpTo: vi.fn() } 
        });
        expect(screen.getByText('Chapter 1')).toBeInTheDocument();
        expect(screen.getByText('Chapter 2')).toBeInTheDocument();
    });

    it('should call onJumpTo when a chapter is clicked', async () => {
        const onJumpTo = vi.fn();
        render(ReaderTOC, { 
            props: { toc: mockToc, currentTheme: 'sepia', themes: mockThemes, isOpen: true, onJumpTo } 
        });
        
        await fireEvent.click(screen.getByText('Chapter 1'));
        expect(onJumpTo).toHaveBeenCalledWith('ch1.xhtml');
    });
});
