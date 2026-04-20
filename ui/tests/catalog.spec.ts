import { test, expect } from '@playwright/test';

test.describe('Catalog & Search', () => {
	test.beforeEach(async ({ page }) => {
		// Mock logged in user
		await page.route('**/api/users', async (route) => {
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

		// Mock search results
		await page.route('**/api/catalog/search*', async (route) => {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({
					data: {
						books: [{ id: 'book-1', title: 'Search Result Book', authorNames: ['Test Author'] }],
						authors: [{ id: 'auth-1', name: 'Test Author', bookCount: 1 }],
						series: [{ id: 'series-1', name: 'Test Series', bookCount: 1 }]
					}
				})
			});
		});
	});

	test('should perform a global search', async ({ page }) => {
		await page.goto('/');
		
		// Open search (assuming it's accessible via a button or keyboard shortcut)
		// Based on GlobalSearch.svelte, it might have a trigger
		await page.getByPlaceholder('Search books, authors, series...').fill('test');
		
		// Wait for search results to appear
		await expect(page.getByText('Search Result Book')).toBeVisible();
		await expect(page.getByText('Test Author')).toBeVisible();
		await expect(page.getByText('Test Series')).toBeVisible();
	});

	test('should navigate to authors and series pages', async ({ page }) => {
		// Mock authors page
		await page.route('**/api/authors/page*', async (route) => {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({
					data: { items: [{ id: 'auth-1', name: ' Isaac Asimov', bookCount: 10 }], totalCount: 1, page: 0, size: 20 }
				})
			});
		});

		// Mock series page
		await page.route('**/api/series/page*', async (route) => {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({
					data: { items: [{ id: 'series-1', name: 'Foundation', bookCount: 7 }], totalCount: 1, page: 0, size: 20 }
				})
			});
		});

		await page.goto('/authors');
		await expect(page.getByRole('heading', { name: 'Authors' })).toBeVisible();
		await expect(page.getByText('Isaac Asimov')).toBeVisible();

		await page.goto('/series');
		await expect(page.getByRole('heading', { name: 'Series' })).toBeVisible();
		await expect(page.getByText('Foundation')).toBeVisible();
	});
});
