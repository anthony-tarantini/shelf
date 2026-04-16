<script lang="ts">
	import type { ImportScanProgress, StagedBookPage } from '$lib/types/models';
	import StagedBookItem from '$lib/components/StagedBookItem.svelte';
	import StagedBookTable from '$lib/components/StagedBookTable.svelte';
	import { api } from '$lib/api/client';

	import { t } from '$lib/i18n';
	import Pagination from '$lib/components/ui/Pagination.svelte';
	import BatchActionBar from '$lib/components/ui/BatchActionBar.svelte';
	import EmptyState from '$lib/components/ui/EmptyState/EmptyState.svelte';
	import ConfirmDialog from '$lib/components/ui/ConfirmDialog.svelte';
	import {
		getProgressPercent,
		shouldShowScanProgress
	} from '$lib/utils/importScanProgress';
	import { progress } from '$lib/state/progress.svelte';

	let { data } = $props();

	let lastRefresh = $state(0);
	let remotePageData = $state<StagedBookPage | null>(null);
	let pageData = $derived<StagedBookPage>(remotePageData || data.stagedPage);

	let scanProgress = $derived(progress.importScan);
	let batchProgress = $derived(progress.batch);

	let viewMode = $state<'list' | 'table'>('table');
	let selectedIds = $state(new Set<string>());
	let sortBy = $state('createdAt');
	let sortDir = $state<'ASC' | 'DESC'>('DESC');
	let currentPage = $state(0);
	let pageSize = $state(20);
	let authorFilter = $state('');
	let isProcessingBatch = $state(false);
	let isLoading = $state(false);
	let batchConfirm = $state<{ action: 'DELETE' | 'PROMOTE_ALL'; title: string; message: string } | null>(null);
	let showFailedDetails = $state(false);
	let showBatchFailedDetails = $state(false);
	let showBatchWarningDetails = $state(false);

	let visibleScanProgress = $derived.by(() =>
		scanProgress && shouldShowScanProgress(scanProgress) ? scanProgress : null
	);
	let processedCount = $derived(
		(visibleScanProgress?.completedFiles ?? 0) + (visibleScanProgress?.failedFiles ?? 0)
	);
	let progressPercent = $derived(getProgressPercent(visibleScanProgress));

	let visibleBatchProgress = $derived(progress.visibleBatch);
	let batchProcessedCount = $derived(
		(visibleBatchProgress?.completedItems ?? 0) + (visibleBatchProgress?.failedItems ?? 0)
	);
	let batchPercent = $derived.by(() => {
		const p = visibleBatchProgress;
		if (!p) return 0;
		const processed = p.completedItems + p.failedItems;
		if (p.totalItems > 0) return Math.min(100, Math.round((processed / p.totalItems) * 100));
		return p.status === 'RUNNING' ? 0 : 100;
	});

	$effect(() => {
		progress.ensurePolling();
	});

	$effect(() => {
		void lastRefresh;
		refreshData(currentPage, pageSize, sortBy, sortDir, authorFilter);
	});

	$effect(() => {
		// Reset page on filter change
		void authorFilter;
		currentPage = 0;
	});

	// Watch global progress for changes that should refresh the staged list
	let prevScanProcessed = $state(0);
	let prevBatchProcessed = $state(0);
	$effect(() => {
		const scanProcessed = (scanProgress?.completedFiles ?? 0) + (scanProgress?.failedFiles ?? 0);
		const batchProcessed = (batchProgress?.completedItems ?? 0) + (batchProgress?.failedItems ?? 0);

		if (scanProcessed !== prevScanProcessed || batchProcessed !== prevBatchProcessed) {
			prevScanProcessed = scanProcessed;
			prevBatchProcessed = batchProcessed;
			refreshData(currentPage, pageSize, sortBy, sortDir, authorFilter, { silent: true });
		}
	});

	async function refreshData(
		p: number,
		s: number,
		sort: string,
		dir: string,
		author: string,
		options: { silent?: boolean } = {}
	) {
		if (!options.silent) {
			isLoading = true;
		}
		let url = `/books/staged?page=${p}&size=${s}&sortBy=${sort}&sortDir=${dir}`;
		if (author) url += `&author=${encodeURIComponent(author)}`;
		const result = await api.get<StagedBookPage>(url);
		if (result.right) {
			remotePageData = result.right;
		}
		if (!options.silent) {
			isLoading = false;
		}
	}

	function handleActionSuccess() {
		lastRefresh++;
	}
	function progressLabel(progress: ImportScanProgress) {
		if (progress.status === 'RUNNING') return $t('import.progress.status_running');
		if (progress.status === 'FAILED') return $t('import.progress.status_failed');
		if (progress.failedFiles > 0) return $t('import.progress.status_completed_with_failures');
		return $t('import.progress.status_completed');
	}

	async function handleBatchAction(actionId: string) {
		const action = actionId as 'PROMOTE' | 'DELETE' | 'PROMOTE_ALL';
		if (action !== 'PROMOTE_ALL' && selectedIds.size === 0) return;
		
		if (action === 'DELETE') {
			batchConfirm = {
				action,
				title:
					selectedIds.size === 1
						? $t('import.staged.delete_selected_title_one', { count: selectedIds.size })
						: $t('import.staged.delete_selected_title_other', { count: selectedIds.size }),
				message: $t('import.staged.delete_selected_message')
			};
			return;
		}
		if (action === 'PROMOTE_ALL') {
			batchConfirm = {
				action,
				title: authorFilter
					? $t('import.staged.promote_all_matching_title')
					: $t('import.staged.promote_all_title'),
				message: $t('import.staged.promote_all_message')
			};
			return;
		}

		await runBatchAction(action);
	}

	async function runBatchAction(action: 'PROMOTE' | 'DELETE' | 'PROMOTE_ALL') {
		if (action !== 'PROMOTE_ALL' && selectedIds.size === 0) return;

		isProcessingBatch = true;
		const result = await api.post<void>('/books/staged/batch', {
			ids: action === 'PROMOTE_ALL' ? [] : Array.from(selectedIds),
			action,
			author: authorFilter
		});

		if (!result.left) {
			selectedIds = new Set();
			progress.ensurePolling();
		}
		isProcessingBatch = false;
		batchConfirm = null;
	}
</script>

<div class="mx-auto w-full">
	<header class="mb-8 rounded-[1.75rem] border border-border/70 bg-card/70 p-6 shadow-xl shadow-black/5 backdrop-blur-md">
		<div class="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
			<div>
				<p class="mb-2 text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{$t('import.eyebrow')}</p>
				<h2 class="font-display text-4xl font-bold text-primary">{$t('import.page_title')}</h2>
				<p class="text-muted-foreground mt-2 max-w-2xl">{$t('import.page_subtitle')}</p>
			</div>

			<div class="flex flex-wrap items-center gap-3">
				<div class="inline-flex min-w-[7.5rem] items-center justify-between gap-3 rounded-full border border-border/70 bg-background/80 px-4 py-2 text-sm font-medium text-foreground">
					<span class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">
						{$t('common.queued')}
					</span>
					<span>{pageData?.totalCount ?? 0}</span>
				</div>

				{#if selectedIds.size > 0}
					<div class="inline-flex min-w-[7.5rem] items-center justify-between gap-3 rounded-full border border-primary/40 bg-primary/10 px-4 py-2 text-sm font-medium text-foreground">
						<span class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">
							{$t('common.selection')}
						</span>
						<span>{selectedIds.size}</span>
					</div>
				{/if}

				<div class="relative inline-flex min-w-[12rem] items-center rounded-full border border-border/70 bg-background/80 text-sm font-medium text-foreground">
					<span class="pl-4 text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('common.filter')}</span>
					<input
						type="text"
						bind:value={authorFilter}
						placeholder={$t('common.filter_by_author')}
						class="w-full bg-transparent px-3 py-2 text-sm focus:outline-none"
					/>
					{#if authorFilter}
						<button
							onclick={() => authorFilter = ''}
							class="pr-3 text-muted-foreground hover:text-foreground text-xs"
						>
							✕
						</button>
					{/if}
				</div>

				<button
					type="button"
					onclick={() => viewMode = viewMode === 'list' ? 'table' : 'list'}
					class="inline-flex min-w-[9rem] items-center justify-between gap-3 rounded-full border border-border/70 bg-background/80 px-4 py-2 text-sm font-medium text-foreground transition-all hover:border-primary/40 hover:bg-card focus:outline-none focus:ring-2 focus:ring-primary/30"
				>
					<span class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">
						{$t('import.view_label')}
					</span>
					<span class="capitalize">{viewMode}</span>
				</button>


			</div>
		</div>
	</header>

	<section class="mb-8 space-y-4">
			{#if visibleScanProgress}
				<div class="rounded-2xl border border-primary/25 bg-primary/10 p-4">
					<div class="flex flex-wrap items-start justify-between gap-4">
						<div>
							<p class="text-[10px] font-bold uppercase tracking-[0.3em] text-primary">{$t('import.progress.title')}</p>
							<p class="mt-3 font-display text-3xl text-foreground">
								{$t('import.progress.processed', {
									count: processedCount,
									total: visibleScanProgress.totalFiles
								})}
							</p>
							<p class="mt-1 text-sm text-muted-foreground">
								<span>{progressLabel(visibleScanProgress)} {$t('import.progress.for')} </span>
								<span class="font-medium text-foreground">{visibleScanProgress.sourcePath}</span>
							</p>
						</div>
						<div class="min-w-[14rem] rounded-2xl border border-primary/20 bg-background/70 px-4 py-3 text-sm">
							<div class="flex items-center justify-between gap-4">
								<span class="text-muted-foreground">{$t('common.queued')}</span>
								<span class="font-semibold text-foreground">{visibleScanProgress.queuedFiles}</span>
							</div>
							<div class="mt-2 flex items-center justify-between gap-4">
								<span class="text-muted-foreground">{$t('common.imported')}</span>
								<span class="font-semibold text-foreground">{visibleScanProgress.completedFiles}</span>
							</div>
							{#if visibleScanProgress.failedFiles > 0}
								<div class="mt-2 flex items-center justify-between gap-4 text-destructive">
									<span>{$t('common.failed')}</span>
									<span class="font-semibold">{visibleScanProgress.failedFiles}</span>
								</div>
								{#if visibleScanProgress.failedFileDetails?.length > 0}
									<button
										onclick={() => showFailedDetails = !showFailedDetails}
										class="mt-2 text-xs text-destructive/80 hover:text-destructive underline underline-offset-2"
									>
										{showFailedDetails ? $t('import.progress.hide_failures') : $t('import.progress.show_failures')}
									</button>
								{/if}
							{/if}
						</div>
					</div>
					{#if showFailedDetails && visibleScanProgress.failedFileDetails?.length > 0}
						<div class="mt-4 max-h-48 overflow-y-auto rounded-xl border border-destructive/20 bg-destructive/5 p-3 text-sm">
							{#each visibleScanProgress.failedFileDetails as detail (detail.fileName)}
								<div class="py-1.5 border-b border-destructive/10 last:border-0">
									<span class="font-medium text-foreground">{detail.fileName}</span>
									<span class="ml-2 text-muted-foreground">{detail.errorMessage}</span>
								</div>
							{/each}
						</div>
					{/if}
					<div class="mt-5">
						<div class="h-3 overflow-hidden rounded-full bg-primary/15">
							<div
								class="h-full rounded-full bg-primary transition-all duration-500"
								style={`width: ${progressPercent}%`}
							></div>
						</div>
						<div class="mt-2 flex items-center justify-between gap-4 text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground">
							<span>{$t('import.progress.percent_processed', { percent: progressPercent })}</span>
							<span>
								{#if visibleScanProgress.status === 'RUNNING'}
									{$t('import.progress.auto_refresh')}
								{:else}
									{$t('import.progress.refresh_complete')}
								{/if}
							</span>
						</div>
					</div>
				</div>
			{/if}
			{#if visibleBatchProgress}
				<div class="rounded-2xl border border-primary/25 bg-primary/10 p-4">
					<div class="flex flex-wrap items-start justify-between gap-4">
						<div>
							<p class="text-[10px] font-bold uppercase tracking-[0.3em] text-primary">{$t('progress.batch_title')}</p>
							<p class="mt-3 font-display text-3xl text-foreground">
								{$t('import.progress.processed', {
									count: batchProcessedCount,
									total: visibleBatchProgress.totalItems
								})}
							</p>
							<p class="mt-1 text-sm text-muted-foreground">
								{visibleBatchProgress.action === 'DELETE'
									? $t('progress.batch_deleting')
									: $t('progress.batch_promoting')}
							</p>
						</div>
						<div class="min-w-[14rem] rounded-2xl border border-primary/20 bg-background/70 px-4 py-3 text-sm">
							<div class="flex items-center justify-between gap-4">
								<span class="text-muted-foreground">{$t('common.completed')}</span>
								<span class="font-semibold text-foreground">{visibleBatchProgress.completedItems}</span>
							</div>
							{#if visibleBatchProgress.failedItems > 0}
								<div class="mt-2 flex items-center justify-between gap-4 text-destructive">
									<span>{$t('common.failed')}</span>
									<span class="font-semibold">{visibleBatchProgress.failedItems}</span>
								</div>
								{#if visibleBatchProgress.failedItemDetails?.length > 0}
									<button
										onclick={() => showBatchFailedDetails = !showBatchFailedDetails}
										class="mt-2 text-xs text-destructive/80 hover:text-destructive underline underline-offset-2"
									>
										{showBatchFailedDetails ? $t('import.progress.hide_failures') : $t('import.progress.show_failures')}
									</button>
								{/if}
							{/if}
							{#if visibleBatchProgress.warningItems > 0}
								<div class="mt-2 flex items-center justify-between gap-4 text-amber-500">
									<span>{$t('common.warnings')}</span>
									<span class="font-semibold">{visibleBatchProgress.warningItems}</span>
								</div>
								{#if visibleBatchProgress.warningDetails?.length > 0}
									<button
										onclick={() => showBatchWarningDetails = !showBatchWarningDetails}
										class="mt-2 text-xs text-amber-500/80 hover:text-amber-500 underline underline-offset-2"
									>
										{showBatchWarningDetails ? $t('import.progress.hide_warnings') : $t('import.progress.show_warnings')}
									</button>
								{/if}
							{/if}
						</div>
					</div>
					{#if showBatchFailedDetails && visibleBatchProgress.failedItemDetails?.length > 0}
						<div class="mt-4 max-h-48 overflow-y-auto rounded-xl border border-destructive/20 bg-destructive/5 p-3 text-sm">
							{#each visibleBatchProgress.failedItemDetails as detail (detail.fileName)}
								<div class="py-1.5 border-b border-destructive/10 last:border-0">
									<span class="font-medium text-foreground">{detail.fileName}</span>
									<span class="ml-2 text-muted-foreground">{detail.errorMessage}</span>
								</div>
							{/each}
						</div>
					{/if}
					{#if showBatchWarningDetails && visibleBatchProgress.warningDetails?.length > 0}
						<div class="mt-4 max-h-48 overflow-y-auto rounded-xl border border-amber-500/20 bg-amber-500/5 p-3 text-sm">
							{#each visibleBatchProgress.warningDetails as detail}
								<div class="py-1.5 border-b border-amber-500/10 last:border-0">
									<span class="font-medium text-foreground">{detail.fileName}</span>
									<span class="ml-2 text-muted-foreground">{detail.field}: {detail.message}</span>
								</div>
							{/each}
						</div>
					{/if}
					<div class="mt-5">
						<div class="h-3 overflow-hidden rounded-full bg-primary/15">
							<div
								class="h-full rounded-full bg-primary transition-all duration-500"
								style={`width: ${batchPercent}%`}
							></div>
						</div>
						<div class="mt-2 flex items-center justify-between gap-4 text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground">
							<span>{$t('import.progress.percent_processed', { percent: batchPercent })}</span>
							<span>
								{#if visibleBatchProgress.status === 'RUNNING'}
									{$t('import.progress.auto_refresh')}
								{:else}
									{$t('import.progress.refresh_complete')}
								{/if}
							</span>
						</div>
					</div>
				</div>
			{/if}
	</section>

	<BatchActionBar
		selectedCount={selectedIds.size}
		processing={isProcessingBatch}
		onClear={() => selectedIds = new Set()}
		onAction={handleBatchAction}
		actions={[
			{ id: 'PROMOTE', label: $t('import.actions.promote_selected') },
			{ id: 'DELETE', label: $t('import.actions.delete_selected'), variant: 'destructive' }
		]}
	/>

	{#if selectedIds.size === 0 && pageData && pageData.totalCount > 0}
		<div class="mb-6 p-4 bg-muted/20 border border-border rounded-lg flex items-center justify-between">
			<div class="text-sm text-muted-foreground font-medium">
				{authorFilter
					? $t('import.summary.count_matching', { count: pageData.totalCount })
					: $t('import.summary.count', { count: pageData.totalCount })}
			</div>
			<button 
				onclick={() => handleBatchAction('PROMOTE_ALL')}
				disabled={isProcessingBatch}
				class="bg-primary hover:bg-primary/90 text-primary-foreground text-xs font-bold py-1.5 px-6 rounded-md shadow-sm transition-all disabled:opacity-50"
			>
				{authorFilter ? $t('import.actions.promote_all_matching') : $t('import.actions.promote_all')}
			</button>
		</div>
	{/if}

	{#if isLoading}
		<div class="rounded-[1.5rem] border border-border/70 bg-card/70 p-10 shadow-xl shadow-black/5">
			<div class="grid gap-4">
				<div class="h-6 w-48 animate-pulse rounded bg-accent/40"></div>
				<div class="h-28 animate-pulse rounded-2xl bg-accent/30"></div>
				<div class="h-28 animate-pulse rounded-2xl bg-accent/30"></div>
			</div>
		</div>
	{:else if !pageData || pageData.items.length === 0}
		{#if authorFilter}
			<div class="mb-6 rounded-2xl border border-border/70 bg-card/70 p-5 text-sm text-muted-foreground shadow-lg shadow-black/5">
				{$t('import.staged.no_match', { author: authorFilter })}
			</div>
		{/if}
		<EmptyState
			eyebrow={$t('import.staged.eyebrow')}
			title={authorFilter ? $t('import.staged.empty_filtered_title') : $t('import.staged.empty_title')}
			message={authorFilter ? $t('import.staged.empty_filtered_message') : $t('import.empty_state')}
			actionText={$t('import.link_go_to_import')}
			href="/import"
		/>
	{:else}
		{#if viewMode === 'list'}
			<div class="grid grid-cols-1 gap-6">
				{#each pageData.items as book (book.id)}
					<StagedBookItem {book} onActionSuccess={handleActionSuccess} />
				{/each}
			</div>
		{:else}
			<StagedBookTable 
				books={pageData.items} 
				bind:selectedIds 
				bind:sortBy
				bind:sortDir
				onActionSuccess={handleActionSuccess}
			/>
		{/if}

		<Pagination 
			bind:currentPage 
			totalCount={pageData.totalCount} 
			{pageSize} 
		/>
	{/if}
</div>

<ConfirmDialog
	open={batchConfirm !== null}
	title={batchConfirm?.title ?? ''}
	message={batchConfirm?.message ?? ''}
	confirmLabel={batchConfirm?.action === 'PROMOTE_ALL' ? 'Promote all' : 'Delete selected'}
	variant={batchConfirm?.action === 'DELETE' ? 'destructive' : 'default'}
	processing={isProcessingBatch}
	onCancel={() => (batchConfirm = null)}
	onConfirm={() => {
		if (batchConfirm) {
			void runBatchAction(batchConfirm.action);
		}
	}}
/>
