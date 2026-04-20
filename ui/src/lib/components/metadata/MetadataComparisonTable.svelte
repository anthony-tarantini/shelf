<script lang="ts">
    import { t } from '$lib/i18n';
    import AuthenticatedImage from '$lib/components/ui/AuthenticatedImage.svelte';
    import { getExternalAuthorNames } from '$lib/utils/externalMetadata';
    import type {ExternalContributor, ExternalGenre, ExternalMetadata, StagedSeries} from '$lib/types/models';
    import type {MetadataBookView} from '$lib/types/metadata';
    import type {MetadataState} from '$lib/states/metadataState.svelte.js';
    import {ChipRow, CoverRow, DescriptionRow, IdentifierGroupRow, SeriesListRow, TextRow} from '../comparison';

    let {
        book,
        state: passedState,
        external,
        onBack,
        onApplySuccess,
        onError,
        onApply,
        coverApiPath
    } = $props<{
        book: MetadataBookView;
        state: MetadataState;
        external: ExternalMetadata;
        onBack: () => void;
        onApplySuccess: () => void;
        onError: (msg: string) => void;
        onApply: (payload: any) => Promise<{left?: {message: string}}>;
        coverApiPath?: string;
    }>();

    let processing = $state(false);

    let externalContributors = $derived(
        getExternalAuthorNames(external)
    );

    let externalGenres = $derived((external.genres ?? []).map((g: ExternalGenre) => g.name));

    let doesSeriesExist = (name: string, position: number | undefined) => {
        const exists = passedState.series.find((s: StagedSeries) => s.name === name);
        if (exists) {
            passedState.removeSeries(name);
        } else {
            passedState.addSeries(name, position);
        }
    }

    async function applyMetadata() {
        if (processing) return;
        processing = true;

        try {
            const updatePayload = passedState.getUpdatePayload();

            const result = await onApply(updatePayload);
            if (result.left) {
                onError(result.left.message);
            } else {
                onApplySuccess();
            }
        } catch (e) {
            onError(e instanceof Error ? e.message : t.get('metadata.manager.apply_failed'));
        } finally {
            processing = false;
        }
    }
</script>

<div class="mb-4 flex justify-between items-center gap-4">
    <h4 class="text-lg font-bold text-foreground truncated">
        {$t('metadata.manager.compare_title')} <span class="text-primary/80">{book.title}</span> {$t('metadata.manager.compare_external')}
    </h4>
    <button
            type="button"
            onclick={onBack}
            disabled={processing}
            class="text-sm text-muted-foreground hover:text-foreground disabled:opacity-50 flex items-center gap-1 shrink-0"
    >
        ← {$t('metadata.manager.back_to_results')}
    </button>
</div>

<p class="mb-3 text-sm text-muted-foreground">
    {$t('metadata.manager.external_hint')}
</p>

<div class="overflow-x-auto rounded-[1.5rem] border border-border bg-card/80 shadow-xl shadow-black/5">
    <table class="w-full table-fixed text-left text-sm">
        <thead class="sticky top-0 z-10 bg-card/95 text-muted-foreground backdrop-blur">
        <tr>
            <th class="p-3 w-1/5">{$t('metadata.manager.field')}</th>
            <th class="p-3 w-2/5">{$t('metadata.manager.merged_result')}</th>
            <th class="p-3 w-2/5 text-primary">{$t('metadata.manager.external_options')}</th>
        </tr>
        </thead>
        <tbody class="divide-y divide-border bg-card/80">

        <CoverRow
                bookId={book.id}
                originalCoverPath={book.coverPath}
                mergedCoverUrl={passedState.coverUrl}
                externalCoverUrl={external.imageUrl}
                onUseExternal={() => passedState.coverUrl = external.imageUrl}
                internalCoverApiPath={coverApiPath}
                internalImageComponent={AuthenticatedImage}
        />

        <TextRow
                label={$t('metadata.fields.title')}
                currentValue={passedState.title}
                externalValue={external.title}
                onUseExternal={() => passedState.title = external.title}
        />

        <ChipRow
                label={$t('metadata.fields.authors')}
                currentItems={passedState.authors}
                externalOptions={externalContributors}
                onToggleItem={(name) => passedState.toggleAuthor(name)}
        />

        <DescriptionRow
                currentHtml={passedState.description}
                externalHtml={external.description}
                onUseExternal={() => passedState.description = external.description}
        />

        <TextRow
                label={$t('metadata.fields.publisher')}
                currentValue={passedState.publisher}
                externalValue={external.publisher?.name || ''}
                onUseExternal={() => passedState.publisher = external.publisher?.name || ''}
        />

        <IdentifierGroupRow
                label={$t('metadata.fields.ebook_identifiers')}
                currentMetadata={passedState.ebookMetadata}
                externalData={external.defaultEbook}
                onUseExternal={(type, val) => {
                    if (!passedState.ebookMetadata) passedState.ebookMetadata = {};
                    passedState.ebookMetadata[type] = val;
                }}
        />

        <IdentifierGroupRow
                label={$t('metadata.fields.audiobook_identifiers')}
                currentMetadata={passedState.audiobookMetadata}
                externalData={external.defaultAudiobook}
                onUseExternal={(type, val) => {
                    if (!passedState.audiobookMetadata) passedState.audiobookMetadata = {};
                    passedState.audiobookMetadata[type] = val;
                }}
        />

        <TextRow
                label={$t('metadata.fields.publish_year')}
                currentValue={passedState.publishYear}
                externalValue={external.releaseYear?.toString()}
                onUseExternal={() => passedState.publishYear = external.releaseYear}
        />

        <ChipRow
                label={$t('metadata.fields.genres')}
                currentItems={passedState.genres}
                externalOptions={externalGenres}
                onToggleItem={(name) => passedState.toggleGenre(name)}
        />

        <SeriesListRow
                currentSeries={passedState.series}
                externalSeriesOptions={external.seriesName || []}
                onToggleSeries={doesSeriesExist}
                onUpdateSeriesIndex={(name, index) => passedState.updateSeriesIndex(name, index)}
        />
        </tbody>
    </table>
</div>

<div class="mt-4 flex gap-2">
    <button
            type="button"
            onclick={applyMetadata}
            disabled={processing}
            class="flex items-center rounded-xl bg-primary px-4 py-2 font-bold text-primary-foreground transition-colors hover:bg-primary/90 disabled:bg-primary/80"
    >
        {#if processing}
            <span class="mr-2 h-4 w-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
            {$t('metadata.manager.applying')}
        {:else}
            {$t('metadata.manager.apply_selected')}
        {/if}
    </button>
    <button
            type="button"
            onclick={onBack}
            disabled={processing}
            class="rounded-xl border border-border bg-accent px-4 py-2 font-bold text-foreground transition-colors hover:bg-accent/80 disabled:opacity-50"
    >
        {$t('common.actions.cancel')}
    </button>
</div>

<style>
    .truncated {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }
</style>
