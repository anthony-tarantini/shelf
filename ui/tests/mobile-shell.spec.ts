import { test, expect } from '@playwright/test';

test.describe('Mobile Shell', () => {
	test.use({ viewport: { width: 390, height: 844 } });

	test.beforeEach(async ({ page }) => {
		await page.route('**/api/setup', async (route) => {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({ data: { complete: true } })
			});
		});

		await page.route('**/api/users', async (route) => {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({
					data: {
						user: { id: '1', email: 'test@example.com', username: 'testuser', role: 'READER' },
						token: 'fake-jwt-token'
					}
				})
			});
		});

		await page.route('**/api/books/page*', async (route) => {
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

		await page.route('**/api/books/import/progress', async (route) => {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({
					data: {
						isScanning: false,
						scannedFiles: 0,
						importedBooks: 0,
						currentPath: null
					}
				})
			});
		});

		await page.route('**/api/books/staged/batch/progress', async (route) => {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({
					data: {
						total: 0,
						completed: 0,
						failed: 0,
						running: false
					}
				})
			});
		});

		await page.route('**/api/search*', async (route) => {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({
					data: {
						books: [],
						authors: [],
						series: []
					}
				})
			});
		});
	});

	test('shows mobile navigation, drawer tools, and search sheet', async ({ page }) => {
		await page.goto('/');

		await expect(page.getByRole('heading', { name: 'Shelf' })).toBeVisible();
		await expect(page.getByRole('link', { name: 'Library' })).toBeVisible();
		await expect(page.getByRole('button', { name: 'Open navigation' })).toBeVisible();

		await page.getByRole('button', { name: 'Open navigation' }).click();
		await expect(page.getByText('Navigation')).toBeVisible();
		await expect(page.getByRole('link', { name: 'Import' })).toBeVisible();
		await expect(page.getByRole('link', { name: 'KOReader Sync' })).toBeVisible();
		await page.getByRole('complementary').getByRole('button', { name: 'Close' }).click();

		await page.getByRole('button', { name: 'Open search' }).click();
		await expect(page.getByRole('heading', { name: 'Search the catalog' })).toBeVisible();
		await expect(page.getByPlaceholder('Search books, authors, series...')).toBeVisible();
	});
});
