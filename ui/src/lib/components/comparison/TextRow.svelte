<script lang="ts">
    import { t } from '$lib/i18n';

    let {
        label,
        currentValue,
        externalValue,
        onUseExternal
    } = $props<{
        label: string;
        currentValue: string | null | undefined;
        externalValue: string | null | undefined;
        onUseExternal: () => void;
    }>();
</script>

{#if externalValue}
    <tr class="bg-background">
        <td class="p-3 font-medium text-muted-foreground w-1/5">{label}</td>
        <td class="p-3 text-foreground w-2/5">{currentValue || $t('common.none')}</td>
        <td class="p-3 w-2/5">
            <button
                    type="button"
                    onclick={onUseExternal}
                    class="px-3 py-1 bg-card hover:bg-primary/20 text-primary border border-border hover:border-primary rounded-full text-xs transition-all"
            >
                {externalValue}
            </button>
        </td>
    </tr>
{/if}
