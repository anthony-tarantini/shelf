<script lang="ts">
    import { t } from '$lib/i18n';

    let {
        selectedCount,
        processing = false,
        onClear,
        onAction,
        actions = []
    } = $props<{
        selectedCount: number;
        processing?: boolean;
        onClear: () => void;
        onAction: (id: string) => void;
        actions?: readonly { id: string; label: string; variant?: 'primary' | 'destructive' }[];
    }>();
</script>

{#if selectedCount > 0}
    <div class="animate-in fade-in slide-in-from-top-2 mb-6 flex flex-col gap-4 rounded-[1.5rem] border border-primary/20 bg-primary/10 p-4 shadow-lg shadow-primary/5 md:flex-row md:items-center md:justify-between">
        <div class="flex items-center gap-4">
            <div class="flex h-10 w-10 items-center justify-center rounded-full bg-primary/15 text-primary">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
            </div>
            <div>
                <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('common.selection')}</p>
                <span class="text-sm font-semibold text-foreground">{$t('common.selection_count', { count: selectedCount })}</span>
            </div>
            <button onclick={onClear} class="text-xs text-muted-foreground underline decoration-primary/30 underline-offset-4 transition-colors hover:text-foreground">{$t('common.actions.clear_selection')}</button>
        </div>
        <div class="flex flex-wrap gap-2">
            {#each actions as action}
                <button
                        disabled={processing}
                        onclick={() => onAction(action.id)}
                        class="rounded-xl px-4 py-2 text-xs font-bold transition-all disabled:opacity-50 {action.variant === 'destructive' ? 'bg-destructive text-destructive-foreground shadow-lg shadow-destructive/20 hover:bg-destructive/90' : 'bg-primary text-primary-foreground shadow-lg shadow-primary/20 hover:bg-primary/90'}"
                >
                    {action.label}
                </button>
            {/each}
        </div>
    </div>
{/if}
