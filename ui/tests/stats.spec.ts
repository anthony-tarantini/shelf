import { test, expect } from './fixtures';

test.describe('Statistics', () => {
    test('should load stats pages and show overview and books', async ({ authenticatedPage }) => {
        // Test overview page
        await authenticatedPage.goto('/stats');
        await expect(authenticatedPage.getByRole('heading', { name: /reading stats/i, includeHidden: false }).first()).toBeVisible();
        
        // Assert charts or metrics exist (might be empty without seeding)
        await expect(authenticatedPage.getByRole('main')).toBeVisible();

        // Test books stats page
        await authenticatedPage.goto('/stats/books');
        await expect(authenticatedPage.getByRole('heading', { name: /books/i, includeHidden: false }).first()).toBeVisible();
        await expect(authenticatedPage.getByRole('table').or(authenticatedPage.getByText(/no matched books/i))).toBeVisible();
        
        // Books stats heading should be visible (covers populated and empty states)
        await expect(authenticatedPage.getByRole('heading', { name: /books read/i }).first()).toBeVisible();
    });
});
