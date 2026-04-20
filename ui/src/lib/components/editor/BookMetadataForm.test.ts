import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/svelte';
import BookMetadataForm from './BookMetadataForm.svelte';

const mockInitialData = {
    id: '1',
    title: 'Test Book',
    authors: ['Author 1'],
    description: 'A test description',
    publisher: 'Test Publisher',
    publishYear: 2024,
    genres: ['Genre 1'],
    series: [],
    ebookMetadata: {},
    audiobookMetadata: {},
    selectedAuthorIds: {},
    authorSuggestions: {}
};

describe('BookMetadataForm', () => {
    it('should render with initial data', () => {
        render(BookMetadataForm, { 
            props: { 
                initialData: mockInitialData,
                onSave: () => {},
                onCancel: () => {}
            } 
        });

        expect(screen.getByLabelText(/Title/i)).toHaveValue('Test Book');
        expect(screen.getByLabelText(/Publisher/i)).toHaveValue('Test Publisher');
        expect(screen.getByLabelText(/Publish Year/i)).toHaveValue('2024');
    });

    it('should call onSave with updated data', async () => {
        const onSave = vi.fn();
        render(BookMetadataForm, { 
            props: { 
                initialData: mockInitialData,
                onSave,
                onCancel: () => {}
            } 
        });

        const titleInput = screen.getByLabelText(/Title/i);
        await fireEvent.input(titleInput, { target: { value: 'Updated Title' } });

        const saveButton = screen.getByText(/Save Changes/i);
        await fireEvent.click(saveButton);

        expect(onSave).toHaveBeenCalledWith(expect.objectContaining({
            title: 'Updated Title'
        }));
    });

    it('should call onCancel when cancel button is clicked', async () => {
        const onCancel = vi.fn();
        render(BookMetadataForm, { 
            props: { 
                initialData: mockInitialData,
                onSave: () => {},
                onCancel
            } 
        });

        const cancelButton = screen.getByText(/Cancel/i);
        await fireEvent.click(cancelButton);

        expect(onCancel).toHaveBeenCalled();
    });
});
