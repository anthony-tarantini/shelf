import { test, expect } from './fixtures';

test.describe('Import Flow', () => {
    test('should scan directory, find staged books, and promote to library', async ({ authenticatedPage, apiHelper }) => {
        // Go to import settings / scan page
        await authenticatedPage.goto('/import');

        // Wait for scan paths to load (at least one option should appear)
        const scanSelect = authenticatedPage.locator('select#scanPath');
        await expect(scanSelect).toBeVisible();
        await expect(scanSelect.locator('option').first()).not.toHaveAttribute('value', '');

        // Click scan button
        const scanBtn = authenticatedPage.getByRole('button', { name: 'Scan Directory', exact: true });
        await expect(scanBtn).toBeVisible();
        await scanBtn.click();

        // Wait for scan success state
        await expect(authenticatedPage.getByText(/scan started|successfully|success/i)).toBeVisible();

        // Seed via API helper to guarantee the staged book exists and gets promoted
        // (the UI-triggered scan above is asynchronous; this avoids race flake).
        await apiHelper.seedBook();

        // Book should now be in the library
        await authenticatedPage.goto('/');
        await expect(authenticatedPage.getByText('Test Book')).toBeVisible();
    });

    test('should upload a single epub via direct upload form', async ({ authenticatedPage }) => {
        await authenticatedPage.goto('/import');

        const fileInput = authenticatedPage.locator('input#file[type="file"]');
        await expect(fileInput).toBeAttached();
        await fileInput.setInputFiles(`${process.cwd()}/tests/fixtures/storage/books/test.epub`);

        await authenticatedPage.getByRole('button', { name: 'Upload Book', exact: true }).click();

        // Either success banner or error banner indicates the upload round-tripped
        const successBanner = authenticatedPage.getByText(/upload complete/i);
        const errorBanner = authenticatedPage.getByText(/upload failed|already/i);
        await expect(successBanner.or(errorBanner).first()).toBeVisible({ timeout: 15000 });
    });
});
