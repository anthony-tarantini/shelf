<script lang="ts">
    import {t} from '$lib/i18n';
    import type {StagedBook} from '../types/models.ts';
    import {MediaType} from '../types/models.ts';
    import {safeHtml} from '../actions/safeHtml.ts';
    import {formatDuration} from '../utils.ts';
    import type {Snippet} from 'svelte';

    interface Props {
        book: StagedBook;
        processing: boolean;
        isFetchingMetadata: boolean;
        onEdit: () => void;
        onFetchMetadata: () => void;
        onPromote: () => void;
        onMerge: () => void;
        onDelete: () => void;
        children?: Snippet;
    }

    let {
        book,
        processing,
        isFetchingMetadata,
        onEdit,
        onFetchMetadata,
        onPromote,
        onMerge,
        onDelete,
        children
    }: Props = $props();

    function formatDate(date: string) {
        if (!date) return $t('dates.unknown');
        const d = new Date(date);
        return isNaN(d.getTime()) ? $t('dates.unknown') : d.toLocaleString();
    }
    const primaryEdition = $derived(book.ebookMetadata ?? book.audiobookMetadata);
    const totalTime = $derived(book.audiobookMetadata?.totalTime);
</script>

<div class="flex flex-wrap justify-between items-start gap-4">
    <div>
        <p class="mb-2 text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{$t('import.staged.item_eyebrow')}</p>
        <h3 class="font-display text-3xl font-bold text-foreground">{book.title}</h3>
        <p class="text-muted-foreground mt-2">{$t('import.staged.uploaded', {date: formatDate(book.createdAt)})}</p>
    </div>
    <div class="flex gap-2 flex-wrap justify-end">
        <button
                onclick={onEdit}
                class="bg-accent hover:bg-gray-600 text-foreground font-bold py-2 px-4 rounded-xl transition-colors">
            {$t('common.edit')}
        </button>
        <button
                onclick={onFetchMetadata}
                disabled={isFetchingMetadata || processing}
                class="bg-primary hover:bg-primary/90 disabled:bg-muted text-primary-foreground font-bold py-2 px-4 rounded-xl transition-colors flex items-center"
        >
            {#if isFetchingMetadata}
                <span class="mr-2 h-4 w-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
            {/if}
            {$t('metadata.fetch')}
        </button>
        <button
                onclick={onMerge}
                disabled={processing}
                class="bg-primary hover:bg-primary disabled:bg-muted text-primary-foreground font-bold py-2 px-4 rounded-xl transition-colors flex items-center"
        >
            <svg xmlns="http://www.w3.org/2000/svg" class="mr-2 h-4 w-4" fill="none" viewBox="0 0 24 24"
                 stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                      d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"/>
            </svg>
            {$t('import.staged.merge_existing')}
        </button>
        <button
                onclick={onPromote}
                disabled={processing}
                class="bg-primary hover:bg-primary disabled:bg-muted text-primary-foreground font-bold py-2 px-4 rounded-xl transition-colors flex items-center"
        >
            {#if processing}
                <span class="mr-2 h-4 w-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
            {/if}
            {$t('import.staged.promote')}
        </button>
        <button
                onclick={onDelete}
                disabled={processing}
                class="bg-destructive hover:bg-destructive/90 disabled:bg-muted text-destructive-foreground font-bold py-2 px-4 rounded-xl transition-colors"
        >
            {$t('import.staged.delete')}
        </button>
    </div>
</div>

{@render children?.()}

<div class="mt-5 grid grid-cols-1 gap-4 text-sm md:grid-cols-2">
    <div>
        <span class="text-muted-foreground block">{$t('common.authors')}</span>
        <span class="text-muted-foreground">{book.authors.join(', ') || $t('common.unknown')}</span>
    </div>
    <div>
        <span class="text-muted-foreground block">{$t('common.series')}</span>
        <span class="text-muted-foreground">{book.series && book.series[0] ? `${book.series[0].name} #${book.series[0].index}` : $t('common.none')}</span>
    </div>
    <div>
        <span class="text-muted-foreground block">{$t('common.genres')}</span>
        <span class="text-muted-foreground">{book.genres.join(', ') || $t('common.none')}</span>
    </div>
    <div>
        <span class="text-muted-foreground block">{$t('common.ISBN')}</span>
        <span class="text-muted-foreground">
            {primaryEdition?.isbn13 ?? primaryEdition?.isbn10 ?? $t('common.none')}
        </span>
    </div>
    {#if book.mediaType === 'AUDIOBOOK' && totalTime}
        <div>
            <span class="text-muted-foreground block">{$t('common.total_time')}</span>
            <span class="text-muted-foreground">{formatDuration(totalTime)}</span>
        </div>
    {/if}
</div>

{#if book.description}
    <div class="mt-4">
        <span class="text-muted-foreground text-sm block">{$t('common.description')}</span>
        <p class="text-muted-foreground text-sm mt-1 line-clamp-3">
            <span use:safeHtml={book.description}></span>
        </p>
    </div>
{/if}

{#if book.chapters && book.chapters.length > 0}
    <div class="mt-6">
        <h4 class="text-xs font-bold text-muted-foreground uppercase tracking-wider mb-2">{$t('common.chapters')}</h4>
        <div class="bg-background/50 rounded-md border border-border divide-y divide-gray-700 max-h-48 overflow-y-auto custom-scrollbar">
            {#each book.chapters as chapter (chapter.id)}
                <div class="px-3 py-2 flex justify-between items-center text-xs">
                    <span class="text-muted-foreground">{chapter.title}</span>
                    <div class="flex gap-4">
                        {#if chapter.startTime !== undefined}
                            <span class="text-muted-foreground font-mono">{formatDuration(chapter.startTime)}</span>
                        {/if}
                        {#if chapter.endTime !== undefined && chapter.startTime !== undefined}
                            <span class="text-primary/70 font-mono">({formatDuration(chapter.endTime - chapter.startTime)}
                                )</span>
                        {/if}
                    </div>
                </div>
            {/each}
        </div>
    </div>
{/if}
