<script lang="ts">
	import { resolve } from '$app/paths';
	import { t } from '$lib/i18n';
	import PodcastRssSettings from '$lib/components/podcast/PodcastRssSettings.svelte';
	import PodcastConfigSettings from '$lib/components/podcast/PodcastConfigSettings.svelte';
	import PodcastDangerZone from '$lib/components/podcast/PodcastDangerZone.svelte';
	import AudibleConnectCard from '$lib/components/audible/AudibleConnectCard.svelte';
	import type { PageData } from './$types';
	import type { SavedPodcastAggregate, SavedPodcastRoot } from '$lib/types/models';
	import { api } from '$lib/api/client';

	let { data }: { data: PageData } = $props();
	let aggregate = $state<SavedPodcastAggregate>(data.aggregate!);

	function handlePodcastUpdate(updated: SavedPodcastRoot) {
		aggregate = { ...aggregate, podcast: updated };
	}

	async function handleDisconnectAudible() {
		const result = await api.delete(`/podcasts/${aggregate.podcast.id}/audible`);
		if (result.right !== undefined) {
			aggregate = { ...aggregate, audibleConnected: false, audibleUsername: undefined };
		}
	}
</script>

<svelte:head>
	<title>{$t('podcasts.settings.title')} | {aggregate.seriesTitle}</title>
</svelte:head>

<div class="mx-auto max-w-3xl">
	<div class="mb-8 flex flex-col items-start gap-4 md:flex-row md:items-center md:justify-between">
		<div>
			<a
				href={resolve(`/podcasts/${aggregate.podcast.id}`)}
				class="group mb-2 flex items-center gap-2 text-sm font-medium text-muted-foreground transition-colors hover:text-primary"
			>
				<span class="transition-transform group-hover:-translate-x-1">←</span>
				{aggregate.seriesTitle}
			</a>
			<h1 class="font-display text-4xl font-bold text-foreground">{$t('podcasts.settings.title')}</h1>
		</div>
	</div>

	<div class="space-y-8">
		<!-- Audible Integration -->
		<AudibleConnectCard 
			isConnected={aggregate.audibleConnected} 
			username={aggregate.audibleUsername}
			onDisconnect={handleDisconnectAudible}
		/>

		<!-- RSS Feed Section -->
		<PodcastRssSettings podcast={aggregate.podcast} onUpdate={handlePodcastUpdate} />

		<!-- Configuration Section -->
		<PodcastConfigSettings podcast={aggregate.podcast} onUpdate={handlePodcastUpdate} />

		<!-- Danger Zone -->
		<PodcastDangerZone podcastId={aggregate.podcast.id} />
	</div>
</div>
