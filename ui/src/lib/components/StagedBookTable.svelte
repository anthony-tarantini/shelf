<script lang="ts">
    import {api} from '../api/client.ts';
    import type {StagedBook} from '../types/models.ts';
    import {invalidateAll} from '$app/navigation';
    import {t} from '$lib/i18n';
    import {SvelteSet} from "svelte/reactivity";
    import StagedBookRow from './StagedBookRow.svelte';
    import StagedBookExpansion from './StagedBookExpansion.svelte';
    import MergeDialog from './ui/MergeDialog.svelte';
    import ConfirmDialog from './ui/ConfirmDialog.svelte';

    interface Props {
        books: StagedBook[];
        selectedIds?: Set<string>;
        sortBy?: string;
        sortDir?: 'ASC' | 'DESC';
        onActionStart?: () => void;
        onActionEnd?: () => void;
        onActionSuccess?: () => void;
    }

    let {
        books,
        selectedIds = $bindable(new Set()),
        sortBy = $bindable('createdAt'),
        sortDir = $bindable('DESC'),
        onActionStart,
        onActionEnd,
        onActionSuccess
    }: Props = $props();

    let expandedId = $state<string | null>(null);
    let mergeDialogId = $state<string | null>(null);
    let viewMode = $state<Record<string, 'edit' | 'metadata'>>({});
    let processingIds = $state(new Set<string>());
    let fetchingMetadataIds = $state(new Set<string>());
    let errors = $state<Record<string, string | null>>({});
    let confirmDeleteId = $state<string | null>(null);

    function toggleExpand(id: string) {
        expandedId = expandedId === id ? null : id;
        if (expandedId && !viewMode[expandedId]) {
            viewMode[expandedId] = 'edit';
        }
    }

    function toggleSelectAll() {
        if (selectedIds.size === books.length) {
            selectedIds = new Set();
        } else {
            selectedIds = new Set(books.map((b: StagedBook) => b.id));
        }
    }

    function toggleSelect(id: string) {
        const next = new SvelteSet(selectedIds);
        if (next.has(id)) next.delete(id);
        else next.add(id);
        selectedIds = next;
    }

    function handleSort(column: string) {
        if (sortBy === column) {
            sortDir = sortDir === 'ASC' ? 'DESC' : 'ASC';
        } else {
            sortBy = column;
            sortDir = 'ASC';
        }
    }

    async function promote(id: string) {
        errors[id] = null;
        processingIds.add(id);
        onActionStart?.();
        const result = await api.post<unknown>(`/books/staged/${id}/promote`);
        if (result.left) {
            errors[id] = result.left.message;
        } else {
            onActionSuccess?.();
            await invalidateAll();
        }
        processingIds.delete(id);
        onActionEnd?.();
    }

    async function merge(id: string, targetBookId: string) {
        errors[id] = null;
        processingIds.add(id);
        onActionStart?.();
        const result = await api.post<unknown>(`/books/staged/${id}/merge`, { targetBookId });
        if (result.left) {
            errors[id] = result.left.message;
        } else {
            mergeDialogId = null;
            onActionSuccess?.();
            await invalidateAll();
        }
        processingIds.delete(id);
        onActionEnd?.();
    }

    async function remove(id: string) {
        errors[id] = null;
        processingIds.add(id);
        onActionStart?.();
        const result = await api.delete<unknown>(`/books/staged/${id}`);
        if (result.left) {
            errors[id] = result.left.message;
        } else {
            onActionSuccess?.();
            await invalidateAll();
        }
        processingIds.delete(id);
        onActionEnd?.();
        confirmDeleteId = null;
    }
</script>

<div class="overflow-x-auto rounded-[1.5rem] border border-border bg-card/80 shadow-xl shadow-black/5">
    <table class="w-full text-left text-sm border-collapse">
        <thead class="sticky top-0 z-10 bg-card/95 text-muted-foreground backdrop-blur">
        <tr>
            <th class="p-3 w-10">
                <input
                        type="checkbox"
                        checked={books.length > 0 && selectedIds.size === books.length}
                        indeterminate={selectedIds.size > 0 && selectedIds.size < books.length}
                        onchange={toggleSelectAll}
                        class="rounded border-border bg-background text-primary focus:ring-primary"
                />
            </th>
            <th class="p-3 w-16">{$t('library.table.cover')}</th>
            <th class="p-3 cursor-pointer hover:text-foreground transition-colors" onclick={() => handleSort('title')}>
                {$t('library.table.title')} {sortBy === 'title' ? (sortDir === 'ASC' ? '↑' : '↓') : ''}
            </th>
            <th class="p-3 cursor-pointer hover:text-foreground transition-colors" onclick={() => handleSort('author')}>
                {$t('library.table.authors')} {sortBy === 'author' ? (sortDir === 'ASC' ? '↑' : '↓') : ''}
            </th>
            <th class="p-3 cursor-pointer hover:text-foreground transition-colors" onclick={() => handleSort('mediaType')}>
                {$t('library.table.type')} {sortBy === 'mediaType' ? (sortDir === 'ASC' ? '↑' : '↓') : ''}
            </th>
            <th class="p-3 cursor-pointer hover:text-foreground transition-colors" onclick={() => handleSort('createdAt')}>
                {$t('library.table.added')} {sortBy === 'createdAt' ? (sortDir === 'ASC' ? '↑' : '↓') : ''}
            </th>
            <th class="p-3 text-right">{$t('library.table.actions')}</th>
        </tr>
        </thead>
        <tbody class="divide-y divide-border bg-card">
        {#each books as book (book.id)}
            <StagedBookRow
                    {book}
                    selected={selectedIds.has(book.id)}
                    expanded={expandedId === book.id}
                    processing={processingIds.has(book.id)}
                    error={errors[book.id]}
                    onSelect={() => toggleSelect(book.id)}
                    onExpand={() => toggleExpand(book.id)}
                    onPromote={() => promote(book.id)}
                    onMerge={() => mergeDialogId = book.id}
                    onDelete={() => confirmDeleteId = book.id}
                    onShowMetadata={() => { expandedId = book.id; viewMode[book.id] = 'metadata'; }}
                    onShowEdit={() => { expandedId = book.id; viewMode[book.id] = 'edit'; }}
            />
            {#if expandedId === book.id}
                <StagedBookExpansion
                        {book}
                        viewMode={viewMode[book.id] || 'edit'}
                        fetchingMetadata={fetchingMetadataIds.has(book.id)}
                        onClose={() => expandedId = null}
                        onViewModeChange={(mode) => viewMode[book.id] = mode}
                        onError={(msg) => errors[book.id] = msg}
                        {onActionSuccess}
                />
            {/if}
        {/each}
        </tbody>
    </table>
</div>

{#if mergeDialogId}
    {@const mergeBook = books.find((b) => b.id === mergeDialogId)}
    {#if mergeBook}
        <MergeDialog
                book={mergeBook}
                processing={processingIds.has(mergeDialogId)}
                onClose={() => mergeDialogId = null}
                onMerge={(targetId) => merge(mergeDialogId || '', targetId)}
        />
    {/if}
{/if}

<ConfirmDialog
    open={confirmDeleteId !== null}
    title={$t('import.confirmations.delete_staged')}
    message={$t('import.confirmations.delete_staged_from_queue_message')}
    confirmLabel={$t('common.actions.delete')}
    variant="destructive"
    processing={confirmDeleteId ? processingIds.has(confirmDeleteId) : false}
    onCancel={() => confirmDeleteId = null}
    onConfirm={() => {
        if (confirmDeleteId) {
            void remove(confirmDeleteId);
        }
    }}
/>
