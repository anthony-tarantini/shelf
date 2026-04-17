import { describe, expect, it } from 'vitest';
import type { ImportScanProgress } from '$lib/types/models';
import {
	COMPLETED_SCAN_VISIBILITY_MS,
	getProgressPercent,
	shouldRefreshStagedList,
	shouldShowScanProgress
} from './importScanProgress';

const baseProgress: ImportScanProgress = {
	runId: 'run-1',
	status: 'RUNNING',
	sourcePath: '/srv/books',
	totalFiles: 10,
	queuedFiles: 10,
	completedFiles: 2,
	failedFiles: 1,
	failedFileDetails: [],
	startedAt: '2026-04-13T12:00:00.000Z',
	finishedAt: null
};

describe('importScanProgress', () => {
	it('shows running scans immediately', () => {
		expect(shouldShowScanProgress(baseProgress)).toBe(true);
	});

	it('keeps completed scans visible during the grace period only', () => {
		const finishedAt = '2026-04-13T12:00:00.000Z';
		const completed: ImportScanProgress = {
			...baseProgress,
			status: 'COMPLETED',
			finishedAt
		};

		const finishedAtMs = new Date(finishedAt).getTime();
		expect(
			shouldShowScanProgress(completed, finishedAtMs + COMPLETED_SCAN_VISIBILITY_MS - 1)
		).toBe(true);
		expect(
			shouldShowScanProgress(completed, finishedAtMs + COMPLETED_SCAN_VISIBILITY_MS + 1)
		).toBe(false);
	});

	it('refreshes when processed counts advance or status changes', () => {
		const previous = { ...baseProgress };
		const latest = { ...baseProgress, completedFiles: 3 };
		const failed = { ...baseProgress, status: 'FAILED' as const };

		expect(shouldRefreshStagedList(previous, latest)).toBe(true);
		expect(shouldRefreshStagedList(previous, failed)).toBe(true);
	});

	it('refreshes when a new run replaces the previous run', () => {
		const previous = { ...baseProgress };
		const latest = { ...baseProgress, runId: 'run-2' };

		expect(shouldRefreshStagedList(previous, latest)).toBe(true);
	});

	it('does not refresh when progress has not materially changed', () => {
		const previous = { ...baseProgress };
		const latest = { ...baseProgress };

		expect(shouldRefreshStagedList(previous, latest)).toBe(false);
	});

	it('calculates progress percentage using completed and failed files', () => {
		expect(getProgressPercent(baseProgress)).toBe(30);
		expect(getProgressPercent({ ...baseProgress, totalFiles: 0, status: 'RUNNING' })).toBe(0);
		expect(getProgressPercent({ ...baseProgress, totalFiles: 0, status: 'COMPLETED' })).toBe(100);
		expect(getProgressPercent(null)).toBe(0);
	});
});
