<script lang="ts">
    import {browser} from '$app/environment';
    import {t} from '$lib/i18n';
    import EmptyState from '$lib/components/ui/EmptyState/EmptyState.svelte';
    import {MediaType, type BookPage, type ReadStatus} from '$lib/types/models';
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
    let titleFilter = $state<string>('');
    let authorFilter = $state<string>('');
    let seriesFilter = $state<string>('');
    let statusFilter = $state<ReadStatus | ''>('');
    let formatFilter = $state<MediaType | ''>('');

    let appliedTitleFilter = $state<string>('');
    let appliedAuthorFilter = $state<string>('');
    let appliedSeriesFilter = $state<string>('');
    let appliedStatusFilter = $state<ReadStatus | ''>('');
    let appliedFormatFilter = $state<MediaType | ''>('');
    let initializedFilters = false;

    $effect(() => {
        if (initializedFilters) return;
        const initialFilters = data.initialFilters ?? {};
        titleFilter = (initialFilters.title as string | undefined) ?? '';
        authorFilter = (initialFilters.author as string | undefined) ?? '';
        seriesFilter = (initialFilters.series as string | undefined) ?? '';
        statusFilter = (initialFilters.status as ReadStatus | undefined) ?? '';
        formatFilter = (initialFilters.format as MediaType | undefined) ?? '';
        appliedTitleFilter = titleFilter;
        appliedAuthorFilter = authorFilter;
        appliedSeriesFilter = seriesFilter;
        appliedStatusFilter = statusFilter;
        appliedFormatFilter = formatFilter;
        initializedFilters = true;
    });

    $effect(() => {
        void lastRefresh;
        refreshData(
            currentPage,
            pageSize,
            sortBy,
            sortDir,
            appliedTitleFilter,
            appliedAuthorFilter,
            appliedSeriesFilter,
            appliedStatusFilter,
            appliedFormatFilter
        );
    });

    async function refreshData(
        p: number,
        s: number,
        sort: string,
        dir: string,
        title: string,
        author: string,
        series: string,
        status: ReadStatus | '',
        format: MediaType | ''
    ) {
        const params = new URLSearchParams({
            page: String(p),
            size: String(s),
            sortBy: sort,
            sortDir: dir
        });
        if (title) params.set('title', title);
        if (author) params.set('author', author);
        if (series) params.set('series', series);
        if (status) params.set('status', status);
        if (format) params.set('format', format);

        const result = await api.get<BookPage>(`/books/page?${params.toString()}`);
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

    function applyFilters() {
        appliedTitleFilter = titleFilter.trim();
        appliedAuthorFilter = authorFilter.trim();
        appliedSeriesFilter = seriesFilter.trim();
        appliedStatusFilter = statusFilter;
        appliedFormatFilter = formatFilter;
        currentPage = 0;
        syncFiltersToUrl({
            title: appliedTitleFilter,
            author: appliedAuthorFilter,
            series: appliedSeriesFilter,
            status: appliedStatusFilter,
            format: appliedFormatFilter
        });
    }

    function clearFilters() {
        titleFilter = '';
        authorFilter = '';
        seriesFilter = '';
        statusFilter = '';
        formatFilter = '';
        appliedTitleFilter = '';
        appliedAuthorFilter = '';
        appliedSeriesFilter = '';
        appliedStatusFilter = '';
        appliedFormatFilter = '';
        currentPage = 0;
        syncFiltersToUrl({
            title: '',
            author: '',
            series: '',
            status: '',
            format: ''
        });
    }

    function syncFiltersToUrl(filters: {
        title: string;
        author: string;
        series: string;
        status: ReadStatus | '';
        format: MediaType | '';
    }) {
        if (!browser) return;

        const params = new URLSearchParams(window.location.search);
        if (filters.title) params.set('title', filters.title);
        else params.delete('title');

        if (filters.author) params.set('author', filters.author);
        else params.delete('author');

        if (filters.series) params.set('series', filters.series);
        else params.delete('series');

        if (filters.status) params.set('status', filters.status);
        else params.delete('status');

        if (filters.format) params.set('format', filters.format);
        else params.delete('format');

        const query = params.toString();
        const nextUrl = `${window.location.pathname}${query ? `?${query}` : ''}`;
        window.history.replaceState(window.history.state, '', nextUrl);
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

<svelte:head>
    <title>{$t('common.library')} | Shelf</title>
</svelte:head>

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

        <form class="mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-5" onsubmit={(event) => {
            event.preventDefault();
            applyFilters();
        }}>
            <label class="block">
                <span class="mb-1 block text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('library.filters.title_label')}</span>
                <input
                    type="text"
                    bind:value={titleFilter}
                    placeholder={$t('library.filters.title_placeholder')}
                    class="w-full rounded-lg border border-border bg-background/80 px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
            </label>

            <label class="block">
                <span class="mb-1 block text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('library.filters.author_label')}</span>
                <input
                    type="text"
                    bind:value={authorFilter}
                    placeholder={$t('library.filters.author_placeholder')}
                    class="w-full rounded-lg border border-border bg-background/80 px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
            </label>

            <label class="block">
                <span class="mb-1 block text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('library.filters.series_label')}</span>
                <input
                    type="text"
                    bind:value={seriesFilter}
                    placeholder={$t('library.filters.series_placeholder')}
                    class="w-full rounded-lg border border-border bg-background/80 px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/30"
                />
            </label>

            <label class="block">
                <span class="mb-1 block text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('library.filters.status_label')}</span>
                <select
                    bind:value={statusFilter}
                    class="w-full rounded-lg border border-border bg-background/80 px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/30"
                >
                    <option value="">{$t('library.filters.all_statuses')}</option>
                    <option value="UNREAD">{$t('books.read_status.options.unread')}</option>
                    <option value="READING">{$t('books.read_status.options.reading')}</option>
                    <option value="FINISHED">{$t('books.read_status.options.finished')}</option>
                    <option value="ABANDONED">{$t('books.read_status.options.abandoned')}</option>
                    <option value="QUEUED">{$t('books.read_status.options.queued')}</option>
                </select>
            </label>

            <label class="block">
                <span class="mb-1 block text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('library.filters.format_label')}</span>
                <select
                    bind:value={formatFilter}
                    class="w-full rounded-lg border border-border bg-background/80 px-3 py-2 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-primary/30"
                >
                    <option value="">{$t('library.filters.all_formats')}</option>
                    <option value={MediaType.EBOOK}>{$t('common.ebook')}</option>
                    <option value={MediaType.AUDIOBOOK}>{$t('common.audiobook')}</option>
                </select>
            </label>

            <div class="sm:col-span-2 lg:col-span-5 flex items-center gap-2">
                <button
                    type="submit"
                    class="inline-flex min-h-10 items-center justify-center rounded-full border border-primary/40 bg-primary/10 px-4 py-2 text-sm font-medium text-foreground transition-all hover:bg-primary/20 focus:outline-none focus:ring-2 focus:ring-primary/30"
                >
                    {$t('library.filters.apply')}
                </button>
                <button
                    type="button"
                    onclick={clearFilters}
                    class="inline-flex min-h-10 items-center justify-center rounded-full border border-border/70 bg-background/80 px-4 py-2 text-sm font-medium text-foreground transition-all hover:border-primary/40 hover:bg-card focus:outline-none focus:ring-2 focus:ring-primary/30"
                >
                    {$t('library.filters.clear')}
                </button>
            </div>
        </form>
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
