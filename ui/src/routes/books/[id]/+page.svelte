<script lang="ts">
    import {type AuthorRoot, type BookSeriesEntry, MediaType} from '$lib/types/models';
    import {safeHtml} from "$lib/actions/safeHtml";
    import {t} from '$lib/i18n';
    import {api} from '$lib/api/client';
    import {invalidateAll} from '$app/navigation';
    import BookFileList from '$lib/components/book/BookFileList.svelte';
    import BookMetadataForm, {type BookMetadataFormState} from '$lib/components/editor/BookMetadataForm.svelte';
    import BookHeader from '$lib/components/book/BookHeader.svelte';
    import BookSidebar from '$lib/components/book/BookSidebar.svelte';
    import BookChapters from '$lib/components/book/BookChapters.svelte';
    import LoadingState from '$lib/components/ui/LoadingState/LoadingState.svelte';
    import StatusBanner from '$lib/components/ui/StatusBanner.svelte';

    let {data} = $props();

    const details = $derived(data.details);
    const book = $derived(details?.book);
    const authors = $derived(details?.authors ?? []);
    const series = $derived(details?.series ?? []);
    const metadata = $derived(details?.metadata);
    const editions = $derived(metadata?.editions ?? []);
    const ebookEdition = $derived(editions.find(e => e.edition.format === MediaType.EBOOK)?.edition);
    const audiobookEdition = $derived(editions.find(e => e.edition.format === MediaType.AUDIOBOOK)?.edition);
    const hasAudiobook = $derived(!!audiobookEdition);
    const hasEbook = $derived(!!ebookEdition);

    const primaryMetadata = $derived(metadata);
    const primaryRecord = $derived(primaryMetadata?.metadata);
    const primaryEdition = $derived(editions[0]?.edition);
    const mediaType = $derived(primaryEdition?.format ?? MediaType.EBOOK);

    let editMode = $state(false);
    let processing = $state(false);
    let error = $state<string | null>(null);
    let initialFormData = $derived<BookMetadataFormState>({
        id: book?.id || '',
        title: book?.title || '',
        authors: authors?.map((a: AuthorRoot) => a.name) || [],
        description: primaryRecord?.description || '',
        publisher: primaryRecord?.publisher || '',
        publishYear: primaryRecord?.published,
        genres: primaryRecord?.genres || [],
        series: series?.map((s: BookSeriesEntry) => ({name: s.name, index: s.index})) || [],
        ebookMetadata: {
            isbn13: metadata?.editions?.find(e => e.edition.format === MediaType.EBOOK)?.edition?.isbn13 || primaryEdition?.isbn13,
            isbn10: metadata?.editions?.find(e => e.edition.format === MediaType.EBOOK)?.edition?.isbn10 || primaryEdition?.isbn10,
            asin: metadata?.editions?.find(e => e.edition.format === MediaType.EBOOK)?.edition?.asin || primaryEdition?.asin
        },
        audiobookMetadata: {
            narrator: metadata?.editions?.find(e => e.edition.format === MediaType.AUDIOBOOK)?.edition?.narrator || primaryEdition?.narrator
        },
        selectedAuthorIds: {},
        authorSuggestions: {}
    });

    function toggleEdit() {
        editMode = !editMode;
        error = null;
    }

    async function handleSave(formData: BookMetadataFormState) {
        if (!book) return;
        processing = true;
        error = null;

        const result = await api.put<unknown>('/books', {
            id: book.id,
            title: formData.title,
            authors: formData.authors,
            description: formData.description,
            publisher: formData.publisher,
            isbn: formData.ebookMetadata?.isbn13 || formData.ebookMetadata?.isbn10 || formData.ebookMetadata?.asin || '',
            publishYear: formData.publishYear,
            genres: formData.genres
        });

        if (result.left) {
            error = result.left.message;
            processing = false;
        } else {
            await invalidateAll();
            editMode = false;
            processing = false;
        }
    }
</script>

<div class="mx-auto max-w-6xl">
    {#if details && book}
        <BookHeader
                title={book.title}
                {authors}
                {series}
                isAudiobook={hasAudiobook}
                narrator={audiobookEdition?.narrator}
                {editMode}
                onEditToggle={toggleEdit}
        />

        {#if error}
            <div class="mb-8">
                <StatusBanner kind="error" title={$t('books.update_failed')} message={error} />
            </div>
        {/if}

        <div class="grid grid-cols-1 gap-8 md:grid-cols-[280px_1fr] md:gap-10 lg:grid-cols-[300px_1fr] lg:gap-12">
            {#if editMode}
                <div class="col-span-full">
                    {#key book.id}
                        <BookMetadataForm
                                initialData={initialFormData}
                                {processing}
                                onSave={handleSave}
                                onCancel={toggleEdit}
                        />
                    {/key}
                </div>
            {:else}
                <BookSidebar
                    bookId={book.id}
                    coverPath={book.coverPath}
                    title={book.title}
                    metadata={metadata ?? undefined}
                    userState={details.userState}
                />

                <main class="space-y-8 sm:space-y-10">
                    {#if primaryRecord?.description}
                        <section>
                            <h2 class="text-xl font-bold text-primary mb-4 flex items-center">
                                {$t('books.about')}
                                <span class="h-px bg-card flex-1 ml-4"></span>
                            </h2>
                            <div class="prose prose-invert max-w-none whitespace-pre-wrap text-base leading-relaxed text-muted-foreground sm:text-lg">
                                <span use:safeHtml={primaryRecord?.description}></span>
                            </div>
                        </section>
                    {/if}

                    <BookFileList
                            {book}
                            {editions}
                    />

                    {#if metadata}
                        <BookChapters metadata={metadata} mediaType={hasAudiobook ? MediaType.AUDIOBOOK : mediaType} />
                    {/if}
                </main>
            {/if}
        </div>
    {:else}
        <LoadingState title={$t('books.loading_detail')} message={$t('books.loading')} />
    {/if}
</div>
