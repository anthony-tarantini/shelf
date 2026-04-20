<script lang="ts">
    import ToggleChip from '../ToggleChip.svelte';
    import {t} from '$lib/i18n';

    let {
        label,
        currentItems,
        externalOptions,
        onToggleItem
    } = $props<{
        label: string;
        currentItems: string[];
        externalOptions: string[];
        onToggleItem: (item: string) => void;
    }>();

    let uniqueExternalOptions = $derived(
        externalOptions.filter((opt: string) => !currentItems.includes(opt))
    );
</script>

<tr class="bg-background">
    <td class="p-3 font-medium text-muted-foreground">{label}</td>

    <td class="p-3">
        <div class="flex flex-wrap gap-2">
            {#each currentItems as item (item)}
                <ToggleChip label={item} selected={true} onToggle={() => onToggleItem(item)}/>
            {:else}
                <span class="text-muted-foreground italic">{$t('metadata.comparison.none_selected', {label: label.toLowerCase()})}</span>
            {/each}
        </div>
    </td>

    <td class="p-3">
        <div class="flex flex-wrap gap-2">
            {#each uniqueExternalOptions as option (option)}
                <ToggleChip label={option} selected={false} onToggle={() => onToggleItem(option)}/>
            {/each}
        </div>
    </td>
</tr>
