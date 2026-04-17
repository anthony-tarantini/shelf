import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/svelte';
import BatchActionBar from './BatchActionBar.svelte';

describe('BatchActionBar', () => {
    const mockActions = [
        { id: 'PROMOTE', label: 'Promote Selected' },
        { id: 'DELETE', label: 'Delete Selected', variant: 'destructive' }
    ] as const;

    it('should not render when count is zero', () => {
        const { container } = render(BatchActionBar, { 
            props: { 
                selectedCount: 0, 
                onClear: () => {}, 
                onAction: () => {},
                actions: mockActions
            } 
        });
        expect(container.textContent).toBe('');
    });

    it('should render correct count', () => {
        render(BatchActionBar, { 
            props: { 
                selectedCount: 5, 
                onClear: () => {}, 
                onAction: () => {},
                actions: mockActions
            } 
        });
        expect(screen.getByText('5 items selected')).toBeInTheDocument();
    });

    it('should render actions', () => {
        render(BatchActionBar, { 
            props: { 
                selectedCount: 1, 
                onClear: () => {}, 
                onAction: () => {},
                actions: mockActions
            } 
        });
        expect(screen.getByText('Promote Selected')).toBeInTheDocument();
        expect(screen.getByText('Delete Selected')).toBeInTheDocument();
    });

    it('should call onAction with correct actionId', async () => {
        const onAction = vi.fn();
        render(BatchActionBar, { 
            props: { 
                selectedCount: 1, 
                onClear: () => {}, 
                onAction,
                actions: mockActions
            } 
        });
        
        await fireEvent.click(screen.getByText('Promote Selected'));
        expect(onAction).toHaveBeenCalledWith('PROMOTE');
        
        await fireEvent.click(screen.getByText('Delete Selected'));
        expect(onAction).toHaveBeenCalledWith('DELETE');
    });

    it('should disable buttons when processing is true', () => {
        render(BatchActionBar, { 
            props: { 
                selectedCount: 1, 
                onClear: () => {}, 
                onAction: () => {},
                actions: mockActions,
                processing: true
            } 
        });
        expect(screen.getByText('Promote Selected')).toBeDisabled();
        expect(screen.getByText('Delete Selected')).toBeDisabled();
    });

    it('should call onClear when clear button is clicked', async () => {
        const onClear = vi.fn();
        render(BatchActionBar, { 
            props: { 
                selectedCount: 1, 
                onClear, 
                onAction: () => {},
                actions: mockActions
            } 
        });
        
        await fireEvent.click(screen.getByText('Clear selection'));
        expect(onClear).toHaveBeenCalled();
    });
});
