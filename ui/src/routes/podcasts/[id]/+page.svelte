<script lang="ts">
	import { resolve } from '$app/paths';
	import { t } from '$lib/i18n';
	import PodcastEpisodeList from '$lib/components/PodcastEpisodeList.svelte';
	import PodcastInfoSidebar from '$lib/components/podcast/PodcastInfoSidebar.svelte';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();
	let aggregate = $derived(data.aggregate!);
	let episodes = $derived(aggregate.episodes);
</script>

<svelte:head>
	<title>{aggregate.seriesTitle} | {$t('podcasts.page_title')}</title>
</svelte:head>

<div class="flex flex-col gap-8">
	<!-- Header -->
	<div class="flex flex-col items-start gap-4 md:flex-row md:items-end md:gap-8">
		<a
			href={resolve('/podcasts')}
			class="group flex items-center gap-2 text-sm font-medium text-muted-foreground transition-colors hover:text-primary"
		>
			<span class="transition-transform group-hover:-translate-x-1">←</span>
			{$t('podcasts.detail.back')}
		</a>
	</div>

	<div class="flex flex-col gap-8 lg:flex-row">
		<!-- Sidebar / Info -->
		<PodcastInfoSidebar {aggregate} />

		<!-- Episodes List -->
		<div class="flex-1">
			<div class="mb-6 flex items-center justify-between">
				<h2 class="font-display text-3xl font-bold text-foreground">
					{$t('podcasts.detail.episodes_tab')}
					<span class="ml-2 text-lg font-normal text-muted-foreground">{episodes.length}</span>
				</h2>
			</div>

			{#if episodes.length > 0}
				<PodcastEpisodeList podcastId={aggregate.podcast.id} {episodes} />
			{:else}
				<div class="flex flex-col items-center justify-center rounded-2xl border border-dashed border-border bg-card/30 py-20 text-center">
					<div class="mb-4 rounded-full bg-accent/30 p-4 text-muted-foreground">
						<svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10 opacity-20" fill="none" viewBox="0 0 24 24" stroke="currentColor">
							<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
						</svg>
					</div>
					<p class="text-lg font-medium text-muted-foreground">
						{$t('podcasts.detail.no_episodes')}
					</p>
				</div>
			{/if}
		</div>
	</div>
</div>
