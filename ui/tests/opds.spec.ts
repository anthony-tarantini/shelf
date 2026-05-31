import { test, expect } from './fixtures';

test.describe('OPDS Feeds', () => {
    test('should require authentication', async ({ apiHelper }) => {
        const res = await apiHelper.opdsRequest('/api/opds/v1.2/catalog', {
            headers: { 'Authorization': '' } // Override to be unauthenticated
        });
        expect(res.status).toBe(401);
    });

    test('should serve root catalog via Basic Auth', async ({ apiHelper }) => {
        const res = await apiHelper.opdsRequest('/api/opds/v1.2/catalog');
        expect(res.status).toBe(200);
        
        const text = await res.text();
        expect(text).toContain('<?xml');
        expect(text).toContain('application/atom+xml');
        expect(text).toContain('Shelf');
    });

    test('should serve navigation and acquisition feeds', async ({ apiHelper }) => {
        // Authors feed
        const authorsRes = await apiHelper.opdsRequest('/api/opds/v1.2/authors');
        expect(authorsRes.status).toBe(200);
        expect(await authorsRes.text()).toContain('Authors');

        // Series feed
        const seriesRes = await apiHelper.opdsRequest('/api/opds/v1.2/series');
        expect(seriesRes.status).toBe(200);
        expect(await seriesRes.text()).toContain('Series');

        // Latest/Acquisition feed
        const latestRes = await apiHelper.opdsRequest('/api/opds/v1.2/books');
        expect(latestRes.status).toBe(200);
        expect(await latestRes.text()).toContain('All Books');
    });

    test('should serve search results', async ({ apiHelper }) => {
        const searchRes = await apiHelper.opdsRequest('/api/opds/v1.2/search?q=test');
        expect(searchRes.status).toBe(200);
        expect(await searchRes.text()).toContain('Search results for: test');
    });
});
