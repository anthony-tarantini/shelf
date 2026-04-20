import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import ButtonTestHost from './ButtonTestHost.svelte';

describe('Button', () => {
    it('should render with children', () => {
        render(ButtonTestHost);
        expect(screen.getByRole('button')).toHaveTextContent('Default Text');
    });

    it('should apply variant classes', () => {
        const { container } = render(ButtonTestHost, { props: { variant: 'destructive' } });
        const button = screen.getByRole('button');
        expect(button).toHaveClass('bg-destructive');
    });

    it('should be disabled when prop is set', () => {
        render(ButtonTestHost, { props: { disabled: true } });
        const button = screen.getByRole('button');
        expect(button).toBeDisabled();
    });
});
