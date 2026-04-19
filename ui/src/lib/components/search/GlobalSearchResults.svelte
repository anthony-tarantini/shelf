<script lang="ts">
    import {t} from '$lib/i18n';
    import {resolve} from '$app/paths';
    import type {GlobalSearchResult} from '$lib/types/models';
    import AuthenticatedImage from '$lib/components/ui/AuthenticatedImage.svelte';
    import EmptyState from '$lib/components/ui/EmptyState/EmptyState.svelte';
    import ReadStatusBadge from '$lib/components/book/ReadStatusBadge.svelte';

    let {
        results,
        query,
        onSelect
    } = $props<{
        results: GlobalSearchResult | null;
        query: string;
        onSelect: () => void;
    }>();
</script>

<div class="max-h-[60vh] overflow-y-auto">
    {#if results}
        {#if results.books.length === 0 && results.authors.length === 0 && results.series.length === 0}
            <div class="p-6">
                <EmptyState
                    eyebrow={$t('common.search.quick_jump')}
                    title={$t('common.search.no_results_title')}
                    message={$t('common.search.no_results', {query})}
                />
            </div>
        {:else}
            {#if results.books.length > 0}
                <div class="p-2">
                    <h3 class="px-3 py-2 text-xs font-bold uppercase tracking-wider text-muted-foreground">{$t('common.search.results_books')}</h3>
                    <div class="space-y-1">
                        {#each results.books as book}
                            <a
                                    href={resolve(`/books/${book.id}`)}
                                    onclick={onSelect}
                                    class="flex items-center gap-3 px-3 py-2 rounded-lg hover:bg-accent transition-colors group"
                            >
                                <div class="w-10 h-14 bg-muted rounded shadow-sm overflow-hidden shrink-0">
                                    {#if book.coverPath}
                                        <AuthenticatedImage
                                            src={`/api/books/${book.id}/cover?v=${encodeURIComponent(book.coverPath)}`}
                                            alt=""
                                            class="w-full h-full object-cover"
                                        />
                                    {/if}
                                </div>
                                <div class="min-w-0">
                                    <div class="font-bold text-foreground truncate group-hover:text-primary transition-colors">{book.title}</div>
                                    <div class="flex items-center gap-2">
                                        <div class="text-sm text-muted-foreground truncate">{book.authorNames?.join(', ') ?? ''}</div>
                                        {#if book.userState}
                                            <ReadStatusBadge status={book.userState.readStatus} />
                                        {/if}
                                    </div>
                                </div>
                            </a>
                        {/each}
                    </div>
                </div>
            {/if}

            {#if results.authors.length > 0}
                <div class="p-2 border-t border-border">
                    <h3 class="px-3 py-2 text-xs font-bold uppercase tracking-wider text-muted-foreground">{$t('common.search.results_authors')}</h3>
                    <div class="grid grid-cols-2 gap-1">
                        {#each results.authors as author}
                            <a
                                    href={resolve(`/authors/${author.id}`)}
                                    onclick={onSelect}
                                    class="flex items-center justify-between px-3 py-2 rounded-lg hover:bg-accent transition-colors group"
                            >
                                <span class="font-bold text-foreground truncate group-hover:text-primary transition-colors">{author.name}</span>
                                <span class="text-xs text-muted-foreground shrink-0">{$t("common.search.book_count", {bookCount: author.bookCount}) }</span>
                            </a>
                        {/each}
                    </div>
                </div>
            {/if}

            {#if results.series.length > 0}
                <div class="p-2 border-t border-border">
                    <h3 class="px-3 py-2 text-xs font-bold uppercase tracking-wider text-muted-foreground">
                        {$t('common.search.results_series')}</h3>
                    <div class="grid grid-cols-2 gap-1">
                        {#each results.series as s}
                            <a
                                    href={resolve(`/series/${s.id}`)}
                                    onclick={onSelect}
                                    class="flex items-center justify-between px-3 py-2 rounded-lg hover:bg-accent transition-colors group"
                            >
                                <span class="font-bold text-foreground truncate group-hover:text-primary transition-colors">{s.name}</span>
                                <span class="text-xs text-muted-foreground shrink-0">{$t("common.search.book_count", {bookCount: s.bookCount}) }</span>
                            </a>
                        {/each}
                    </div>
                </div>
            {/if}
        {/if}
    {:else if query.length === 0}
        <div class="p-8 text-center text-muted-foreground flex flex-col items-center gap-2">
            <div class="p-3 rounded-full bg-accent">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24"
                     stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                          d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
                </svg>
            </div>
            <p>{$t('common.search.anything')}</p>
        </div>
    {/if}
</div>
