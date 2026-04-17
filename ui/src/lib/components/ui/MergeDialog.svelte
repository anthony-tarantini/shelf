<script lang="ts">
    import {api} from '$lib/api/client';
    import { t } from '$lib/i18n';
    import type {StagedBook, GlobalSearchResult} from '$lib/types/models';
    import AuthenticatedImage from '$lib/components/ui/AuthenticatedImage.svelte';

    let {
        book,
        processing = false,
        onClose,
        onMerge
    } = $props<{
        book: StagedBook;
        processing?: boolean;
        onClose: () => void;
        onMerge: (targetBookId: string) => void;
    }>();

    let query = $state('');
    let results = $state<GlobalSearchResult | null>(null);
    let isSearching = $state(false);

    $effect(() => {
        if (!query) {
            query = book.title;
        }
    });

    $effect(() => {
        const q = query.trim();
        if (q.length < 2) {
            results = null;
            isSearching = false;
            return;
        }

        const timer = setTimeout(async () => {
            if (q === query.trim()) isSearching = true;
            try {
                const res = await api.get<GlobalSearchResult>(`/search?q=${encodeURIComponent(q)}`);
                if (q === query.trim() && res.right) {
                    results = res.right;
                }
            } finally {
                if (q === query.trim()) isSearching = false;
            }
        }, 300);

        return () => clearTimeout(timer);
    });
</script>

<div class="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm p-4">
    <div class="flex max-h-[80vh] w-full max-w-2xl flex-col overflow-hidden rounded-[1.75rem] border border-border bg-card shadow-2xl shadow-primary/10">
        <div class="flex items-center justify-between border-b border-border bg-muted/20 p-5">
            <div>
                <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('import.staged.merge')}</p>
                <h3 class="mt-2 text-xl font-semibold text-foreground">{$t('import.staged.merge_existing')} "{book.title}"</h3>
            </div>
            <button onclick={onClose} class="rounded-full p-2 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground" aria-label={$t('common.actions.close')}>&times;</button>
        </div>
        <div class="border-b border-border bg-card/80 p-5">
            <p class="mb-3 text-sm leading-6 text-muted-foreground">{$t('import.staged.merge_help')}</p>
            <input
                type="text"
                bind:value={query}
                placeholder={$t('library.search')}
                class="w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground shadow-sm focus:outline-none focus:ring-2 focus:ring-primary"
            />
        </div>
        <div class="flex-1 overflow-y-auto p-3">
            {#if isSearching}
                <div class="p-8 text-center text-muted-foreground">
                    <span class="inline-block h-6 w-6 border-2 border-primary/30 border-t-primary rounded-full animate-spin"></span>
                </div>
            {:else if results?.books && results.books.length > 0}
                <div class="space-y-2">
                    {#each results.books as libBook}
                        <button
                            disabled={processing}
                            onclick={() => onMerge(libBook.id)}
                            class="group flex w-full items-center gap-4 rounded-2xl border border-transparent px-4 py-3 text-left transition-all hover:border-primary/20 hover:bg-accent/80 disabled:opacity-50"
                        >
                                <div class="h-16 w-11 shrink-0 overflow-hidden rounded-lg border border-border bg-muted shadow-sm">
                                    {#if libBook.coverPath}
                                        <AuthenticatedImage
                                            src={`/api/books/${libBook.id}/cover`}
                                            alt=""
                                            class="w-full h-full object-cover"
                                        />
                                    {/if}
                                </div>
                            <div class="min-w-0 flex-1">
                                <div class="truncate font-bold text-foreground transition-colors group-hover:text-primary">{libBook.title}</div>
                                <div class="truncate text-sm text-muted-foreground">{libBook.authorNames?.join(', ') ?? ''}</div>
                            </div>
                            <div class="hidden rounded-full border border-primary/20 bg-primary/10 px-3 py-1 text-[11px] font-bold uppercase tracking-[0.24em] text-primary sm:block">
                                {$t('import.staged.merge')}
                            </div>
                        </button>
                    {/each}
                </div>
            {:else if query.length >= 2}
                <div class="p-8 text-center text-muted-foreground">
                    {$t('common.search.no_results', { query })}
                </div>
            {:else}
                <div class="p-8 text-center text-muted-foreground">
                    {$t('common.search.anything')}
                </div>
            {/if}
        </div>
        <div class="flex justify-end border-t border-border bg-muted/20 p-4">
            <button onclick={onClose} disabled={processing} class="rounded-xl border border-border bg-accent px-4 py-2 font-bold text-foreground transition-colors hover:bg-accent/80 disabled:opacity-50">
                {$t('common.actions.cancel')}
            </button>
        </div>
    </div>
</div>
