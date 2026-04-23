import { test, expect } from '@playwright/test';

test.describe('Podcasts', () => {
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

        // Mock podcasts list
        await page.route('**/api/podcasts', async route => {
            if (route.request().method() === 'GET') {
                await route.fulfill({
                    status: 200,
                    contentType: 'application/json',
                    body: JSON.stringify({
                        data: [
                            {
                                id: 'pod-1',
                                seriesId: 'ser-1',
                                seriesTitle: 'Daily Tech',
                                feedUrl: 'https://example.com/tech',
                                episodeCount: 42,
                                autoSanitize: true,
                                autoFetch: true,
                                lastFetchedAt: '2026-04-22T10:00:00Z',
                                version: 1
                            }
                        ]
                    })
                });
            } else if (route.request().method() === 'POST') {
                await route.fulfill({
                    status: 201,
                    contentType: 'application/json',
                    body: JSON.stringify({
                        data: {
                            id: 'pod-new',
                            seriesId: 'ser-new',
                            feedUrl: 'https://example.com/new',
                            feedToken: 'new-token'
                        }
                    })
                });
            }
        });

        // Mock podcast aggregate
        await page.route('**/api/podcasts/pod-1', async route => {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    data: {
                        podcast: {
                            id: 'pod-1',
                            seriesId: 'ser-1',
                            feedUrl: 'https://example.com/tech',
                            feedToken: 'token-123',
                            autoSanitize: true,
                            autoFetch: true,
                            fetchIntervalMinutes: 60,
                            version: 1
                        },
                        seriesId: 'ser-1',
                        seriesTitle: 'Daily Tech',
                        episodes: [
                            {
                                bookId: 'ep-1',
                                title: 'Ep 1: The Future',
                                season: 1,
                                episode: 1,
                                totalTime: 3600,
                                publishedAt: '2026-04-22T08:00:00Z'
                            }
                        ],
                        credential: 'NO_CREDENTIAL'
                    }
                })
            });
        });

        // Mock series search for subscription
        await page.route('**/api/series/search?q=*', async route => {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    data: [
                        { id: 'ser-1', title: 'Daily Tech' }
                    ]
                })
            });
        });

        // Mock initial series for subscribe page
        await page.route('**/api/series', async route => {
            await route.fulfill({
                status: 200,
                contentType: 'application/json',
                body: JSON.stringify({
                    data: [
                        { id: 'ser-1', name: 'Daily Tech' }
                    ]
                })
            });
        });
    });

    test('should show podcast list and navigate to details', async ({ page }) => {
        await page.goto('/podcasts');
        
        await expect(page.getByRole('heading', { name: 'Daily Tech' })).toBeVisible();
        await expect(page.getByText('42 episodes')).toBeVisible();

        await page.getByRole('heading', { name: 'Daily Tech' }).click();

        await expect(page).toHaveURL(/\/podcasts\/pod-1/);
        await expect(page.getByRole('heading', { name: 'Daily Tech' })).toBeVisible();
        await expect(page.getByText('Ep 1: The Future')).toBeVisible();
    });

    test('should allow subscribing to a new podcast', async ({ page }) => {
        await page.goto('/podcasts');
        
        const subscribeLink = page.locator('a[href="/podcasts/subscribe"]').first();
        await subscribeLink.click();

        await expect(page).toHaveURL('/podcasts/subscribe');

        await page.getByLabel('Feed URL').fill('https://example.com/new');
        
        // Search for series
        await page.getByPlaceholder('Search series...').fill('Daily');
        await page.getByRole('button', { name: 'Daily Tech' }).click();

        // Submit form using the primary action button
        await page.locator('form button[type="submit"]').click();

        await expect(page.getByText('Podcast subscription created')).toBeVisible();
        await page.getByRole('link', { name: 'Back to Podcasts' }).last().click();
        await expect(page).toHaveURL('/podcasts');
    });

    test('should manage podcast settings', async ({ page }) => {
        await page.goto('/podcasts/pod-1/settings');

        await expect(page.getByText('token-123')).toBeVisible();

        // Test configuration change
        const intervalInput = page.getByLabel('Fetch interval (minutes)');
        await intervalInput.fill('120');

        // Mock successful save
        await page.route('**/api/podcasts/pod-1', async route => {
            if (route.request().method() === 'PUT') {
                await route.fulfill({
                    status: 200,
                    contentType: 'application/json',
                    body: JSON.stringify({
                        data: {
                            id: 'pod-1',
                            autoFetch: true,
                            autoSanitize: true,
                            fetchIntervalMinutes: 120
                        }
                    })
                });
            }
        });

        await page.getByRole('button', { name: 'Save Settings' }).click();
        await expect(page.getByText('Settings saved successfully')).toBeVisible();
    });
});
