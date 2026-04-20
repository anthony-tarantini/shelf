import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
    test.beforeEach(async ({ page }) => {
        // Mock the initial user check that happens on every page load
        await page.route('**/api/users', async route => {
            await route.fulfill({
                status: 401,
                contentType: 'application/json',
                body: JSON.stringify({ type: 'Unauthorized', message: 'Not logged in' })
            });
        });
    });

    test('should show login page', async ({ page }) => {
        await page.goto('/login');
        await expect(page.getByRole('heading', { name: /welcome back/i })).toBeVisible();
    });

    test('should allow user to log in', async ({ page }) => {
        // Mock the login API
        await page.route('**/api/users/login', async route => {
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

        // Mock the user check AFTER login
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

        await page.goto('/login');
        await page.getByLabel(/email/i).fill('test@example.com');
        await page.getByLabel(/password/i).fill('password123');
        await page.getByRole('button', { name: /sign in/i }).click();

        // Should redirect to home
        await expect(page).toHaveURL('/');
    });
});
