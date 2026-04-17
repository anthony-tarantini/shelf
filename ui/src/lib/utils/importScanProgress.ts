import type { ImportScanProgress } from '$lib/types/models';

export const COMPLETED_SCAN_VISIBILITY_MS = 15000;

export function shouldShowScanProgress(
	progress: ImportScanProgress,
	now = Date.now(),
	visibilityMs = COMPLETED_SCAN_VISIBILITY_MS
) {
	if (progress.status === 'RUNNING') return true;
	if (!progress.finishedAt) return true;
	return now - new Date(progress.finishedAt).getTime() < visibilityMs;
}

export function shouldRefreshStagedList(
	previous: ImportScanProgress | null,
	latest: ImportScanProgress | null
) {
	if (!latest) return false;
	if (!previous || previous.runId !== latest.runId) return true;

	const previousProcessed = previous.completedFiles + previous.failedFiles;
	const latestProcessed = latest.completedFiles + latest.failedFiles;

	return (
		previousProcessed !== latestProcessed ||
		previous.status !== latest.status ||
		previous.queuedFiles !== latest.queuedFiles
	);
}

export function getProgressPercent(progress: ImportScanProgress | null) {
	if (!progress) return 0;
	const processedCount = progress.completedFiles + progress.failedFiles;
	if (progress.totalFiles > 0) {
		return Math.min(100, Math.round((processedCount / progress.totalFiles) * 100));
	}
	return progress.status === 'RUNNING' ? 0 : 100;
}

