<script lang="ts">
	import type { EpisodeEntry } from '$lib/types/models';
	import { t } from '$lib/i18n';
	import AuthenticatedImage from './ui/AuthenticatedImage.svelte';
	import { formatDuration } from '$lib/utils';

	interface Props {
		podcastId: string;
		episodes: EpisodeEntry[];
	}

	let { podcastId, episodes }: Props = $props();

	function formatDate(iso?: string) {
		if (!iso) return '';
		return new Date(iso).toLocaleDateString(undefined, {
			year: 'numeric',
			month: 'short',
			day: 'numeric'
		});
	}
</script>

<div class="overflow-x-auto rounded-xl border border-border bg-card/50 shadow-xl backdrop-blur-sm">
	<table class="w-full border-collapse text-left text-sm">
		<thead class="sticky top-0 z-10 bg-card/95 text-[10px] font-bold uppercase tracking-wider text-muted-foreground/70 backdrop-blur">
			<tr>
				<th class="w-16 p-4">{$t('podcasts.table.cover')}</th>
				<th class="p-4">{$t('podcasts.table.title')}</th>
				<th class="w-24 p-4 text-center">{$t('podcasts.table.season')}</th>
				<th class="w-24 p-4 text-center">{$t('podcasts.table.episode')}</th>
				<th class="w-32 p-4">{$t('podcasts.table.duration')}</th>
				<th class="w-40 p-4">{$t('podcasts.table.published')}</th>
			</tr>
		</thead>
		<tbody class="divide-y divide-border/50">
			{#each episodes as episode (episode.id)}
				<tr class="group transition-colors hover:bg-primary/5">
					<td class="p-4">
						<a href={`/podcasts/${podcastId}`} class="block">
							{#if episode.coverPath}
								<AuthenticatedImage
									src={`/api/podcasts/${podcastId}/episodes/${episode.id}/cover?v=${encodeURIComponent(episode.coverPath)}`}
									alt=""
									class="h-14 w-10 bg-background object-contain p-0.5 shadow-sm transition-transform group-hover:scale-105"
								/>
							{:else}
								<div class="flex h-14 w-10 items-center justify-center rounded border border-border bg-background text-[8px] text-muted-foreground shadow-sm">
									{$t('common.no_cover')}
								</div>
							{/if}
						</a>
					</td>
					<td class="p-4">
						<a href={`/podcasts/${podcastId}`} class="block font-semibold text-foreground hover:text-primary transition-colors">
							{episode.title}
						</a>
					</td>
					<td class="p-4 text-center font-mono text-muted-foreground">
						{episode.season || '—'}
					</td>
					<td class="p-4 text-center font-mono text-muted-foreground">
						{episode.episode}
					</td>
					<td class="p-4 text-muted-foreground">
						{episode.totalTime ? formatDuration(episode.totalTime) : '—'}
					</td>
					<td class="p-4 text-muted-foreground">
						{formatDate(episode.publishedAt)}
					</td>
				</tr>
			{/each}
		</tbody>
	</table>
</div>
