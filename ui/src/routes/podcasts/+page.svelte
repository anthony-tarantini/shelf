<script lang="ts">
	import { t } from '$lib/i18n';
	import { resolve } from '$app/paths';
	import AuthenticatedImage from '$lib/components/ui/AuthenticatedImage.svelte';
	import StatusBanner from '$lib/components/ui/StatusBanner.svelte';
	import { api } from '$lib/api/client';
	import type { PodcastSummary } from '$lib/types/models';

	let { data } = $props();

	let remotePodcasts = $state<PodcastSummary[] | null>(null);
	let podcasts = $derived<PodcastSummary[]>(remotePodcasts || data.podcasts);
	let loadError = $state<string | null>(null);

	type SortField = 'title' | 'episodeCount';
	type SortDir = 'ASC' | 'DESC';
	type SortPreset = { sortBy: SortField; sortDir: SortDir };

	const sortPresets: SortPreset[] = [
		{ sortBy: 'title', sortDir: 'ASC' },
		{ sortBy: 'title', sortDir: 'DESC' },
		{ sortBy: 'episodeCount', sortDir: 'DESC' },
		{ sortBy: 'episodeCount', sortDir: 'ASC' },
	];

	let sortBy = $state<SortField>('title');
	let sortDir = $state<SortDir>('ASC');

	let sorted = $derived.by(() => {
		const list = [...podcasts];
		list.sort((a, b) => {
			if (sortBy === 'title') {
				const cmp = a.seriesTitle.localeCompare(b.seriesTitle);
				return sortDir === 'ASC' ? cmp : -cmp;
			}
			const cmp = a.episodeCount - b.episodeCount;
			return sortDir === 'ASC' ? cmp : -cmp;
		});
		return list;
	});

	function cycleSortPreset() {
		const currentIndex = sortPresets.findIndex((p) => p.sortBy === sortBy && p.sortDir === sortDir);
		const next = sortPresets[(currentIndex + 1 + sortPresets.length) % sortPresets.length];
		sortBy = next.sortBy;
		sortDir = next.sortDir;
	}

	let currentSortLabel = $derived.by(() => {
		if (sortBy === 'title') return sortDir === 'ASC' ? $t('podcasts.dashboard.sort_alpha_asc') : $t('podcasts.dashboard.sort_alpha_desc');
		return sortDir === 'DESC' ? $t('podcasts.dashboard.sort_episodes_desc') : $t('podcasts.dashboard.sort_episodes_asc');
	});

	async function refreshData() {
		const result = await api.get<PodcastSummary[]>('/podcasts');
		if (result.right) {
			remotePodcasts = result.right;
			loadError = null;
		} else {
			loadError = result.left?.message ?? 'Failed to load podcasts';
		}
	}

	function formatLastFetched(iso?: string): string {
		if (!iso) return $t('podcasts.card.never_fetched');
		const date = new Date(iso);
		return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
	}
</script>

<svelte:head>
	<title>{$t('podcasts.page_title')} | Shelf</title>
</svelte:head>

<div class="mx-auto w-full">
	<header class="mb-8 rounded-[1.75rem] border border-border/70 bg-card/70 p-6 shadow-xl shadow-black/5 backdrop-blur-md">
		<div class="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
			<div>
				<p class="mb-2 text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{$t('podcasts.eyebrow')}</p>
				<h2 class="font-display text-4xl font-bold text-primary">{$t('podcasts.page_title')}</h2>
				<p class="text-muted-foreground mt-2 max-w-2xl">{$t('podcasts.page_subtitle')}</p>
			</div>

			<div class="flex flex-wrap items-center gap-3">
				<div class="inline-flex min-w-[7.5rem] items-center justify-between gap-3 rounded-full border border-border/70 bg-background/80 px-4 py-2 text-sm font-medium text-foreground">
					<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">
						{$t('podcasts.dashboard.count_label')}
					</p>
					<p>{podcasts.length}</p>
				</div>
				<button
					type="button"
					onclick={cycleSortPreset}
					class="inline-flex min-w-[11rem] items-center justify-between gap-3 rounded-full border border-border/70 bg-background/80 px-4 py-2 text-sm font-medium text-foreground transition-all hover:border-primary/40 hover:bg-card focus:outline-none focus:ring-2 focus:ring-primary/30"
				>
					<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('podcasts.dashboard.sort_label')}</p>
					<p>{currentSortLabel}</p>
				</button>
				<a
					href={resolve('/podcasts/subscribe')}
					class="inline-flex items-center gap-2 rounded-full bg-primary px-5 py-2 text-sm font-bold text-primary-foreground transition-colors hover:bg-primary/90"
				>
					<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
						<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
					</svg>
					{$t('podcasts.subscribe.action')}
				</a>
			</div>
		</div>
	</header>

	{#if loadError}
		<div class="mb-6">
			<StatusBanner kind="error" title={$t('podcasts.load_error_title')} message={loadError} />
		</div>
	{/if}

	{#if podcasts.length === 0}
		<div class="rounded-[1.5rem] border border-border bg-card p-12 text-center shadow-xl">
			<div class="mx-auto mb-6 flex h-16 w-16 items-center justify-center rounded-full bg-primary/10">
				<svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
				</svg>
			</div>
			<p class="text-lg text-muted-foreground">{$t('podcasts.empty_state')}</p>
			<a
				href={resolve('/podcasts/subscribe')}
				class="mt-6 inline-flex items-center gap-2 rounded-xl bg-primary px-6 py-3 font-bold text-primary-foreground transition-colors hover:bg-primary/90"
			>
				<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4" />
				</svg>
				{$t('podcasts.subscribe.action')}
			</a>
		</div>
	{:else}
		<div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
			{#each sorted as podcast (podcast.id)}
				<a
					href={`/podcasts/${podcast.id}`}
					class="group flex items-center gap-4 rounded-[1.5rem] border border-border bg-card/80 p-3 shadow-xl transition-all hover:-translate-y-1 hover:border-primary/50"
				>
					<div class="h-28 w-20 shrink-0 overflow-hidden rounded-lg bg-background isolate">
						{#if podcast.coverPath}
							<AuthenticatedImage
								src={`/api/podcasts/${podcast.id}/cover`}
								alt={podcast.seriesTitle}
								class="h-full w-full object-contain p-1"
							/>
						{:else}
							<div class="flex h-full w-full items-center justify-center p-1 text-center text-[9px] font-bold leading-tight text-muted-foreground">
								{podcast.seriesTitle}
							</div>
						{/if}
					</div>
					<div class="min-w-0 flex-1">
						<p class="mb-1 text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('podcasts.eyebrow')}</p>
						<h3 class="truncate font-display text-xl font-bold text-foreground transition-colors group-hover:text-primary">
							{podcast.seriesTitle}
						</h3>
						<p class="mt-1 text-sm text-muted-foreground">
							{$t(podcast.episodeCount === 1 ? 'podcasts.card.episodes_one' : 'podcasts.card.episodes', { count: podcast.episodeCount })}
						</p>
						<div class="mt-2 flex items-center gap-3 text-xs text-muted-foreground">
							{#if podcast.autoFetch}
								<span class="inline-flex items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 text-primary">
									<svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
										<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
									</svg>
									{$t('podcasts.card.auto_fetch')}
								</span>
							{/if}
							<span>{formatLastFetched(podcast.lastFetchedAt)}</span>
						</div>
					</div>
				</a>
			{/each}
		</div>
	{/if}
</div>
