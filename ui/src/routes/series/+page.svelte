<script lang="ts">
	import { t } from '$lib/i18n';
	import { resolve } from '$app/paths';
	import AuthenticatedImage from '$lib/components/ui/AuthenticatedImage.svelte';
	import SeriesTable from '$lib/components/SeriesTable.svelte';
	import Pagination from '$lib/components/ui/Pagination.svelte';
	import StatusBanner from '$lib/components/ui/StatusBanner.svelte';
	import { api } from '$lib/api/client';
	import type { SeriesPage } from '$lib/types/models';

	let { data } = $props();

	type ViewMode = 'grid' | 'table';
	type SortPreset = { sortBy: string; sortDir: 'ASC' | 'DESC' };

	const sortPresets: SortPreset[] = [
		{ sortBy: 'title', sortDir: 'ASC' },
		{ sortBy: 'title', sortDir: 'DESC' },
		{ sortBy: 'bookCount', sortDir: 'DESC' },
		{ sortBy: 'bookCount', sortDir: 'ASC' },
	];

	let remotePageData = $state<SeriesPage | null>(null);
	let pageData = $derived<SeriesPage>(remotePageData || data.seriesPage);

	let viewMode = $state<ViewMode>('grid');
	let sortBy = $state('title');
	let sortDir = $state<'ASC' | 'DESC'>('ASC');
	let currentPage = $state(0);
	let pageSize = $state(20);
	let loadError = $state<string | null>(null);
	let previousSortKey = $state('title:ASC');
	let requestVersion = 0;

	$effect(() => {
		const sortKey = `${sortBy}:${sortDir}`;
		if (sortKey !== previousSortKey) {
			previousSortKey = sortKey;
			if (currentPage !== 0) {
				currentPage = 0;
				return;
			}
		}

		if (
			!remotePageData &&
			currentPage === 0 &&
			pageSize === 20 &&
			sortBy === 'title' &&
			sortDir === 'ASC'
		) {
			return;
		}

		void refreshData(currentPage, pageSize, sortBy, sortDir);
	});

	async function refreshData(p: number, s: number, sort: string, dir: string) {
		const version = ++requestVersion;
		const result = await api.get<SeriesPage>(`/series/page?page=${p}&size=${s}&sortBy=${sort}&sortDir=${dir}`);
		if (version !== requestVersion) return;
		if (result.right) {
			remotePageData = result.right;
			loadError = null;
		} else {
			loadError = result.left?.message ?? t.get('errors.api.message');
		}
	}

	function toggleViewMode() {
		viewMode = viewMode === 'grid' ? 'table' : 'grid';
	}

	function cycleSortPreset() {
		const currentIndex = sortPresets.findIndex((p) => p.sortBy === sortBy && p.sortDir === sortDir);
		const next = sortPresets[(currentIndex + 1 + sortPresets.length) % sortPresets.length];
		sortBy = next.sortBy;
		sortDir = next.sortDir;
		currentPage = 0;
	}

	let currentSortLabel = $derived.by(() => {
		if (sortBy === 'title') return sortDir === 'ASC' ? t.get('series.dashboard.sort_alpha_asc') : t.get('series.dashboard.sort_alpha_desc');
		return sortDir === 'DESC' ? t.get('series.dashboard.sort_count_desc') : t.get('series.dashboard.sort_count_asc');
	});

	let series = $derived(pageData?.items ?? []);
</script>

<svelte:head>
	<title>{$t('series.page_title')} | Shelf</title>
</svelte:head>

<div class="mx-auto w-full">
	<header class="mb-8 rounded-[1.75rem] border border-border/70 bg-card/70 p-6 shadow-xl shadow-black/5 backdrop-blur-md">
		<div class="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
			<div>
				<p class="mb-2 text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{$t('series.eyebrow')}</p>
				<h2 class="font-display text-4xl font-bold text-primary">{$t('series.page_title')}</h2>
				<p class="text-muted-foreground mt-2 max-w-2xl">{$t('series.page_subtitle')}</p>
			</div>

			<div class="flex flex-wrap items-center gap-3">
				<div class="inline-flex min-w-[7.5rem] items-center justify-between gap-3 rounded-full border border-border/70 bg-background/80 px-4 py-2 text-sm font-medium text-foreground">
					<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">
						{$t('series.dashboard.count_label')}
					</p>
					<p>{pageData.totalCount}</p>
				</div>
				<button
					type="button"
					onclick={toggleViewMode}
					class="inline-flex min-w-[9rem] items-center justify-between gap-3 rounded-full border border-border/70 bg-background/80 px-4 py-2 text-sm font-medium text-foreground transition-all hover:border-primary/40 hover:bg-card focus:outline-none focus:ring-2 focus:ring-primary/30"
				>
					<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('series.dashboard.view_label')}</p>
					<p class="capitalize">{viewMode}</p>
				</button>
				<button
					type="button"
					onclick={cycleSortPreset}
					class="inline-flex min-w-[11rem] items-center justify-between gap-3 rounded-full border border-border/70 bg-background/80 px-4 py-2 text-sm font-medium text-foreground transition-all hover:border-primary/40 hover:bg-card focus:outline-none focus:ring-2 focus:ring-primary/30"
				>
					<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('series.dashboard.sort_label')}</p>
					<p>{currentSortLabel}</p>
				</button>
			</div>
		</div>
	</header>

	{#if series.length === 0 && pageData.totalCount === 0}
		<div class="bg-card border border-border rounded-[1.5rem] p-12 text-center shadow-xl">
			<p class="text-muted-foreground text-lg">{$t('series.empty_state')}</p>
		</div>
	{:else}
		{#if loadError}
			<div class="mb-6">
				<StatusBanner kind="error" title={$t('series.load_error_title')} message={loadError} />
			</div>
		{/if}

		{#if viewMode === 'grid'}
			<div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
				{#each series as s (s.id)}
					<a
						href={resolve(`/series/${s.id}`)}
						class="bg-card/80 border border-border rounded-[1.5rem] p-3 shadow-xl hover:border-primary/50 hover:-translate-y-1 transition-all group flex items-center gap-4"
					>
						<div class="w-20 h-28 shrink-0 rounded-lg overflow-hidden bg-background isolate">
							{#if s.coverPath}
								<AuthenticatedImage
									src={`/api/series/${s.id}/cover`}
									alt={s.name}
									class="h-full w-full object-contain p-1"
								/>
							{:else}
								<div class="flex h-full w-full items-center justify-center text-[9px] font-bold text-muted-foreground text-center p-1 leading-tight">
									{s.name}
								</div>
							{/if}
						</div>
						<div class="min-w-0">
							<p class="mb-1 text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('common.series')}</p>
							<h3 class="font-display text-xl font-bold text-foreground group-hover:text-primary transition-colors truncate">
								{s.name}
							</h3>
							<p class="text-muted-foreground mt-2 text-sm">
								{$t(s.bookCount === 1 ? 'common.counts.book_one' : 'common.counts.book_other', { count: s.bookCount })}
							</p>
						</div>
					</a>
				{/each}
			</div>
		{:else}
			<SeriesTable {series} bind:sortBy bind:sortDir />
		{/if}

		<Pagination
			bind:currentPage
			totalCount={pageData.totalCount}
			{pageSize}
		/>
	{/if}
</div>
