<script lang="ts">
    import { t } from '$lib/i18n';
    import type { StagedEditionMetadata } from '$lib/types/models';
    import FormField from '$lib/components/ui/FormField.svelte';

    interface Props {
        bookId: string;
        title: string;
        metadata: StagedEditionMetadata;
        showPages?: boolean;
        showNarrator?: boolean;
    }

    let {
        bookId,
        title,
        metadata = $bindable<StagedEditionMetadata>({}),
        showPages = false,
        showNarrator = false
    }: Props = $props();

    const idPrefix = $derived(title.toLowerCase().replace(/\s+/g, '-'));
</script>

<div class="space-y-3 bg-accent/30 p-3 rounded-md border border-border/50">
    <h4 class="text-sm font-bold text-foreground flex items-center gap-2">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"></path></svg>
        {title}
    </h4>
    <div class="grid grid-cols-2 gap-2">
        <FormField label={$t('books.metadata.edition.isbn13')} forId={`${idPrefix}-isbn13-${bookId}`}>
            <input id={`${idPrefix}-isbn13-${bookId}`} type="text" bind:value={metadata.isbn13} class="ui-input-sm font-mono" />
        </FormField>
        <FormField label={$t('books.metadata.edition.isbn10')} forId={`${idPrefix}-isbn10-${bookId}`}>
            <input id={`${idPrefix}-isbn10-${bookId}`} type="text" bind:value={metadata.isbn10} class="ui-input-sm font-mono" />
        </FormField>
    </div>
    <FormField label={$t('books.metadata.edition.asin')} forId={`${idPrefix}-asin-${bookId}`}>
        <input id={`${idPrefix}-asin-${bookId}`} type="text" bind:value={metadata.asin} class="ui-input-sm font-mono" />
    </FormField>
    
    {#if showPages}
        <FormField label={$t('books.metadata.edition.pages')} forId={`${idPrefix}-pages-${bookId}`}>
            <input 
                id={`${idPrefix}-pages-${bookId}`} 
                type="text" 
                inputmode="numeric"
                value={metadata.pages ?? ''} 
                oninput={(e) => {
                    const val = e.currentTarget.value.replace(/[^0-9]/g, '');
                    metadata.pages = val ? parseInt(val) : undefined;
                }}
                class="ui-input-sm" 
            />
        </FormField>
    {/if}

    {#if showNarrator}
        <FormField label={$t('books.metadata.edition.narrator')} forId={`${idPrefix}-narrator-${bookId}`}>
            <input id={`${idPrefix}-narrator-${bookId}`} type="text" bind:value={metadata.narrator} class="ui-input-sm" />
        </FormField>
    {/if}
</div>
