<script lang="ts">
    import {t} from '$lib/i18n';
    import {api} from '$lib/api/client';
    import type {GlobalSearchResult} from '$lib/types/models';
    import {onMount} from 'svelte';
    import GlobalSearchResults from './search/GlobalSearchResults.svelte';

    let {isOpen = $bindable(false)} = $props<{ isOpen: boolean }>();

    let query = $state('');
    let results = $state<GlobalSearchResult | null>(null);
    let isSearching = $state(false);
    let inputElement = $state<HTMLInputElement | null>(null);

    $effect(() => {
        if (isOpen && inputElement) {
            inputElement.focus();
        }
    });

    // Debounced search effect
    $effect(() => {
        const q = query.trim();
        if (q.length < 2) {
            results = null;
            isSearching = false;
            return;
        }

        const timer = setTimeout(async () => {
            // Set isSearching ONLY if we're still on this query
            if (q === query.trim()) {
                isSearching = true;
            }

            try {
                const res = await api.get<GlobalSearchResult>(`/search?q=${encodeURIComponent(q)}`);

                // Only update state if this is still the current query
                if (q === query.trim()) {
                    if (res.right) {
                        results = res.right;
                    }
                }
            } finally {
                // Terminal state reset for this query attempt
                if (q === query.trim()) {
                    isSearching = false;
                }
            }
        }, 300);

        return () => {
            clearTimeout(timer);
        };
    });

    function close() {
        isOpen = false;
        query = '';
        results = null;
    }

    function handleKeydown(e: KeyboardEvent) {
        if (e.key === 'Escape') close();
    }

    onMount(() => {
        const handleGlobalKeydown = (e: KeyboardEvent) => {
            if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
                e.preventDefault();
                isOpen = true;
            }
        };
        window.addEventListener('keydown', handleGlobalKeydown);
        return () => window.removeEventListener('keydown', handleGlobalKeydown);
    });
</script>

{#if isOpen}
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div
            class="fixed inset-0 z-50 flex items-end justify-center bg-background/80 px-3 pt-6 backdrop-blur-sm animate-in fade-in duration-200 sm:items-start sm:px-4 sm:pt-[12vh]"
            onclick={close}
    >
        <div
                class="max-h-[85dvh] w-full overflow-hidden rounded-t-[1.75rem] border border-border bg-card/95 shadow-2xl backdrop-blur-md animate-in zoom-in-95 duration-200 sm:max-h-[80dvh] sm:max-w-2xl sm:rounded-[1.75rem]"
                onclick={e => e.stopPropagation()}
                onkeydown={handleKeydown}
        >
            <div class="border-b border-border p-4">
                <div class="mb-3 flex items-center justify-between gap-3">
                    <div>
                        <p class="text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{$t('common.search.quick_jump')}</p>
                        <h2 class="font-display text-2xl text-foreground">{$t('common.search.search_catalog')}</h2>
                    </div>
                    <button onclick={close}
                            class="rounded border border-border bg-accent/50 px-2 py-1 text-xs text-muted-foreground hover:text-foreground">
                        <span class="hidden sm:inline">ESC</span>
                        <span class="sm:hidden">{$t('common.actions.close')}</span>
                    </button>
                </div>
                <div class="flex items-center gap-3 rounded-2xl border border-border bg-background/70 px-4 py-3">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-muted-foreground" fill="none"
                         viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/>
                    </svg>
                    <input
                            bind:this={inputElement}
                            bind:value={query}
                            type="text"
                            placeholder={$t('common.search.placeholder')}
                            class="w-full border-none bg-transparent text-base text-foreground outline-none placeholder:text-muted-foreground sm:text-lg"
                    />
                </div>
            </div>

            {#if isSearching}
                <div class="p-8 text-center text-muted-foreground">
                    <span class="inline-block h-6 w-6 border-2 border-primary/30 border-t-primary rounded-full animate-spin"></span>
                </div>
            {:else}
                <GlobalSearchResults {results} {query} onSelect={close} />
            {/if}

            <div class="hidden items-center justify-between border-t border-border bg-accent/30 p-3 text-[10px] text-muted-foreground sm:flex">
                <div class="flex gap-4">
                    <span><kbd class="bg-card px-1 rounded border border-border shadow-sm">Enter</kbd> {$t('common.search.select')}</span>
                    <span><kbd class="bg-card px-1 rounded border border-border shadow-sm">↑↓</kbd> {$t('common.search.navigate')}</span>
                </div>
            </div>
        </div>
    </div>
{/if}
