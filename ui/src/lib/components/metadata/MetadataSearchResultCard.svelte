<script lang="ts">
	import { t } from '$lib/i18n';
	import { getExternalAuthorNames } from '$lib/utils/externalMetadata';
	import type { ExternalMetadata } from '../../types/models.ts';

	let { result, onSelect }: { result: ExternalMetadata; onSelect: (result: ExternalMetadata) => void } = $props();
	const authorNames = $derived(
		getExternalAuthorNames(result).join(', ') ||
			t.get('common.unknown_author')
	);
</script>

<div
	role="button"
	tabindex="0"
	aria-label={$t('metadata.manager.select_result', { title: result.title, author: authorNames })}
	onclick={() => onSelect(result)}
	onkeydown={(e) => (e.key === 'Enter' || e.key === ' ') && onSelect(result)}
	class="text-left bg-card hover:bg-accent border border-border rounded-md p-3 flex gap-3 transition-colors cursor-pointer group"
>
	{#if result.imageUrl}
		<img src={result.imageUrl} alt={result.title} class="w-16 h-24 object-cover rounded bg-background" />
	{:else}
		<div class="w-16 h-24 bg-background rounded flex items-center justify-center text-xs text-muted-foreground text-center">{$t('common.no_cover')}</div>
	{/if}
	<div class="grow min-w-0">
		<h5 class="font-bold text-foreground truncate group-hover:text-primary transition-colors" title={result.title}>{result.title}</h5>
		<p class="text-sm text-muted-foreground truncate mt-1">{authorNames}</p>
		{#if result.releaseYear}
			<p class="text-xs text-muted-foreground mt-1">{result.releaseYear}</p>
		{/if}
		{#if result.seriesName && result.seriesName.length > 0}
			<p class="text-xs text-primary mt-1 truncate">{result.seriesName[0].name}</p>
		{/if}
	</div>
</div>
