import { test, expect } from './fixtures';

test.describe('Library & Books', () => {
    test('should load library page and show books', async ({ authenticatedPage }) => {
        await authenticatedPage.goto('/');
        
        // Wait for network requests or content to load
        await expect(authenticatedPage.getByRole('main')).toBeVisible();
        
        const emptyState = authenticatedPage.getByText(/no books found/i);
        const bookList = authenticatedPage.locator('[data-testid="book-card"]').first();

        await expect(emptyState.or(bookList)).toBeVisible();
        
        await expect(authenticatedPage.getByRole('heading', { name: /library/i, includeHidden: false }).first()).toBeVisible();
    });

    test('should navigate to authors catalog', async ({ authenticatedPage }) => {
        await authenticatedPage.goto('/authors');
        await expect(authenticatedPage.getByRole('heading', { name: /authors/i, includeHidden: false }).first()).toBeVisible();
    });

    test('should navigate to series catalog', async ({ authenticatedPage }) => {
        await authenticatedPage.goto('/series');
        await expect(authenticatedPage.getByRole('heading', { name: /series/i, includeHidden: false }).first()).toBeVisible();
    });
});
