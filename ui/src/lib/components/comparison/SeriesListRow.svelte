<script lang="ts">
    import ToggleChip from '../ToggleChip.svelte';
    import {t} from '$lib/i18n';
    import type {ExternalSeries, StagedSeries} from "$lib/types/models";

    interface Props {
        currentSeries: StagedSeries[];
        externalSeriesOptions: ExternalSeries[];
        onToggleSeries: (name: string, position?: number) => void;
        onUpdateSeriesIndex?: (name: string, index: number | undefined) => void;
    }

    let {
        currentSeries = [],
        externalSeriesOptions = [],
        onToggleSeries,
        onUpdateSeriesIndex
    }: Props = $props();
</script>

{#if externalSeriesOptions.length > 0 || currentSeries.length > 0}
    <tr class="bg-background">
        <td class="p-3 font-medium text-muted-foreground">{$t('metadata.comparison.series_label')}</td>

        <td class="p-3 text-foreground">
            {#if currentSeries.length > 0}
                <div class="flex flex-wrap gap-2">
                    {#each currentSeries as series (series.name)}
                        <div class="flex items-center gap-1 bg-primary/10 border border-primary/30 rounded-full pl-3 pr-1 py-1 text-sm text-primary">
                            <span class="font-medium whitespace-nowrap">{series.name}</span>
                            <span class="text-primary/60 font-medium">#</span>
                            <input
                                type="text"
                                inputmode="decimal"
                                value={series.index ?? ''}
                                oninput={(e) => {
                                    const val = e.currentTarget.value.replace(/[^0-9.]/g, '');
                                    onUpdateSeriesIndex?.(series.name, val ? parseFloat(val) : undefined);
                                }}
                                class="w-10 bg-transparent border-b border-transparent hover:border-primary/30 focus:border-primary outline-none text-center"
                                title={$t('metadata.comparison.edit_series_index')}
                                placeholder={$t('metadata.comparison.unknown_series_index')}
                            />
                            <button
                                type="button"
                                onclick={() => onToggleSeries(series.name, series.index)}
                                class="ml-1 text-primary/60 hover:text-primary rounded-full p-0.5 hover:bg-primary/20 transition-colors"
                                aria-label={$t('metadata.comparison.remove_series')}
                            >
                                <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                                    <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
                                </svg>
                            </button>
                        </div>
                    {/each}
                </div>
            {:else}
                <span class="text-muted-foreground italic">{$t('metadata.comparison.no_series_selected')}</span>
            {/if}
        </td>

        <td class="p-3">
            <div class="flex flex-wrap gap-2">
                {#each externalSeriesOptions as series (series.name)}
                    <ToggleChip
                            label={`${series.name} #${series.position ?? $t('metadata.comparison.unknown_series_index')}`}
                            selected={currentSeries.some(s => s.name === series.name)}
                            onToggle={() => onToggleSeries(series.name, series.position)}
                    />
                {/each}
            </div>
        </td>
    </tr>
{/if}
