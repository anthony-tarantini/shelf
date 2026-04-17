<script lang="ts">
    import { t } from '$lib/i18n';
    import type { StagedSeries } from '$lib/types/models';
    import FormField from '$lib/components/ui/FormField.svelte';

    interface Props {
        bookId: string;
        seriesList: StagedSeries[];
    }

    let {
        bookId,
        seriesList = $bindable<StagedSeries[]>([])
    }: Props = $props();

    function addSeries() {
        seriesList.push({ name: '', index: undefined });
    }

    function removeSeries(index: number) {
        seriesList.splice(index, 1);
    }
</script>

<FormField label={$t('books.metadata.series.title')}>
    <div class="flex justify-between items-center mb-2">
        <button type="button" onclick={addSeries} class="text-xs text-primary hover:text-primary/80">{$t('books.metadata.series.add')}</button>
    </div>
    {#if seriesList.length === 0}
        <p class="text-xs text-muted-foreground italic mb-2">{$t('books.metadata.series.no_series')}</p>
    {/if}
    <div class="space-y-2">
        {#each seriesList as series, idx (series.name)}
            <div class="flex gap-2 items-center">
                <label for="series-name-{bookId}-{idx}" class="sr-only">{$t('books.metadata.series.series_name')}</label>
                <input
                    id="series-name-{bookId}-{idx}"
                    type="text"
                    placeholder={$t('books.metadata.series.series_name')}
                    bind:value={series.name}
                    class="ui-input-sm flex-1"
                />
                <label for="series-index-{bookId}-{idx}" class="sr-only">{$t('books.metadata.series.series_index')}</label>
                <input
                    id="series-index-{bookId}-{idx}"
                    type="text"
                    inputmode="decimal"
                    placeholder={$t('books.metadata.series.index')}
                    value={series.index ?? ''}
                    oninput={(e) => {
                        const val = e.currentTarget.value.replace(/[^0-9.]/g, '');
                        series.index = val ? parseFloat(val) : undefined;
                    }}
                    class="ui-input-sm w-24"
                />
                <button 
                    type="button" 
                    onclick={() => removeSeries(idx)} 
                    class="text-destructive hover:text-destructive/80 p-1"
                    aria-label={$t('books.metadata.series.remove_series')}
                    title={$t('books.metadata.series.remove_series')}
                >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                </button>
            </div>
        {/each}
    </div>
</FormField>
