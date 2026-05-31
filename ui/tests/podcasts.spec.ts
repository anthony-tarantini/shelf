import { test, expect } from './fixtures';

test.describe('Podcasts', () => {
    test('should allow subscribing to a podcast, list it, and show details', async ({ authenticatedPage }) => {
        await authenticatedPage.goto('/podcasts');
        
        const emptyStateOrList = authenticatedPage.locator('[data-testid="podcasts-empty"], [data-testid="podcast-card"]');
        await expect(emptyStateOrList.first()).toBeVisible({ timeout: 10000 });

        // Navigate to subscribe page
        const subscribeLink = authenticatedPage.getByRole('link', { name: /subscribe|add/i });
        await expect(subscribeLink).toBeVisible();
        await subscribeLink.click();
        await expect(authenticatedPage).toHaveURL(/\/podcasts\/subscribe/);

        // Fill URL, series, and submit
        await authenticatedPage.getByLabel(/url|rss/i).fill('http://fixtures:80/test-podcast.xml');
        await authenticatedPage.getByLabel(/new series|create series/i).fill('Test Podcast');
        await authenticatedPage.getByRole('button', { name: /subscribe|add/i }).click();

        // Should show success state, not redirect
        await expect(authenticatedPage.getByText(/subscribed/i).first()).toBeVisible();
        
        // Go back to podcasts list
        const backBtn = authenticatedPage.getByRole('link', { name: /back/i }).first();
        await expect(backBtn).toBeVisible();
        await backBtn.click();
        await expect(authenticatedPage).toHaveURL(/\/podcasts$/);

        // Click on the created podcast
        const podcastLink = authenticatedPage.getByRole('link', { name: /test podcast/i }).first();
        await expect(podcastLink).toBeVisible();
        await podcastLink.click();
        
        await expect(authenticatedPage.getByRole('heading', { name: /test podcast/i }).first()).toBeVisible();
    });

    test('should allow editing podcast settings', async ({ authenticatedPage, apiHelper }) => {
        // Seed a podcast to ensure it exists for the settings test
        await apiHelper.seedPodcast();
        
        await authenticatedPage.goto('/podcasts');
        const podcastLink = authenticatedPage.getByRole('link', { name: /test podcast/i }).first();
        await expect(podcastLink).toBeVisible();
        await podcastLink.click();
        
        const settingsLink = authenticatedPage.getByRole('link', { name: /settings/i });
        await expect(settingsLink).toBeVisible();
        await settingsLink.click();
        await expect(authenticatedPage).toHaveURL(/\/settings/);
        await expect(authenticatedPage.getByRole('heading', { name: /settings/i })).toBeVisible();
    });
});
