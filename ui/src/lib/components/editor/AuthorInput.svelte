<script lang="ts">
    import { t } from '$lib/i18n';
    import type { AuthorRoot } from '$lib/types/models';
    import FormField from '$lib/components/ui/FormField.svelte';

    interface Props {
        bookId: string;
        authorsString: string;
        selectedAuthorIds?: Record<string, string | null>;
        authorSuggestions?: Record<string, AuthorRoot[]>;
    }

    let {
        bookId,
        authorsString = $bindable(''),
        selectedAuthorIds = $bindable<Record<string, string | null>>({}),
        authorSuggestions = {}
    }: Props = $props();

    let authorsArray = $derived(
        authorsString.split(',').map((a: string) => a.trim()).filter((a: string) => a.length > 0)
    );

    function selectSuggestion(authorName: string, authorId: string) {
        selectedAuthorIds[authorName] = authorId;
    }

    function unselectAuthor(authorName: string) {
        selectedAuthorIds[authorName] = null;
    }
</script>

<FormField label={$t('books.metadata.author.title')} forId={`authors-${bookId}`}>
    <input
        id={`authors-${bookId}`}
        type="text"
        bind:value={authorsString}
        class="ui-input"
    />
    <div class="mt-2 flex flex-wrap gap-2">
        {#each authorsArray as authorName (authorName)}
            <div class="bg-accent rounded px-2 py-1 text-xs flex flex-col gap-1 border border-border">
                <div class="flex items-center justify-between gap-2">
                    <span class="text-gray-200 font-medium">{authorName}</span>
                    {#if selectedAuthorIds[authorName]}
                        <button 
                            onclick={() => unselectAuthor(authorName)}
                            class="text-primary hover:text-primary/80 flex items-center gap-1"
                            title={$t('books.metadata.author.unlink')}
                        >
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" viewBox="0 0 20 20" fill="currentColor">
                                <path fill-rule="evenodd" d="M12.586 4.586a2 2 0 112.828 2.828l-3 3a2 2 0 01-2.828 0 1 1 0 00-1.414 1.414 4 4 0 005.656 0l3-3a4 4 0 00-5.656-5.656l-1.5 1.5a1 1 0 101.414 1.414l1.5-1.5zm-5 5a2 2 0 012.828 0 1 1 0 101.414-1.414 4 4 0 00-5.656 0l-3 3a4 4 0 105.656 5.656l1.5-1.5a1 1 0 10-1.414-1.414l-1.5 1.5a2 2 0 11-2.828-2.828l3-3z" clip-rule="evenodd" />
                            </svg>
                            {$t('books.metadata.author.linked')}
                        </button>
                    {/if}
                </div>
                
                {#if !selectedAuthorIds[authorName] && authorSuggestions[authorName]?.length > 0}
                    <div class="mt-1 pt-1 border-t border-border">
                        <span class="text-[10px] text-muted-foreground uppercase font-bold block mb-1">{$t('books.metadata.author.suggestions')}</span>
                        <div class="flex flex-wrap gap-1">
                            {#each authorSuggestions[authorName] as suggestion (suggestion.id)}
                                <button
                                    onclick={() => selectSuggestion(authorName, suggestion.id)}
                                    class="bg-primary/20 hover:bg-primary/20 text-primary px-1.5 py-0.5 rounded text-[10px] transition-colors border border-primary/30"
                                >
                                    {suggestion.name}
                                </button>
                            {/each}
                        </div>
                    </div>
                {/if}
            </div>
        {/each}
    </div>
</FormField>
