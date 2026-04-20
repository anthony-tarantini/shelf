<script lang="ts">
    import {t} from '$lib/i18n';
    import EmptyState from '$lib/components/ui/EmptyState/EmptyState.svelte';
    import type { BookPage } from '$lib/types/models';
    import { api } from '$lib/api/client';
    import LibraryBookTable from '$lib/components/LibraryBookTable.svelte';
    import LibraryBookGrid from '$lib/components/LibraryBookGrid.svelte';
    import Pagination from '$lib/components/ui/Pagination.svelte';

    let {data} = $props();

    let lastRefresh = $state(0);
    let remotePageData = $state<BookPage | null>(null);
    let pageData = $derived<BookPage>(remotePageData || data.bookPage);

    type ViewMode = 'grid' | 'table';
    type SortPreset = { sortBy: string; sortDir: 'ASC' | 'DESC' };

    const sortPresets: SortPreset[] = [
        { sortBy: 'createdAt', sortDir: 'DESC' },
        { sortBy: 'title', sortDir: 'ASC' },
        { sortBy: 'title', sortDir: 'DESC' },
        { sortBy: 'author', sortDir: 'ASC' },
        { sortBy: 'author', sortDir: 'DESC' }
    ];

    let viewMode = $state<ViewMode>('grid');
    let sortBy = $state('createdAt');
    let sortDir = $state<'ASC' | 'DESC'>('DESC');
    let currentPage = $state(0);
    let pageSize = $state(20);
    $effect(() => {
        void lastRefresh;
        refreshData(currentPage, pageSize, sortBy, sortDir);
    });

    async function refreshData(p: number, s: number, sort: string, dir: string) {
        const result = await api.get<BookPage>(`/books/page?page=${p}&size=${s}&sortBy=${sort}&sortDir=${dir}`);
        if (result.right) {
            remotePageData = result.right;
        }
    }

    function toggleViewMode() {
        viewMode = viewMode === 'grid' ? 'table' : 'grid';
    }

    function cycleSortPreset() {
        const currentIndex = sortPresets.findIndex((preset) => preset.sortBy === sortBy && preset.sortDir === sortDir);
        const nextPreset = sortPresets[(currentIndex + 1 + sortPresets.length) % sortPresets.length];
        sortBy = nextPreset.sortBy;
        sortDir = nextPreset.sortDir;
        currentPage = 0;
    }

    let currentSortLabel = $derived.by(() => {
        if (sortBy === 'createdAt') {
            return sortDir === 'DESC' ? t.get('library.dashboard.sort_newest') : t.get('library.dashboard.sort_oldest');
        }

        if (sortBy === 'author') {
            return sortDir === 'ASC' ? t.get('library.dashboard.sort_author_asc') : t.get('library.dashboard.sort_author_desc');
        }

        return sortDir === 'ASC' ? t.get('library.dashboard.sort_alpha_asc') : t.get('library.dashboard.sort_alpha_desc');
    });

</script>

<div class="mx-auto w-full">
    <header class="mb-6 rounded-[1.5rem] border border-border/70 bg-card/70 p-4 shadow-xl shadow-black/5 backdrop-blur-md sm:mb-8 sm:p-6">
        <div class="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
            <div>
                <p class="mb-2 text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{$t('library.toolbar.eyebrow')}</p>
                <h2 class="font-display text-3xl font-bold text-primary sm:text-4xl">{$t('common.library')}</h2>
                <p class="text-muted-foreground mt-2 max-w-2xl">{$t('library.page_subtitle')}</p>
            </div>

            <div class="grid w-full gap-3 sm:flex sm:flex-wrap sm:items-center lg:w-auto">
                <div class="inline-flex min-h-11 items-center justify-between gap-3 rounded-full border border-border/70 bg-background/80 px-4 py-2 text-sm font-medium text-foreground">
                    <span class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">
                        {$t('library.dashboard.books_label')}
                    </span>
                    <span>{pageData.totalCount}</span>
                </div>

                <button
                    type="button"
                    onclick={toggleViewMode}
                    class="inline-flex min-h-11 items-center justify-between gap-3 rounded-full border border-border/70 bg-background/80 px-4 py-2 text-sm font-medium text-foreground transition-all hover:border-primary/40 hover:bg-card focus:outline-none focus:ring-2 focus:ring-primary/30 sm:min-w-[9rem]"
                >
                    <span class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">
                        {$t('library.dashboard.view_label')}
                    </span>
                    <span class="capitalize">{viewMode}</span>
                </button>

                <button
                    type="button"
                    onclick={cycleSortPreset}
                    class="inline-flex min-h-11 items-center justify-between gap-3 rounded-full border border-border/70 bg-background/80 px-4 py-2 text-sm font-medium text-foreground transition-all hover:border-primary/40 hover:bg-card focus:outline-none focus:ring-2 focus:ring-primary/30 sm:min-w-[11rem]"
                >
                    <span class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">
                        {$t('library.dashboard.sort_label')}
                    </span>
                    <span>{currentSortLabel}</span>
                </button>
            </div>
        </div>
    </header>

    {#if !pageData || pageData.items.length === 0}
        <EmptyState
                message={$t('library.empty')}
                actionText={$t('library.import_books')}
                href="/import"
        />
    {:else}
        {#if viewMode === 'grid'}
            <LibraryBookGrid books={pageData.items} />
        {:else}
            <LibraryBookTable
                    books={pageData.items}
                    bind:sortBy
                    bind:sortDir
            />
        {/if}

        <Pagination 
            bind:currentPage 
            totalCount={pageData.totalCount} 
            {pageSize} 
        />
    {/if}
</div>
