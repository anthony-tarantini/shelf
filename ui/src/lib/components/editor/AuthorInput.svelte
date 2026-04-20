<script lang="ts">
    import { t } from '$lib/i18n';
    import { api } from '$lib/api/client';
    import type { AuthorRoot, GlobalSearchResult, AuthorSummary } from '$lib/types/models';
    import FormField from '$lib/components/ui/FormField.svelte';

    interface Props {
        bookId: string;
        authors: string[];
        selectedAuthorIds?: Record<string, string | null>;
        authorSuggestions?: Record<string, AuthorRoot[]>;
    }

    let {
        bookId,
        authors = $bindable([]),
        selectedAuthorIds = $bindable<Record<string, string | null>>({}),
        authorSuggestions = {}
    }: Props = $props();

    let inputValue = $state('');
    let searchResults = $state<AuthorSummary[]>([]);
    let isSearching = $state(false);
    let showDropdown = $state(false);
    let dropdownIndex = $state(-1);

    let searchTimeout: ReturnType<typeof setTimeout>;

    function handleInput() {
        clearTimeout(searchTimeout);
        if (inputValue.trim().length < 2) {
            searchResults = [];
            showDropdown = false;
            return;
        }

        searchTimeout = setTimeout(async () => {
            isSearching = true;
            const result = await api.get<GlobalSearchResult>(`/search?q=${encodeURIComponent(inputValue.trim())}`);
            if (result.right) {
                searchResults = result.right.authors;
                showDropdown = searchResults.length > 0;
                dropdownIndex = -1;
            }
            isSearching = false;
        }, 300);
    }

    function addAuthor(name: string, authorId?: string) {
        const trimmedName = name.trim();
        if (trimmedName && !authors.includes(trimmedName)) {
            authors = [...authors, trimmedName];
            if (authorId) {
                selectedAuthorIds[trimmedName] = authorId;
            }
        }
        inputValue = '';
        searchResults = [];
        showDropdown = false;
        dropdownIndex = -1;
    }

    function removeAuthor(name: string) {
        authors = authors.filter(a => a !== name);
        // We don't necessarily want to delete the ID mapping if it was useful, 
        // but if the author is gone, the mapping is irrelevant for this save.
        const newIds = { ...selectedAuthorIds };
        delete newIds[name];
        selectedAuthorIds = newIds;
    }

    function handleKeydown(e: KeyboardEvent) {
        if (e.key === 'Enter') {
            e.preventDefault();
            if (dropdownIndex >= 0 && dropdownIndex < searchResults.length) {
                const suggestion = searchResults[dropdownIndex];
                addAuthor(suggestion.name, suggestion.id);
            } else if (inputValue.trim()) {
                addAuthor(inputValue);
            }
        } else if (e.key === 'ArrowDown') {
            e.preventDefault();
            if (showDropdown) {
                dropdownIndex = Math.min(dropdownIndex + 1, searchResults.length - 1);
            }
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            if (showDropdown) {
                dropdownIndex = Math.max(dropdownIndex - 1, -1);
            }
        } else if (e.key === 'Escape') {
            showDropdown = false;
        } else if (e.key === 'Backspace' && !inputValue && authors.length > 0) {
            removeAuthor(authors[authors.length - 1]);
        }
    }

    function selectSuggestion(authorName: string, authorId: string) {
        selectedAuthorIds[authorName] = authorId;
    }

    function unselectAuthor(authorName: string) {
        selectedAuthorIds[authorName] = null;
    }
</script>

<FormField label={$t('books.metadata.author.title')} forId={`authors-input-${bookId}`}>
    <div class="relative group">
        <div class="flex flex-wrap gap-2 p-2 rounded-xl border border-border bg-background shadow-sm min-h-[46px] focus-within:ring-2 focus-within:ring-primary transition-all">
            {#each authors as authorName (authorName)}
                <div class="bg-accent rounded-lg px-2 py-1 text-xs flex items-center gap-1.5 border border-border group/chip">
                    <span class="text-foreground font-medium">{authorName}</span>
                    
                    {#if selectedAuthorIds[authorName]}
                        <span class="flex items-center text-primary" title={$t('books.metadata.author.linked')}>
                            <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" viewBox="0 0 20 20" fill="currentColor">
                                <path fill-rule="evenodd" d="M12.586 4.586a2 2 0 112.828 2.828l-3 3a2 2 0 01-2.828 0 1 1 0 00-1.414 1.414 4 4 0 005.656 0l3-3a4 4 0 00-5.656-5.656l-1.5 1.5a1 1 0 101.414 1.414l1.5-1.5zm-5 5a2 2 0 012.828 0 1 1 0 101.414-1.414 4 4 0 00-5.656 0l-3 3a4 4 0 105.656 5.656l1.5-1.5a1 1 0 10-1.414-1.414l-1.5 1.5a2 2 0 11-2.828-2.828l3-3z" clip-rule="evenodd" />
                            </svg>
                        </span>
                    {/if}

                    <button 
                        type="button"
                        onclick={() => removeAuthor(authorName)}
                        class="text-muted-foreground hover:text-destructive transition-colors"
                        title={$t('common.remove')}
                    >
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" viewBox="0 0 20 20" fill="currentColor">
                            <path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" />
                        </svg>
                    </button>
                </div>
            {/each}
            
            <input
                id={`authors-input-${bookId}`}
                type="text"
                bind:value={inputValue}
                oninput={handleInput}
                onkeydown={handleKeydown}
                onblur={() => setTimeout(() => showDropdown = false, 200)}
                onfocus={() => { if (searchResults.length > 0) showDropdown = true; }}
                placeholder={authors.length === 0 ? $t('books.metadata.author.placeholder') : ''}
                class="flex-1 bg-transparent border-none outline-none text-xs min-w-[120px] py-1 text-foreground"
            />
        </div>

        {#if showDropdown}
            <div class="absolute border-t-0 z-50 w-full bg-popover border border-border rounded-b-xl shadow-xl overflow-hidden max-h-60 overflow-y-auto">
                {#each searchResults as result, i}
                    <button
                        type="button"
                        class="w-full text-left px-4 py-2 text-sm hover:bg-accent transition-colors flex items-center justify-between {i === dropdownIndex ? 'bg-accent text-accent-foreground' : 'text-popover-foreground'}"
                        onclick={() => addAuthor(result.name, result.id)}
                    >
                        <span class="font-medium">{result.name}</span>
                        <span class="text-xs text-muted-foreground">{result.bookCount} books</span>
                    </button>
                {/each}
            </div>
        {/if}
    </div>

    <!-- External Suggestions (Hardcover) -->
    <div class="mt-2 flex flex-wrap gap-2">
        {#each authors as authorName (authorName)}
            {#if !selectedAuthorIds[authorName] && authorSuggestions[authorName]?.length > 0}
                <div class="bg-primary/5 rounded-lg p-2 border border-primary/10 w-full">
                    <span class="text-[10px] text-primary/70 uppercase font-bold block mb-1">{$t('books.metadata.author.suggestions_for')} "{authorName}"</span>
                    <div class="flex flex-wrap gap-1">
                        {#each authorSuggestions[authorName] as suggestion (suggestion.id)}
                            <button
                                type="button"
                                onclick={() => selectSuggestion(authorName, suggestion.id)}
                                class="bg-primary/10 hover:bg-primary/20 text-primary px-2 py-0.5 rounded-md text-[10px] transition-colors border border-primary/20 flex items-center gap-1"
                            >
                                <svg xmlns="http://www.w3.org/2000/svg" class="h-2.5 w-2.5" viewBox="0 0 20 20" fill="currentColor">
                                    <path fill-rule="evenodd" d="M12.586 4.586a2 2 0 112.828 2.828l-3 3a2 2 0 01-2.828 0 1 1 0 00-1.414 1.414 4 4 0 005.656 0l3-3a4 4 0 00-5.656-5.656l-1.5 1.5a1 1 0 101.414 1.414l1.5-1.5zm-5 5a2 2 0 012.828 0 1 1 0 101.414-1.414 4 4 0 00-5.656 0l-3 3a4 4 0 105.656 5.656l1.5-1.5a1 1 0 10-1.414-1.414l-1.5 1.5a2 2 0 11-2.828-2.828l3-3z" clip-rule="evenodd" />
                                </svg>
                                {suggestion.name}
                            </button>
                        {/each}
                    </div>
                </div>
            {:else if selectedAuthorIds[authorName]}
                 <button 
                    type="button"
                    onclick={() => unselectAuthor(authorName)}
                    class="text-[10px] text-primary hover:text-primary/80 flex items-center gap-1 mt-1 px-1"
                    title={$t('books.metadata.author.unlink')}
                >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-2.5 w-2.5" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd" d="M12.586 4.586a2 2 0 112.828 2.828l-3 3a2 2 0 01-2.828 0 1 1 0 00-1.414 1.414 4 4 0 005.656 0l3-3a4 4 0 00-5.656-5.656l-1.5 1.5a1 1 0 101.414 1.414l1.5-1.5zm-5 5a2 2 0 012.828 0 1 1 0 101.414-1.414 4 4 0 00-5.656 0l-3 3a4 4 0 105.656 5.656l1.5-1.5a1 1 0 10-1.414-1.414l-1.5 1.5a2 2 0 11-2.828-2.828l3-3z" clip-rule="evenodd" />
                    </svg>
                    {$t('books.metadata.author.linked')} ({authorName})
                </button>
            {/if}
        {/each}
    </div>
</FormField>
