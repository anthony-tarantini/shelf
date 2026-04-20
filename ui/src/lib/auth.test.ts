import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AuthState } from './auth.svelte';
import { api } from './api/client';
import { UserRole} from "$lib/types/models";

vi.mock('./api/client', () => ({
	api: {
		get: vi.fn(() => Promise.resolve({ right: { complete: true } })),
		setToken: vi.fn(),
		logout: vi.fn()
	}
}));

describe('AuthState', () => {
	beforeEach(() => {
		vi.resetAllMocks();
		vi.mocked(api.get).mockResolvedValue({ right: { complete: true } });
	});

	it('should initialize as not authenticated if setup is complete and user fetch fails', async () => {
		vi.mocked(api.get).mockResolvedValueOnce({ right: { complete: true } }); // setup check
		vi.mocked(api.get).mockResolvedValueOnce({ left: { type: 'Unauthorized', message: 'Not logged in' } }); // user check

		const auth = new AuthState();
		await vi.waitFor(() => {
			expect(auth.isInitialized).toBe(true);
		});

		expect(auth.currentUser).toBeNull();
		expect(auth.isSetupRequired).toBe(false);
		expect(api.logout).toHaveBeenCalled();
	});

	it('should initialize as authenticated if user fetch succeeds', async () => {
		const mockUser = { id: '1', email: 'test@example.com', username: 'testuser' };
		vi.mocked(api.get).mockResolvedValueOnce({ right: { complete: true } });
		vi.mocked(api.get).mockResolvedValueOnce({ right: { user: mockUser, token: 'fake-token' } });

		const auth = new AuthState();
		await vi.waitFor(() => {
			expect(auth.isInitialized).toBe(true);
		});

		expect(auth.currentUser).toEqual(mockUser);
		expect(api.setToken).toHaveBeenCalledWith('fake-token');
	});

	it('should require setup if setup status is false', async () => {
		vi.mocked(api.get).mockResolvedValueOnce({ right: { complete: false } });

		const auth = new AuthState();
		await vi.waitFor(() => {
			expect(auth.isInitialized).toBe(true);
		});

		expect(auth.isSetupRequired).toBe(true);
		expect(auth.currentUser).toBeNull();
	});

	it('should update user and token on setUser', () => {
		const auth = new AuthState();
		const mockUser = { id: '2', email: 'other@example.com', username: 'other', role: UserRole.USER };
		
		auth.setUser(mockUser);
		expect(auth.currentUser).toEqual(mockUser);
		expect(auth.isSetupRequired).toBe(false);
	});

	it('should clear user on logout', () => {
		const auth = new AuthState();
		auth.setUser({ id: '1' } as any);
		
		auth.logout();
		expect(auth.currentUser).toBeNull();
		expect(api.logout).toHaveBeenCalled();
	});
});
