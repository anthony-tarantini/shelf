<script lang="ts">
    import {t} from '$lib/i18n';
    import type {StagedBook} from '../types/models.ts';
    import MediaTypeBadge from './ui/MediaTypeBadge.svelte';
    import AuthenticatedImage from './ui/AuthenticatedImage.svelte';
    import RowActionButton from './ui/RowActionButton.svelte';

    interface Props {
        book: StagedBook;
        selected?: boolean;
        expanded?: boolean;
        processing?: boolean;
        error?: string | null;
        onSelect: () => void;
        onExpand: () => void;
        onPromote: () => void;
        onMerge: () => void;
        onDelete: () => void;
        onShowMetadata: () => void;
        onShowEdit: () => void;
    }

    let {
        book,
        selected = false,
        expanded = false,
        processing = false,
        error = null,
        onSelect,
        onExpand,
        onPromote,
        onMerge,
        onDelete,
        onShowMetadata,
        onShowEdit
    }: Props = $props();

    function formatDate(date: string) {
        if (!date) return t.get('common.unknown');
        const d = new Date(date);
        return isNaN(d.getTime()) ? t.get('common.unknown') : d.toLocaleDateString();
    }

    function handleRowToggle() {
        onExpand();
    }

    function handleTitleClick(event: MouseEvent) {
        event.stopPropagation();
        onExpand();
    }
</script>

<tr class="hover:bg-accent/30 transition-colors group {expanded ? 'bg-accent/20' : ''}">
    <td class="p-3">
        <input
                type="checkbox"
                checked={selected}
                onchange={onSelect}
                class="rounded border-border bg-background text-primary focus:ring-primary"
        />
    </td>
    <td class="p-3 cursor-pointer" onclick={handleRowToggle}>
        {#if book.coverPath}
            <AuthenticatedImage src={`/api/books/staged/${book.id}/cover`} alt="" class="w-10 h-14 object-cover rounded shadow-sm bg-background"/>
        {:else}
            <div class="w-10 h-14 bg-background rounded border border-border flex items-center justify-center text-[8px] text-muted-foreground text-center px-1">{$t('common.no_cover')}</div>
        {/if}
    </td>
    <td class="p-3 font-medium text-foreground cursor-pointer" onclick={handleRowToggle}>
        <button class="text-left hover:text-primary transition-colors" onclick={handleTitleClick}>
            {book.title}
        </button>
        {#if error}
            <div class="text-[10px] text-destructive mt-1">{error}</div>
        {/if}
    </td>
    <td class="p-3 text-muted-foreground truncate max-w-37.5 cursor-pointer" title={book.authors.join(', ')} onclick={handleRowToggle}>
        {book.authors.join(', ') || t.get('common.unknown')}
    </td>
    <td class="p-3 cursor-pointer" onclick={handleRowToggle}>
        <MediaTypeBadge type={book.mediaType} />
    </td>
    <td class="p-3 text-muted-foreground whitespace-nowrap cursor-pointer" onclick={handleRowToggle}>
        {formatDate(book.createdAt)}
    </td>
    <td class="p-3 text-right">
        <div class="flex justify-end gap-1 opacity-0 transition-opacity group-hover:opacity-100 focus-within:opacity-100">
            <RowActionButton
                    onclick={onShowMetadata}
                    title={$t("import.staged.fetch")}
            >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
            </RowActionButton>
            <RowActionButton
                    onclick={onShowEdit}
                    title={$t('import.staged.edit')}
            >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
            </RowActionButton>
            <RowActionButton
                    onclick={onMerge}
                    title={$t('import.staged.merge')}
            >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
                </svg>
            </RowActionButton>
            <RowActionButton
                    onclick={onPromote}
                    disabled={processing}
                    title={$t('import.staged.promote')}
            >
                {#if processing}
                    <span class="h-4 w-4 border-2 border-primary/30 border-t-primary rounded-full animate-spin block"></span>
                {:else}
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
                    </svg>
                {/if}
            </RowActionButton>
            <RowActionButton
                    onclick={onDelete}
                    disabled={processing}
                    title={$t('import.staged.delete')}
                    variant="destructive"
            >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
            </RowActionButton>
        </div>
    </td>
</tr>
