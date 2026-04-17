<script lang="ts">
    import { api } from '$lib/api/client';
    import { t } from '$lib/i18n';
    import BookCover from '$lib/components/book/BookCover.svelte';
    import StatusBanner from '$lib/components/ui/StatusBanner.svelte';
    import type { BookUserState, MetadataAggregate, ReadStatus } from '$lib/types/models';
    import ReadStatusBadge from './ReadStatusBadge.svelte';

    let {
        bookId,
        coverPath,
        title,
        metadata,
        userState = null
    } = $props<{
        bookId: string;
        coverPath?: string;
        title: string;
        metadata?: MetadataAggregate;
        userState?: BookUserState | null;
    }>();

    const primaryRecord = $derived(metadata?.metadata);
    const editions = $derived(metadata?.editions ?? []);
    let readStatus = $state<ReadStatus>('UNREAD');
    let statusError = $state<string | null>(null);
    let savingStatus = $state(false);

    $effect(() => {
        readStatus = userState?.readStatus ?? 'UNREAD';
    });

    async function updateReadStatus(nextStatus: ReadStatus) {
        if (nextStatus === readStatus || savingStatus) return;
        const previous = readStatus;
        readStatus = nextStatus;
        savingStatus = true;
        statusError = null;

        const result = await api.put(`/books/${bookId}/status`, { status: nextStatus });
        savingStatus = false;

        if (result.left) {
            readStatus = previous;
            statusError = result.left.message;
        }
    }
</script>

<aside class="space-y-6">
    <BookCover {bookId} {coverPath} {title}/>

    <div class="space-y-6">
        <div>
            <h3 class="text-xs font-bold text-muted-foreground uppercase tracking-wider mb-1">
                {$t('books.read_status.label')}</h3>
            <div class="flex flex-col gap-2 sm:flex-row sm:items-center">
                <select
                    class="ui-select"
                    value={readStatus}
                    disabled={savingStatus}
                    onchange={(e) => updateReadStatus((e.currentTarget as HTMLSelectElement).value as ReadStatus)}
                    aria-label={$t('books.read_status.label')}
                >
                    <option value="UNREAD">{$t('books.read_status.options.unread')}</option>
                    <option value="READING">{$t('books.read_status.options.reading')}</option>
                    <option value="FINISHED">{$t('books.read_status.options.finished')}</option>
                    <option value="ABANDONED">{$t('books.read_status.options.abandoned')}</option>
                    <option value="QUEUED">{$t('books.read_status.options.queued')}</option>
                </select>
                <ReadStatusBadge status={readStatus} />
            </div>
            {#if statusError}
                <div class="mt-3">
                    <StatusBanner kind="error" title={$t('books.read_status.update_failed')} message={statusError} compact={true} />
                </div>
            {/if}
        </div>

        <div>
            <h3 class="text-xs font-bold text-muted-foreground uppercase tracking-wider mb-1">
                {$t('books.sidebar.publisher')}</h3>
            {#if primaryRecord?.publisher}
                <p class="text-foreground">{primaryRecord.publisher}</p>
            {:else}
                <p class="text-muted-foreground italic text-sm">{$t('books.sidebar.not_set')}</p>
            {/if}
        </div>

        <div>
            <h3 class="text-xs font-bold text-muted-foreground uppercase tracking-wider mb-1">
                {$t('books.sidebar.published_year')}</h3>
            {#if primaryRecord?.published}
                <p class="text-foreground">{primaryRecord.published}</p>
            {:else}
                <p class="text-muted-foreground italic text-sm">{$t('books.sidebar.not_set')}</p>
            {/if}
        </div>

        <div>
            <h3 class="text-xs font-bold text-muted-foreground uppercase tracking-wider mb-1">
                {$t('books.sidebar.identifiers')}</h3>
            {#if editions.length > 0}
                <div class="flex flex-col gap-3">
                    {#each editions as {edition} (edition.id)}
                        <div class="bg-background border border-border rounded p-2 text-sm text-muted-foreground">
                            <span class="font-bold text-xs uppercase block mb-1 text-primary">{edition.format}</span>
                            <div class="flex flex-col gap-0.5 font-mono">
                                {#if edition.isbn13}
                                    <div>{$t('metadata.comparison.identifier_isbn13')}: {edition.isbn13}</div>
                                {/if}
                                {#if edition.isbn10}
                                    <div>{$t('metadata.comparison.identifier_isbn10')}: {edition.isbn10}</div>
                                {/if}
                                {#if edition.asin}
                                    <div>{$t('metadata.comparison.identifier_asin')}: {edition.asin}</div>
                                {/if}
                                {#if !edition.isbn13 && !edition.isbn10 && !edition.asin}
                                    <span class="italic text-xs">{$t('books.sidebar.no_identifiers')}</span>
                                {/if}
                            </div>
                        </div>
                    {/each}
                </div>
            {:else}
                <p class="text-muted-foreground italic text-sm">{$t('books.sidebar.not_set')}</p>
            {/if}
        </div>

        <div>
            <h3 class="text-xs font-bold text-muted-foreground uppercase tracking-wider mb-1">
                {$t('books.sidebar.genres')}</h3>
            {#if primaryRecord?.genres && primaryRecord.genres.length > 0}
                <div class="flex flex-wrap gap-2 mt-2">
                    {#each primaryRecord.genres as genre (genre)}
                        <span class="px-2.5 py-1 bg-card border border-border rounded text-xs text-muted-foreground font-medium">
                            {genre}
                        </span>
                    {/each}
                </div>
            {:else}
                <p class="text-muted-foreground italic text-sm">{$t('common.none')}</p>
            {/if}
        </div>
    </div>
</aside>
