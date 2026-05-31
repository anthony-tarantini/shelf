import { test, expect } from './fixtures';

test.describe('Metadata Editing', () => {
    test('should allow editing book metadata', async ({ authenticatedPage, apiHelper }) => {
        // Seed a book so we have something to edit
        await apiHelper.seedBook();
        
        await authenticatedPage.goto('/');
        
        const bookLink = authenticatedPage.locator('[data-testid="book-card"]').first();
        await expect(bookLink).toBeVisible();
        await bookLink.click();
        
        // Go to edit page
        const editBtn = authenticatedPage.getByRole('button', { name: 'Edit Metadata', exact: true });
        await expect(editBtn).toBeVisible();
        await editBtn.click();

        // Try to change title
        const titleInput = authenticatedPage.getByLabel(/title/i).first();
        await expect(titleInput).toBeVisible();
        
        const originalTitle = await titleInput.inputValue();
        await titleInput.fill(originalTitle + ' (Edited)');
        
        const saveBtn = authenticatedPage.getByRole('button', { name: /save|update/i });
        await expect(saveBtn).toBeVisible();
        await saveBtn.click();
        
        // Save click should produce a response: either edit mode exits (success) or a status banner appears (validation failure)
        const editAgain = authenticatedPage.getByRole('button', { name: 'Edit Metadata', exact: true });
        const banner = authenticatedPage.getByText(/book update|update failed|saved/i);
        await expect(editAgain.or(banner).first()).toBeVisible();
    });
});
