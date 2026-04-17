import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ApiClient } from './client';

describe('ApiClient', () => {
    let client: ApiClient;
    const mockFetch = vi.fn();

    beforeEach(() => {
        vi.clearAllMocks();
        client = new ApiClient('/api');
    });

    it('should include Authorization header when token is set', async () => {
        client.setToken('test-token');
        mockFetch.mockResolvedValueOnce({
            ok: true,
            status: 200,
            json: async () => ({ data: { success: true } })
        });

        await client.get('/test', mockFetch);

        expect(mockFetch).toHaveBeenCalledWith(
            expect.stringContaining('/api/test'),
            expect.objectContaining({
                headers: expect.objectContaining({
                    'Authorization': 'Bearer test-token'
                })
            })
        );
    });

    it('should wrap body in "data" key for POST requests', async () => {
        mockFetch.mockResolvedValueOnce({
            ok: true,
            status: 200,
            json: async () => ({ data: {} })
        });

        const body = { title: 'New Book' };
        await client.post('/books', body, mockFetch);

        expect(mockFetch).toHaveBeenCalledWith(
            expect.any(String),
            expect.objectContaining({
                method: 'POST',
                body: JSON.stringify({ data: body })
            })
        );
    });

    it('should return left on network error', async () => {
        mockFetch.mockRejectedValueOnce(new Error('Network failure'));

        const result = await client.get('/error', mockFetch);

        expect(result.left).toBeDefined();
        expect(result.left?.type).toBe('NetworkError');
    });

    it('should return left on non-ok response', async () => {
        mockFetch.mockResolvedValueOnce({
            ok: false,
            status: 400,
            json: async () => ({ type: 'ValidationError', message: 'Invalid data' })
        });

        const result = await client.get('/bad-request', mockFetch);

        expect(result.left).toBeDefined();
        expect(result.left?.status).toBe(400);
        expect(result.left?.type).toBe('ValidationError');
    });
});
