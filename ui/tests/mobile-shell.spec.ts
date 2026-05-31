import { test, expect } from './fixtures';

test.describe('Mobile Shell', () => {
    test.use({ viewport: { width: 390, height: 844 } }); // iPhone/Pixel size

    test('should show bottom navigation and drawer', async ({ authenticatedPage }) => {
        await authenticatedPage.goto('/');

        // The bottom nav should be visible on mobile
        const bottomNav = authenticatedPage.getByRole('navigation').last();
        await expect(bottomNav).toBeVisible();

        // Check if there's a menu button to open drawer
        const menuBtn = authenticatedPage.getByRole('button', { name: /open navigation|menu|drawer/i });
        await expect(menuBtn).toBeVisible();
        await menuBtn.click();
        
        // Drawer should appear
        const drawer = authenticatedPage.locator('[data-testid="mobile-drawer"]');
        await expect(drawer).toBeVisible();

        // Close drawer to test search
        await drawer.getByRole('button', { name: /close/i }).click();
        await expect(drawer).not.toBeVisible();

        // Check for search sheet
        const searchBtn = authenticatedPage.getByRole('button', { name: /search/i }).first();
        await expect(searchBtn).toBeVisible();
        await searchBtn.click();
        
        const searchInput = authenticatedPage.getByPlaceholder(/search/i).or(authenticatedPage.getByRole('searchbox'));
        await expect(searchInput).toBeVisible();
    });
});
