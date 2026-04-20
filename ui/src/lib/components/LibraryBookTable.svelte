<script lang="ts">
    import type {BookAggregate} from '../types/models.ts';
    import {resolve} from '$app/paths';
    import {t} from '$lib/i18n';
    import {MediaType} from '$lib/types/models';
    import MediaTypeBadge from './ui/MediaTypeBadge.svelte';
    import AuthenticatedImage from './ui/AuthenticatedImage.svelte';
    import ReadStatusBadge from './book/ReadStatusBadge.svelte';

    interface Props {
        books: BookAggregate[];
        sortBy?: string;
        sortDir?: 'ASC' | 'DESC';
    }

    let {
        books,
        sortBy = $bindable('createdAt'),
        sortDir = $bindable('DESC'),
    }: Props = $props();

    function handleSort(column: string) {
        if (sortBy === column) {
            sortDir = sortDir === 'ASC' ? 'DESC' : 'ASC';
        } else {
            sortBy = column;
            sortDir = 'ASC';
        }
    }
</script>

<div class="overflow-x-auto rounded-lg border border-border shadow-xl">
    <table class="w-full border-collapse text-left text-sm">
        <thead class="sticky top-0 z-10 bg-card/95 text-muted-foreground backdrop-blur">
        <tr>
            <th class="p-3 w-16">{$t('library.table.cover')}</th>
            <th class="p-3 cursor-pointer hover:text-foreground transition-colors" onclick={() => handleSort('title')}>
                {$t('library.table.title')} {sortBy === 'title' ? (sortDir === 'ASC' ? '↑' : '↓') : ''}
            </th>
            <th class="p-3 cursor-pointer hover:text-foreground transition-colors" onclick={() => handleSort('author')}>
                {$t('library.table.authors')} {sortBy === 'author' ? (sortDir === 'ASC' ? '↑' : '↓') : ''}
            </th>
            <th class="p-3 cursor-pointer hover:text-foreground transition-colors"
                onclick={() => handleSort('mediaType')}>
                {$t('library.table.type')} {sortBy === 'mediaType' ? (sortDir === 'ASC' ? '↑' : '↓') : ''}
            </th>
            <th class="p-3">{$t('books.read_status.label')}</th>
        </tr>
        </thead>
        <tbody class="divide-y divide-border bg-card/80">
        {#each books as book (book.book.id)}
            {@const formats = [...new Set(book.metadata?.editions?.map(e => e.edition.format) ?? [MediaType.EBOOK])]}
            <tr class="group transition-colors hover:bg-accent/30">
                <td class="p-3">
                    <a href={resolve(`/books/${book.book.id}`)} class="block">
                        {#if book.book.coverPath}
                            <AuthenticatedImage
                                src={`/api/books/${book.book.id}/cover?v=${encodeURIComponent(book.book.coverPath)}`}
                                alt=""
                                class="w-10 h-14 object-cover rounded shadow-sm bg-background hover:opacity-80 transition-opacity"
                            />
                        {:else}
                            <div class="w-10 h-14 bg-background rounded border border-border flex items-center justify-center text-[8px] text-muted-foreground text-center px-1 hover:opacity-80 transition-opacity">
                                No Cover
                            </div>
                        {/if}
                    </a>
                </td>
                <td class="p-3 font-medium text-foreground">
                    <a href={resolve(`/books/${book.book.id}`)} class="hover:text-primary transition-colors">
                        {book.book.title}
                    </a>
                </td>
                <td class="p-3 text-muted-foreground truncate max-w-37.5">
                    {book.authors?.length > 0 ? book.authors.map((a) => a.name).join(', ') : $t('common.unknown_author')}
                </td>
                <td class="p-3">
                    <div class="flex flex-wrap gap-1">
                        {#each formats as format}
                            <MediaTypeBadge type={format} />
                        {/each}
                    </div>
                </td>
                <td class="p-3">
                    {#if book.userState}
                        <ReadStatusBadge status={book.userState.readStatus} />
                    {/if}
                </td>
            </tr>
        {/each}
        </tbody>
    </table>
</div>
