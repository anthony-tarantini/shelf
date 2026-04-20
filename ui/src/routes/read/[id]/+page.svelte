<script lang="ts">
    import { t } from '$lib/i18n';
    import EpubReader from '$lib/components/reader/EpubReader.svelte';

    let { data } = $props();
    const details = $derived(data.details);
    const book = $derived(details?.book);
</script>

<svelte:head>
    {#if book}
        <title>{book.title} | Shelf</title>
    {:else}
        <title>Shelf</title>
    {/if}
</svelte:head>

{#if book}
    <EpubReader bookId={book.id} title={book.title} />
{:else}
    <div class="h-screen w-screen flex items-center justify-center">
        <p class="text-muted-foreground">{$t('books.loading_reader')}</p>
    </div>
{/if}
