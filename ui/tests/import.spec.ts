import { test, expect } from '@playwright/test';

test.describe('Import & Staging Workflow', () => {
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

		// Mock setup check
		await page.route('**/api/setup', async (route) => {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({ data: { complete: true } })
			});
		});

		// Mock scan progress (initially idle)
		await page.route('**/api/books/import/progress', async (route) => {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({ data: null })
			});
		});

		// Mock batch progress (initially idle)
		await page.route('**/api/books/staged/batch/progress', async (route) => {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({ data: null })
			});
		});

		// Mock staged books page
		await page.route('**/api/books/staged/page*', async (route) => {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({
					data: {
						items: [
							{
								id: 'staged-1',
								title: 'Dune',
								authors: ['Frank Herbert'],
								mediaType: 'EBOOK',
								size: 1024,
								createdAt: new Date().toISOString(),
								authorSuggestions: {},
								selectedAuthorIds: {}
							}
						],
						totalCount: 1,
						page: 0,
						size: 20
					}
				})
			});
		});
	});

	test('should complete the full import to library workflow', async ({ page }) => {
		// 1. Go to Import page
		await page.goto('/import');
		await expect(page.getByRole('heading', { name: 'Ingest Content' })).toBeVisible();

		// 2. Mock a scan initiation
		await page.route('**/api/books/import/scan', async (route) => {
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({ data: { runId: 'run-1' } })
			});
		});

		// 3. Perform a scan
		await page.getByPlaceholder('e.g. /srv/books').fill('/my/books');
		await page.getByRole('button', { name: 'Start Library Scan' }).click();

		// 4. Verify success message and link to staged area
		await expect(page.getByText('Library scan initiated successfully!')).toBeVisible();
		const viewStagedLink = page.getByRole('link', { name: 'View staged books' });
		await expect(viewStagedLink).toBeVisible();

		// 5. Navigate to Staged area
		await viewStagedLink.click();
		await expect(page).toHaveURL(/\/import\/staged/);
		await expect(page.getByText('Dune')).toBeVisible();
		await expect(page.getByText('Frank Herbert')).toBeVisible();

		// 6. Mock promotion
		await page.route('**/api/books/staged/batch', async (route) => {
			expect(route.request().method()).toBe('POST');
			await route.fulfill({
				status: 200,
				contentType: 'application/json',
				body: JSON.stringify({ data: { runId: 'batch-1' } })
			});
		});

		// 7. Select book and promote
		await page.getByRole('checkbox').first().check();
		await page.getByRole('button', { name: 'Add to Library' }).click();

		// 8. Verify success toast or redirection
		// (The UI might show a batch progress or just success)
		// Based on current implementation, it might show a progress bar
	});
});
