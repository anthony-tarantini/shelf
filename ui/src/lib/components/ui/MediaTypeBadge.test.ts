import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import MediaTypeBadge from './MediaTypeBadge.svelte';
import { MediaType } from '$lib/types/models';

describe('MediaTypeBadge', () => {
    it('should render Audiobook badge', () => {
        render(MediaTypeBadge, { props: { type: MediaType.AUDIOBOOK } });
        expect(screen.getByText('Audio')).toBeInTheDocument();
        expect(screen.getByTitle('Audiobook')).toBeInTheDocument();
    });

    it('should render Ebook badge', () => {
        render(MediaTypeBadge, { props: { type: MediaType.EBOOK } });
        expect(screen.getByText('Ebook')).toBeInTheDocument();
        expect(screen.getByTitle('Ebook')).toBeInTheDocument();
    });

    it('should render Ebook badge for unknown types', () => {
        render(MediaTypeBadge, { props: { type: 'UNKNOWN' } });
        expect(screen.getByText('Ebook')).toBeInTheDocument();
    });

    it('should apply custom class', () => {
        const { container } = render(MediaTypeBadge, { props: { type: MediaType.EBOOK, class: 'custom-class' } });
        expect(container.querySelector('.custom-class')).toBeInTheDocument();
    });
});
