import { test, expect } from './fixtures';

test.describe('Authentication', () => {
    // These tests use the default `page` which is unauthenticated

    test('should show login page and allow valid login', async ({ page }) => {
        await page.goto('/login');
        await expect(page.getByRole('heading', { name: /welcome back/i, includeHidden: false })).toBeVisible();

        await page.getByLabel(/email/i).fill('admin@example.com');
        await page.getByLabel(/password/i).fill('adminpassword');
        await page.getByRole('button', { name: /login|sign in/i }).click();

        // Should redirect to library or admin depending on backend logic
        await expect(page).toHaveURL(/\/(admin\/users|)$/);
    });

    test('should show error on invalid login', async ({ page }) => {
        await page.goto('/login');
        await page.getByLabel(/email/i).fill('admin@example.com');
        await page.getByLabel(/password/i).fill('wrongpassword');
        await page.getByRole('button', { name: /login|sign in/i }).click();

        await expect(page.locator('.bg-destructive\\/10').first()).toBeVisible();
        await expect(page).toHaveURL(/\/login/);
    });

    test('should redirect unauthenticated to login', async ({ page }) => {
        await page.goto('/admin/library');
        await expect(page).toHaveURL(/\/login/);
    });

    test('should logout successfully', async ({ authenticatedPage }) => {
        await authenticatedPage.goto('/');
        
        // Find logout button (might be in a menu)
        const menuBtn = authenticatedPage.getByRole('button', { name: /user menu|profile|admin/i });
        if (await menuBtn.isVisible()) {
            await menuBtn.click();
        }
        
        const logoutBtn = authenticatedPage.getByRole('button', { name: /logout|log out|sign out/i });
        await expect(logoutBtn).toBeVisible();
        await logoutBtn.click();
        await expect(authenticatedPage).toHaveURL(/\/login/);

        // Robustness: reload and ensure we're still at login (session is cleared)
        await authenticatedPage.reload();
        await expect(authenticatedPage).toHaveURL(/\/login/);
    });
});
