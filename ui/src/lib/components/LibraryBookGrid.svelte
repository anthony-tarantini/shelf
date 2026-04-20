<script lang="ts">
    import {t} from '$lib/i18n';
    import {MediaType} from '$lib/types/models';
    import type {BookSummary, BookAggregate} from '$lib/types/models';
    import {resolve} from '$app/paths';
    import MediaTypeBadge from './ui/MediaTypeBadge.svelte';
    import AuthenticatedImage from './ui/AuthenticatedImage.svelte';
    import ReadStatusBadge from './book/ReadStatusBadge.svelte';

    type BookResult = BookSummary | BookAggregate;

    let {books} = $props<{ books: BookResult[] }>();

    function getAuthorNames(book: BookResult): string[] {
        if ('authors' in book) {
            return Array.isArray(book.authors) ? book.authors.map((author) => author.name) : [];
        }
        return book.authorNames;
    }

    function getFormats(book: BookResult): MediaType[] {
        if ('metadata' in book && book.metadata?.editions) {
            return [...new Set(book.metadata.editions.map((edition) => edition.edition.format))];
        }
        return [MediaType.EBOOK];
    }
</script>

<div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
    {#each books as book ('book' in book ? book.book.id : book.id)}
        {@const id = 'book' in book ? book.book.id : book.id}
        {@const title = 'book' in book ? book.book.title : book.title}
        {@const coverPath = 'book' in book ? book.book.coverPath : book.coverPath}
        {@const formats = getFormats(book)}
        {@const authorNames = getAuthorNames(book)}
        {@const userState = book.userState}
        <a href={resolve(`/books/${id}`)} class="bg-card/80 border border-border rounded-[1.5rem] p-3 shadow-xl hover:border-primary/50 hover:-translate-y-1 transition-all group flex items-center gap-4">
            <div class="w-20 h-28 shrink-0 rounded-lg overflow-hidden bg-background isolate">
                {#if coverPath}
                    <AuthenticatedImage src={`/api/books/${id}/cover?v=${encodeURIComponent(coverPath)}`} alt={title} class="h-full w-full object-contain p-1 transition-opacity duration-300 group-hover:opacity-90" />
                {:else}
                    <div class="flex h-full w-full items-center justify-center text-[9px] font-bold text-muted-foreground text-center p-1 leading-tight">
                        {title}
                    </div>
                {/if}
            </div>
            <div class="min-w-0">
                <div class="mb-1 flex flex-wrap gap-1">
                    {#if userState}
                        <ReadStatusBadge status={userState.readStatus} />
                    {/if}
                    {#each formats as format}
                        <MediaTypeBadge type={format} />
                    {/each}
                </div>
                <h3 class="font-display text-xl font-bold text-foreground group-hover:text-primary transition-colors truncate">
                    {title}
                </h3>
                <p class="text-muted-foreground mt-1 text-sm truncate">
                    {authorNames.length > 0 ? authorNames.join(', ') : $t('common.unknown_author')}
                </p>
            </div>
        </a>
    {/each}
</div>
