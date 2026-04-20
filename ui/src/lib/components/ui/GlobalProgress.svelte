<script lang="ts">
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { t } from '$lib/i18n';
	import { progress } from '$lib/state/progress.svelte';
	import type { BatchProgress, ImportScanProgress } from '$lib/types/models';

	function scanPercent(p: ImportScanProgress): number {
		const processed = p.completedFiles + p.failedFiles;
		if (p.totalFiles > 0) return Math.min(100, Math.round((processed / p.totalFiles) * 100));
		return p.status === 'RUNNING' ? 0 : 100;
	}

	function batchPercent(p: BatchProgress): number {
		const processed = p.completedItems + p.failedItems;
		if (p.totalItems > 0) return Math.min(100, Math.round((processed / p.totalItems) * 100));
		return p.status === 'RUNNING' ? 0 : 100;
	}

	function batchLabel(p: BatchProgress): string {
		const processed = p.completedItems + p.failedItems;
		if (p.action === 'DELETE') {
			return p.status === 'RUNNING'
				? $t('progress.deleting', { count: processed, total: p.totalItems })
				: $t('progress.delete_complete');
		}
		return p.status === 'RUNNING'
			? $t('progress.promoting', { count: processed, total: p.totalItems })
			: $t('progress.promote_complete');
	}

	function scanLabel(p: ImportScanProgress): string {
		const processed = p.completedFiles + p.failedFiles;
		return p.status === 'RUNNING'
			? $t('progress.importing', { count: processed, total: p.totalFiles })
			: $t('progress.import_complete');
	}

	function navigateToStaging() {
		goto(resolve('/import/staged'));
	}
</script>

{#if progress.visibleImportScan || progress.visibleBatch}
	<div class="px-4 pb-3 space-y-2">
		{#if progress.visibleImportScan}
			{@const scan = progress.visibleImportScan}
			<button
				onclick={navigateToStaging}
				class="w-full rounded-xl border border-primary/25 bg-primary/10 p-3 text-left transition-colors hover:bg-primary/15"
			>
				<div class="flex items-center gap-2 text-xs font-bold text-primary">
					{#if scan.status === 'RUNNING'}
						<span class="relative flex h-2 w-2">
							<span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary opacity-75"></span>
							<span class="relative inline-flex h-2 w-2 rounded-full bg-primary"></span>
						</span>
					{:else if scan.failedFiles > 0}
						<span class="text-amber-500">!</span>
					{:else}
						<span class="text-emerald-500">&#10003;</span>
					{/if}
					{scanLabel(scan)}
				</div>
				<div class="mt-2 h-1.5 overflow-hidden rounded-full bg-primary/15">
					<div
						class="h-full rounded-full bg-primary transition-all duration-500"
						style="width: {scanPercent(scan)}%"
					></div>
				</div>
			</button>
		{/if}

		{#if progress.visibleBatch}
			{@const batch = progress.visibleBatch}
			<button
				onclick={navigateToStaging}
				class="w-full rounded-xl border border-primary/25 bg-primary/10 p-3 text-left transition-colors hover:bg-primary/15"
			>
				<div class="flex items-center gap-2 text-xs font-bold text-primary">
					{#if batch.status === 'RUNNING'}
						<span class="relative flex h-2 w-2">
							<span class="absolute inline-flex h-full w-full animate-ping rounded-full bg-primary opacity-75"></span>
							<span class="relative inline-flex h-2 w-2 rounded-full bg-primary"></span>
						</span>
					{:else if batch.failedItems > 0}
						<span class="text-amber-500">!</span>
					{:else if batch.warningItems > 0}
						<span class="text-amber-500">!</span>
					{:else}
						<span class="text-emerald-500">&#10003;</span>
					{/if}
					{batchLabel(batch)}
				</div>
				<div class="mt-2 h-1.5 overflow-hidden rounded-full bg-primary/15">
					<div
						class="h-full rounded-full bg-primary transition-all duration-500"
						style="width: {batchPercent(batch)}%"
					></div>
				</div>
			</button>
		{/if}
	</div>
{/if}
