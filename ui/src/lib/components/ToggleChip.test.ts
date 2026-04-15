import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/svelte';
import ToggleChip from './ToggleChip.svelte';

describe('ToggleChip', () => {
    it('should render the label', () => {
        render(ToggleChip, { props: { label: 'Test Label', selected: false, onToggle: () => {} } });
        expect(screen.getByText('Test Label')).toBeInTheDocument();
    });

    it('should show close icon when selected', () => {
        render(ToggleChip, { props: { label: 'Selected', selected: true, onToggle: () => {} } });
        expect(screen.getByText('×')).toBeInTheDocument();
    });

    it('should call onToggle when clicked', async () => {
        const onToggle = vi.fn();
        render(ToggleChip, { props: { label: 'Click Me', selected: false, onToggle } });
        
        const button = screen.getByRole('button');
        await fireEvent.click(button);
        
        expect(onToggle).toHaveBeenCalled();
    });
});
