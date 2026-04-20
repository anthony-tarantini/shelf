import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ProgressState } from './progress.svelte';
import { api } from '$lib/api/client';

// Mock the api client
vi.mock('$lib/api/client', () => ({
	api: {
		get: vi.fn()
	}
}));

// Mock the utils since they might have complex logic or dependencies
vi.mock('$lib/utils/importScanProgress', () => ({
	shouldShowScanProgress: vi.fn(() => true),
	COMPLETED_SCAN_VISIBILITY_MS: 5000
}));

describe('ProgressState', () => {
	let progress: ProgressState;

	beforeEach(() => {
		vi.resetAllMocks();
		vi.useFakeTimers();
		progress = new ProgressState();
	});

	it('should start with null progress', () => {
		expect(progress.importScan).toBeNull();
		expect(progress.batch).toBeNull();
		expect(progress.hasVisibleProgress).toBe(false);
	});

	it('should poll for progress when started', async () => {
		const mockScan = { status: 'RUNNING', totalFiles: 10, completedFiles: 5 };
		const mockBatch = { status: 'COMPLETED', totalItems: 10, completedItems: 10, finishedAt: new Date().toISOString() };

		vi.mocked(api.get).mockImplementation(async (path) => {
			if (path === '/books/import/progress') return { right: mockScan };
			if (path === '/books/staged/batch/progress') return { right: mockBatch };
			return { right: null };
		});

		progress.startPolling();
		
		// Wait for state to be updated
		await vi.waitFor(() => {
			expect(progress.importScan).toEqual(mockScan);
			expect(progress.batch).toEqual(mockBatch);
		});
	});

	it('should stop polling when no visible progress remains', async () => {
		// Mock scan that is completed
		const mockScan = { status: 'COMPLETED', finishedAt: new Date(Date.now() - 10000).toISOString() };
		const mockBatch = null;

		// First poll returns completed scan (still visible maybe? depends on mock)
		vi.mocked(api.get).mockResolvedValueOnce({ right: mockScan });
		vi.mocked(api.get).mockResolvedValueOnce({ right: mockBatch });

		// We need to mock shouldShowScanProgress to return false to trigger stopPolling
		const { shouldShowScanProgress } = await import('$lib/utils/importScanProgress');
		vi.mocked(shouldShowScanProgress).mockReturnValue(false);

		progress.startPolling();
		
		await vi.waitFor(() => {
			expect(api.get).toHaveBeenCalledTimes(2); // One call for each endpoint
		});

		expect(progress.hasVisibleProgress).toBe(false);
		
		// Advance time and check that no more calls are made
		vi.advanceTimersByTime(2000);
		expect(api.get).toHaveBeenCalledTimes(2); 
	});

	it('should handle API errors gracefully', async () => {
		vi.mocked(api.get).mockResolvedValue({ left: { type: 'Error', message: 'Failed' } });

		progress.startPolling();
		
		await vi.waitFor(() => {
			expect(api.get).toHaveBeenCalled();
		});

		expect(progress.importScan).toBeNull();
		expect(progress.batch).toBeNull();
	});
});
