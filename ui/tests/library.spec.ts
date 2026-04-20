import { test, expect } from '@playwright/test';

test.describe('Library', () => {
    test.beforeEach(async ({ page }) => {
        // Mock logged in user
        await page.route('**/api/users', async route => {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    data: {
                        user: { id: '1', email: 'test@example.com', username: 'testuser' },
                        token: 'fake-jwt-token'
                    }
                })
            });
        });

        // Mock books page
        await page.route('**/api/books/page*', async route => {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    data: {
                        items: [
                            {
                                book: { id: 'book-1', title: 'Foundation', coverPath: null },
                                authors: [{ id: 'auth-1', name: 'Isaac Asimov' }],
                                series: [],
                                metadata: null
                            }
                        ],
                        totalCount: 1,
                        page: 0,
                        size: 20
                    }
                })
            });
        });

        // Mock book details
        await page.route('**/api/books/book-1/details', async route => {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    data: {
                        book: { id: 'book-1', title: 'Foundation', coverPath: null },
                        authors: [{ id: 'auth-1', name: 'Isaac Asimov' }],
                        series: [],
                        metadata: {
                            metadata: {
                                id: 'meta-1',
                                bookId: 'book-1',
                                title: 'Foundation',
                                description: 'The galactic empire is falling...',
                                genres: ['Sci-Fi'],
                                moods: []
                            },
                            editions: []
                        }
                    }
                })
            });
        });
    });

    test('should show books in library and navigate to details', async ({ page }) => {
        await page.goto('/');
        
        // Check if book is visible
        const bookTitle = page.getByRole('heading', { name: 'Foundation' });
        await expect(bookTitle).toBeVisible();
        await expect(page.getByText('Isaac Asimov')).toBeVisible();

        // Click on the book title/link
        await bookTitle.click();

        // Should be on details page
        await expect(page).toHaveURL(/\/books\/book-1/);
        await expect(page.getByText('The galactic empire is falling...')).toBeVisible();
    });
});
