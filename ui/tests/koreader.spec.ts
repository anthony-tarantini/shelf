import { test, expect } from './fixtures';

test.describe('KOReader Sync Settings', () => {
    test('should show connection URLs and manage API tokens', async ({ authenticatedPage }) => {
        await authenticatedPage.goto('/settings/koreader');
        
        // Assert setup instructions and URLs are visible
        await expect(authenticatedPage.getByText('Sync Server URL', { exact: true })).toBeVisible();
        await expect(authenticatedPage.getByText('WebDAV URL (Reading Stats)', { exact: true })).toBeVisible();

        // Create a new token
        const descInput = authenticatedPage.locator('input[type="text"]').last();
        await expect(descInput).toBeVisible();
        await descInput.fill('test-token');
        
        await authenticatedPage.getByRole('button', { name: /generate|create/i }).click();
        
        // Wait for token creation response and modal/display
        const tokenDisplay = authenticatedPage.locator('[data-testid="new-token-value"]');
        await expect(tokenDisplay).toBeVisible();

        // Close modal if there is one
        const closeBtn = authenticatedPage.getByRole('button', { name: /close|done/i });
        if (await closeBtn.isVisible()) {
            await closeBtn.click();
        }

        // Verify token in list
        const tokensList = authenticatedPage.getByRole('list').last();
        await expect(tokensList).toBeVisible();

        // Delete token
        const deleteBtn = authenticatedPage.getByRole('button', { name: /delete|revoke/i }).first();
        await expect(deleteBtn).toBeVisible();
        await deleteBtn.click();
        
        const confirmBtn = authenticatedPage.getByRole('button', { name: /confirm|yes|delete/i });
        if (await confirmBtn.isVisible()) {
            await confirmBtn.click();
        }
    });
});
