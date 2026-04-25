<script lang="ts">
	import { resolve } from '$app/paths';
	import { t } from '$lib/i18n';
	import AuthenticatedImage from '$lib/components/ui/AuthenticatedImage.svelte';
	import type { SavedPodcastAggregate } from '$lib/types/models';

	let { aggregate }: { aggregate: SavedPodcastAggregate } = $props();
	let podcast = $derived(aggregate.podcast);
	let episodes = $derived(aggregate.episodes);

	function formatDate(iso?: string) {
		if (!iso) return $t('podcasts.card.never_fetched');
		return new Date(iso).toLocaleString(undefined, {
			year: 'numeric',
			month: 'short',
			day: 'numeric',
			hour: '2-digit',
			minute: '2-digit'
		});
	}
</script>

<div class="flex w-full flex-col gap-6 lg:w-72">
	<div class="overflow-hidden rounded-2xl border border-border bg-card shadow-2xl">
		{#if episodes.length > 0 && episodes[0].coverPath}
			<AuthenticatedImage
				src={`/api/podcasts/${podcast.id}/cover?v=${encodeURIComponent(episodes[0].coverPath)}`}
				alt={aggregate.seriesTitle}
				class="aspect-[3/4] w-full object-cover"
			/>
		{:else}
			<div class="flex aspect-[3/4] w-full items-center justify-center bg-accent/30 text-muted-foreground">
				<svg xmlns="http://www.w3.org/2000/svg" class="h-16 w-16 opacity-20" fill="none" viewBox="0 0 24 24" stroke="currentColor">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
				</svg>
			</div>
		{/if}
		<div class="p-6">
			<p class="mb-1 text-[10px] font-bold uppercase tracking-[0.3em] text-primary">
				{$t('podcasts.detail.info_eyebrow')}
			</p>
			<h1 class="font-display text-2xl font-bold leading-tight text-foreground">
				{aggregate.seriesTitle}
			</h1>
			
			<div class="mt-6 space-y-4">
				<div class="space-y-1">
					<p class="text-[10px] font-bold uppercase tracking-wider text-muted-foreground/70">
						{$t('podcasts.detail.feed_url')}
					</p>
					<p class="break-all text-xs font-medium text-foreground/80">
						{podcast.feedUrl}
					</p>
				</div>

				<div class="space-y-1">
					<p class="text-[10px] font-bold uppercase tracking-wider text-muted-foreground/70">
						{$t('podcasts.detail.last_fetched')}
					</p>
					<p class="text-sm font-medium text-foreground/80">
						{formatDate(podcast.lastFetchedAt)}
					</p>
				</div>

				{#if podcast.autoFetch}
					<div class="flex items-center gap-2 rounded-lg bg-primary/10 px-3 py-2 text-xs font-semibold text-primary">
						<div class="h-1.5 w-1.5 animate-pulse rounded-full bg-primary"></div>
						{$t('podcasts.detail.auto_fetch')}
					</div>
					<p class="px-1 text-[10px] text-muted-foreground">
						{$t('podcasts.detail.fetch_interval', { minutes: podcast.fetchIntervalMinutes })}
					</p>
				{/if}

				{#if podcast.autoSanitize}
					<div class="flex items-center gap-2 rounded-lg bg-secondary/10 px-3 py-2 text-xs font-semibold text-secondary">
						<svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
							<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
						</svg>
						{$t('podcasts.detail.auto_sanitize')}
					</div>
				{/if}
			</div>
		</div>
	</div>

	<a
		href={resolve(`/podcasts/${podcast.id}/settings`)}
		class="flex items-center justify-center gap-2 rounded-xl border border-border bg-card px-4 py-3 text-sm font-bold text-foreground transition-all hover:bg-accent"
	>
		<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-muted-foreground" fill="none" viewBox="0 0 24 24" stroke="currentColor">
			<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.543-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
		</svg>
		{$t('podcasts.detail.settings_tab')}
	</a>
</div>
