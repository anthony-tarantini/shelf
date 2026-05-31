import { test, expect } from './fixtures';

test.describe('Catalog and Search', () => {
    test('should allow searching for books', async ({ authenticatedPage }) => {
        await authenticatedPage.goto('/');

        // Open global search dialog via sidebar trigger
        await authenticatedPage.getByRole('button', { name: /search\.\.\./i }).first().click();

        const searchInput = authenticatedPage.getByPlaceholder(/search books, authors/i);
        await expect(searchInput).toBeVisible();
        await searchInput.fill('test');

        // Wait for search response (debounced 300ms)
        await expect(authenticatedPage.getByText(/test|no results/i).first()).toBeVisible();
    });

    test('should navigate catalog pages', async ({ authenticatedPage }) => {
        await authenticatedPage.goto('/authors');
        await expect(authenticatedPage.getByRole('heading', { name: /authors/i })).toBeVisible();

        await authenticatedPage.goto('/series');
        await expect(authenticatedPage.getByRole('heading', { name: /series/i })).toBeVisible();
    });
});
