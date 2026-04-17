import { api } from '$lib/api/client';
import type { BatchProgress, ImportScanProgress } from '$lib/types/models';
import { COMPLETED_SCAN_VISIBILITY_MS, shouldShowScanProgress } from '$lib/utils/importScanProgress';

const POLL_INTERVAL_MS = 1500;

function shouldShowBatchProgress(
	progress: BatchProgress,
	now = Date.now(),
	visibilityMs = COMPLETED_SCAN_VISIBILITY_MS
): boolean {
	if (progress.status === 'RUNNING') return true;
	if (!progress.finishedAt) return true;
	return now - new Date(progress.finishedAt).getTime() < visibilityMs;
}

class ProgressState {
	private _importScan = $state<ImportScanProgress | null>(null);
	private _batch = $state<BatchProgress | null>(null);
	private _pollTimer: ReturnType<typeof setInterval> | null = null;
	private _polling = false;

	get importScan() {
		return this._importScan;
	}

	get batch() {
		return this._batch;
	}

	get visibleImportScan(): ImportScanProgress | null {
		return this._importScan && shouldShowScanProgress(this._importScan) ? this._importScan : null;
	}

	get visibleBatch(): BatchProgress | null {
		return this._batch && shouldShowBatchProgress(this._batch) ? this._batch : null;
	}

	get hasVisibleProgress(): boolean {
		return this.visibleImportScan !== null || this.visibleBatch !== null;
	}

	startPolling() {
		if (typeof window === 'undefined') return;
		if (this._pollTimer) return;
		this.poll();
		this._pollTimer = setInterval(() => this.poll(), POLL_INTERVAL_MS);
	}

	stopPolling() {
		if (this._pollTimer) {
			clearInterval(this._pollTimer);
			this._pollTimer = null;
		}
	}

	ensurePolling() {
		this.startPolling();
	}

	private async poll() {
		if (this._polling) return;
		this._polling = true;

		try {
			const [scanResult, batchResult] = await Promise.all([
				api.get<ImportScanProgress | null>('/books/import/progress'),
				api.get<BatchProgress | null>('/books/staged/batch/progress')
			]);

			if (!scanResult.left) this._importScan = scanResult.right ?? null;
			if (!batchResult.left) this._batch = batchResult.right ?? null;

			if (!this.hasVisibleProgress) {
				this.stopPolling();
			}
		} finally {
			this._polling = false;
		}
	}
}

export const progress = new ProgressState();
