<script lang="ts">
    import {t} from '$lib/i18n';
    import {invalidateAll} from '$app/navigation';
    import type {StagedBook} from '../types/models.ts';
    import StagedBookEditor from './StagedBookEditor.svelte';
    import MetadataManager from './metadata/MetadataManager.svelte';

    interface Props {
        book: StagedBook;
        viewMode: 'edit' | 'metadata';
        fetchingMetadata?: boolean;
        onClose: () => void;
        onViewModeChange: (mode: 'edit' | 'metadata') => void;
        onError: (msg: string | null) => void;
        onActionSuccess?: () => void;
    }

    let {
        book,
        viewMode = 'edit',
        fetchingMetadata = false,
        onClose,
        onViewModeChange,
        onError,
        onActionSuccess
    }: Props = $props();
</script>

<tr>
    <td colspan="7" class="p-6 bg-accent/10">
        <div class="max-w-4xl mx-auto rounded-[1.5rem] border border-border/70 bg-card/80 p-5 shadow-lg shadow-black/5">
            <div class="mb-4 flex gap-4 border-b border-border">
                <button
                        onclick={() => onViewModeChange('edit')}
                        class="px-4 py-2 text-sm font-bold transition-colors {viewMode === 'edit' ? 'text-primary border-b-2 border-primary' : 'text-muted-foreground hover:text-foreground'}"
                >
                    {$t('import.staged.expansion.edit')}
                </button>
                <button
                        onclick={() => onViewModeChange('metadata')}
                        class="px-4 py-2 text-sm font-bold transition-colors {viewMode === 'metadata' ? 'text-primary border-b-2 border-primary' : 'text-muted-foreground hover:text-foreground'}"
                >
                    {$t('import.staged.expansion.metadata')}
                </button>
                <button
                        onclick={onClose}
                        class="ml-auto px-4 py-2 text-sm text-muted-foreground hover:text-foreground"
                >
                    {$t('import.staged.expansion.close')}
                </button>
            </div>

            {#if viewMode === 'edit'}
                <StagedBookEditor
                        {book}
                        onCancel={onClose}
                        onSaveSuccess={async () => { onClose(); onActionSuccess?.(); await invalidateAll(); }}
                        onError={onError}
                />
            {:else if viewMode === 'metadata'}
                <MetadataManager
                        {book}
                        isFetching={fetchingMetadata}
                        onCancel={onClose}
                        onApplySuccess={async () => { onClose(); onActionSuccess?.(); await invalidateAll(); }}
                        onError={onError}
                />
            {/if}
        </div>
    </td>
</tr>
