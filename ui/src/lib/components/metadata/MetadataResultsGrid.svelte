<script lang="ts">
    import { t } from '$lib/i18n';
    import MetadataSearchResultCard from './MetadataSearchResultCard.svelte';
    import type {ExternalMetadata} from "$lib/types/models";

    interface Props {
        results: ExternalMetadata[],
        onSelect: (result: ExternalMetadata) => void,
        onCancel: () => void
    }

    let {results, onSelect, onCancel}: Props = $props();
</script>

<div class="mb-4 flex justify-between items-center">
    <h4 class="text-lg font-bold text-foreground">{$t('metadata.manager.select_match')}</h4>
    <button type="button" onclick={onCancel} class="text-sm text-muted-foreground">{$t('common.actions.cancel')}</button>
</div>

<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
    {#each results as result (result.id)}
        <MetadataSearchResultCard {result} onSelect={() => onSelect(result)}/>
    {/each}
</div>
