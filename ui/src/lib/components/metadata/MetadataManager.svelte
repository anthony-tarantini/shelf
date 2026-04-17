<script lang="ts">
    import {api} from '../../api/client.ts';
    import type {StagedBook, ExternalMetadata} from '../../types/models.ts';
    import {MetadataState} from '../../states/metadataState.svelte.js';
    import { t } from '$lib/i18n';

    import MetadataLoadingState from './MetadataLoadingState.svelte';
    import MetadataResultsGrid from './MetadataResultsGrid.svelte';
    import MetadataComparisonTable from './MetadataComparisonTable.svelte';

    interface Props {
        book: StagedBook;
        onCancel: () => void;
        onApplySuccess: () => void;
        onError: (msg: string) => void;
        isFetching?: boolean;
    }

    let {
        book,
        onCancel,
        onApplySuccess,
        onError,
        isFetching = $bindable(false)
    }: Props = $props();

    // --- Central State ---

    // Instantiate the merge state manager, linked reactively to the 'book' prop.
    const m = new MetadataState(() => book);

    // UI State
    let metadataSearchError = $state<string>();
    let metadataResults = $state<ExternalMetadata[]>();
    let selectedExternal = $state<ExternalMetadata>();

    // --- Effects ---

    // Automatically trigger a search whenever the book ID changes.
    $effect(() => {
        if (book.id) {
            fetchMetadata();
        }
    });

    // --- API Actions ---

    async function fetchMetadata() {
        isFetching = true;
        metadataSearchError = undefined;
        metadataResults = undefined;
        selectedExternal = undefined; // Reset selection on new search

        try {
            // Sanitize query
            const query = encodeURIComponent(book.title.trim());
            if (!query) {
                metadataResults = [];
                return;
            }

            const result = await api.get<ExternalMetadata[]>(`/metadata/external/search?query=${query}`);

            if (result.left) {
                metadataSearchError = result.left.message;
            } else {
                metadataResults = result.right || [];
            }
        } catch (e) {
            console.error('Metadata fetch error:', e);
            metadataSearchError = e instanceof Error ? e.message : t.get('common.unknown_error');
            metadataResults = [];
        } finally {
            isFetching = false;
        }
    }
</script>

<div class="mt-6 p-4 bg-background border border-border rounded-md">

    {#if metadataSearchError}
        <div class="bg-destructive/20 border border-destructive text-destructive-foreground px-4 py-3 rounded-md text-sm mb-4">
            <strong>{$t('metadata.manager.search_error')}</strong> {metadataSearchError}
        </div>
    {/if}

    {#if isFetching}
        <MetadataLoadingState/>
    {:else if selectedExternal}
        <MetadataComparisonTable
                {book}
                state={m}
                external={selectedExternal}
                onBack={() => selectedExternal = undefined}
                {onApplySuccess}
                {onError}
        />
    {:else if metadataResults}
        <MetadataResultsGrid
                results={metadataResults}
                onSelect={(res) => selectedExternal = res}
                {onCancel}
        />

    {:else}
        <div class="text-center py-8 text-muted-foreground">
            {$t('metadata.manager.no_search')}
        </div>
    {/if}

</div>
