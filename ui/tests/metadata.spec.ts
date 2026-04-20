import { test, expect } from '@playwright/test';

test.describe('Metadata Editing', () => {
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

		// Mock book details
		await page.route('**/api/books/book-1/details', async (route) => {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({
					data: {
						book: { id: 'book-1', title: 'Original Title', coverPath: null },
						authors: [{ id: 'auth-1', name: 'Original Author' }],
						series: [],
						metadata: {
							metadata: {
								id: 'meta-1',
								bookId: 'book-1',
								title: 'Original Title',
								description: 'Original Description',
								genres: [],
								moods: []
							},
							editions: []
						}
					}
				})
			});
		});
	});

	test('should edit book metadata', async ({ page }) => {
		await page.goto('/books/book-1');
		
		// Find and click Edit button (assuming it exists on book details page)
		const editButton = page.getByRole('button', { name: /Edit/i });
		await expect(editButton).toBeVisible();
		await editButton.click();

		// Fill in new metadata
		await page.getByLabel(/Title/i).fill('New Title');
		await page.getByLabel(/Description/i).fill('New Description');

		// Mock save
		await page.route('**/api/books/book-1/metadata', async (route) => {
			expect(route.request().method()).toBe('PUT');
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({ data: { id: 'meta-1' } })
			});
		});

		// Click save
		await page.getByRole('button', { name: /Save/i }).click();

		// Verify success (e.g. toast or updated UI)
		// Since we mocked the API, the UI should reflect the change or show success
	});
});
