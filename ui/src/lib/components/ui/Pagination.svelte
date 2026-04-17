<script lang="ts">
    import { t } from '$lib/i18n';

    let {
        currentPage = $bindable(0),
        totalCount,
        pageSize,
        class: className = ''
    } = $props<{
        currentPage: number;
        totalCount: number;
        pageSize: number;
        class?: string;
    }>();

    const totalPages = $derived(Math.ceil(totalCount / pageSize));
    const startItem = $derived(totalCount === 0 ? 0 : currentPage * pageSize + 1);
    const endItem = $derived(Math.min(totalCount, (currentPage + 1) * pageSize));
</script>

{#if totalCount > pageSize}
    <div class="mt-8 flex flex-col gap-3 rounded-2xl border border-border/70 bg-card/70 px-4 py-4 shadow-lg shadow-black/5 sm:flex-row sm:items-center sm:justify-between sm:px-5 {className}">
        <div class="text-sm text-muted-foreground">
            {$t('common.pagination.showing', { start: startItem, end: endItem, total: totalCount })}
        </div>
        <div class="flex items-center justify-between gap-3 sm:justify-center sm:gap-4">
        <button
                disabled={currentPage === 0}
                onclick={() => currentPage--}
                aria-label={$t('common.actions.previous_page')}
                class="rounded-xl border border-border bg-background p-3 text-muted-foreground transition-colors hover:text-foreground disabled:opacity-30"
        >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M12.707 5.293a1 1 0 010 1.414L9.414 10l3.293 3.293a1 1 0 01-1.414 1.414l-4-4a1 1 0 010-1.414l4-4a1 1 0 011.414 0z" clip-rule="evenodd" />
            </svg>
        </button>

        <div class="min-w-28 text-center text-sm text-muted-foreground">
            {$t('common.pagination.page', { page: currentPage + 1, totalPages })}
        </div>

        <button
                disabled={(currentPage + 1) * pageSize >= totalCount}
                onclick={() => currentPage++}
                aria-label={$t('common.actions.next_page')}
                class="rounded-xl border border-border bg-background p-3 text-muted-foreground transition-colors hover:text-foreground disabled:opacity-30"
        >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd" />
            </svg>
        </button>
        </div>
    </div>
{/if}
