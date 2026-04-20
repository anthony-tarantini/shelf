<script lang="ts">
    import { t } from '$lib/i18n'
    import type {StagedSeries, StagedEditionMetadata, AuthorRoot} from '$lib/types/models';
    import AuthorInput from './AuthorInput.svelte';
    import SeriesInput from './SeriesInput.svelte';
    import EditionInput from './EditionInput.svelte';

    export interface BookMetadataFormState {
        id: string;
        title: string;
        authors: string[];
        description: string;
        publisher: string;
        publishYear?: number;
        genres: string[];
        series: StagedSeries[];
        ebookMetadata: StagedEditionMetadata;
        audiobookMetadata: StagedEditionMetadata;
        authorSuggestions?: Record<string, AuthorRoot[]>;
        selectedAuthorIds?: Record<string, string | null>;
    }

    interface Props {
        initialData: BookMetadataFormState;
        processing?: boolean;
        onSave: (data: BookMetadataFormState) => void;
        onCancel: () => void;
    }

    let {
        initialData,
        processing = false,
        onSave,
        onCancel
    }: Props = $props();

    // Internal reactive copy
    let formData = $derived<BookMetadataFormState>(JSON.parse(JSON.stringify(initialData)));
    let genresString = $derived((initialData.genres || []).join(', '));

    $effect(() => {
        formData = JSON.parse(JSON.stringify(initialData));
        genresString = (initialData.genres || []).join(', ');
    });

    // Sync genresString to formData.genres
    $effect(() => {
        if (formData) {
            formData.genres = genresString.split(',')
                .map((g: string) => g.trim())
                .filter((g: string) => g.length > 0);
        }
    });

    function handleSubmit() {
        onSave(formData);
    }
</script>

<div class="max-w-3xl space-y-5 rounded-[1.75rem] border border-border bg-card/80 p-5 shadow-xl shadow-black/5">
    <div>
        <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('import.staged.editor.eyebrow')}</p>
        <p class="mt-2 text-sm leading-6 text-muted-foreground">{$t('import.staged.editor.description')}</p>
    </div>

    <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div>
            <label for="title-{formData.id}"
                   class="text-xs text-muted-foreground uppercase font-bold tracking-wider mb-1 block">{$t('books.metadata.title')}</label>
            <input
                    id="title-{formData.id}"
                    type="text"
                    bind:value={formData.title}
                    class="w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground shadow-sm focus:outline-none focus:ring-2 focus:ring-primary"
            />
        </div>
        <AuthorInput
                bookId={formData.id}
                bind:authors={formData.authors}
                bind:selectedAuthorIds={formData.selectedAuthorIds}
                authorSuggestions={formData.authorSuggestions}
        />
    </div>

    <div class="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div>
            <label for="publisher-{formData.id}"
                   class="text-xs text-muted-foreground uppercase font-bold tracking-wider mb-1 block">{$t('books.metadata.publisher')}</label>
            <input
                    id="publisher-{formData.id}"
                    type="text"
                    bind:value={formData.publisher}
                    class="w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground shadow-sm focus:outline-none focus:ring-2 focus:ring-primary"
            />
        </div>
        <div>
            <label for="date-{formData.id}"
                   class="text-xs text-muted-foreground uppercase font-bold tracking-wider mb-1 block">{$t('books.metadata.publish_year')}</label>
            <input
                    id="date-{formData.id}"
                    type="text"
                    inputmode="numeric"
                    placeholder={$t('import.staged.editor.publish_year_placeholder')}
                    value={formData.publishYear ?? ''}
                    oninput={(e) => {
                    const val = e.currentTarget.value.replace(/[^0-9]/g, '');
                    formData.publishYear = val ? parseInt(val) : undefined;
                }}
                    class="w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground shadow-sm focus:outline-none focus:ring-2 focus:ring-primary"
            />
        </div>
    </div>

    <SeriesInput bookId={formData.id} bind:seriesList={formData.series}/>

    <div>
        <label for="genres-{formData.id}"
               class="text-xs text-muted-foreground uppercase font-bold tracking-wider mb-1 block">{$t('books.metadata.genres')}</label>
        <input
                id="genres-{formData.id}"
                type="text"
                bind:value={genresString}
                class="w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground shadow-sm focus:outline-none focus:ring-2 focus:ring-primary"
        />
    </div>

    <div class="grid grid-cols-1 gap-6 border-t border-border pt-4 lg:grid-cols-2">
        <EditionInput
                bookId={formData.id}
                title={$t('import.staged.editor.ebook_details')}
                bind:metadata={formData.ebookMetadata}
                showPages={true}
        />
        <EditionInput
                bookId={formData.id}
                title={$t('import.staged.editor.audiobook_details')}
                bind:metadata={formData.audiobookMetadata}
                showNarrator={true}
        />
    </div>

    <div>
        <label for="desc-{formData.id}"
               class="text-xs text-muted-foreground uppercase font-bold tracking-wider mb-1 block">{$t('books.metadata.description')}</label>
        <textarea
                id="desc-{formData.id}"
                bind:value={formData.description}
                rows="6"
                class="w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground shadow-sm focus:outline-none focus:ring-2 focus:ring-primary"
        ></textarea>
    </div>

    <div class="flex gap-2 pt-2">
        <button
                onclick={handleSubmit}
                disabled={processing}
                class="flex items-center rounded-xl bg-primary px-4 py-2 font-bold text-primary-foreground shadow-lg shadow-primary/20 transition-colors hover:bg-primary/90 disabled:bg-muted"
        >
            {#if processing}
                <span class="mr-2 h-4 w-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
            {/if}
            {$t('books.metadata.save_changes')}
        </button>
        <button
                onclick={onCancel}
                disabled={processing}
                class="rounded-xl border border-border bg-accent px-4 py-2 font-bold text-foreground transition-colors hover:bg-accent/80 disabled:bg-card"
        >
            {$t('books.metadata.cancel')}
        </button>
    </div>
</div>
