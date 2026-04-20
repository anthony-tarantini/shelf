<script lang="ts">
    import { api } from '$lib/api/client';
    import type { GlobalSearchResult, SeriesSummary } from '$lib/types/models';

    interface Props {
        id: string;
        placeholder: string;
        value: string;
    }

    let {
        id,
        placeholder,
        value = $bindable('')
    }: Props = $props();

    let searchResults = $state<SeriesSummary[]>([]);
    let showDropdown = $state(false);
    let dropdownIndex = $state(-1);
    let searchTimeout: ReturnType<typeof setTimeout>;

    function handleInput() {
        clearTimeout(searchTimeout);
        if (value.trim().length < 2) {
            searchResults = [];
            showDropdown = false;
            return;
        }

        searchTimeout = setTimeout(async () => {
            const result = await api.get<GlobalSearchResult>(`/search?q=${encodeURIComponent(value.trim())}`);
            if (result.right) {
                searchResults = result.right.series;
                showDropdown = searchResults.length > 0;
                dropdownIndex = -1;
            }
        }, 300);
    }

    function selectSeries(name: string) {
        value = name;
        showDropdown = false;
        searchResults = [];
        dropdownIndex = -1;
    }

    function handleKeydown(e: KeyboardEvent) {
        if (e.key === 'Enter' && showDropdown && dropdownIndex >= 0) {
            e.preventDefault();
            selectSeries(searchResults[dropdownIndex].name);
        } else if (e.key === 'ArrowDown') {
            if (showDropdown) {
                e.preventDefault();
                dropdownIndex = Math.min(dropdownIndex + 1, searchResults.length - 1);
            }
        } else if (e.key === 'ArrowUp') {
            if (showDropdown) {
                e.preventDefault();
                dropdownIndex = Math.max(dropdownIndex - 1, -1);
            }
        } else if (e.key === 'Escape') {
            showDropdown = false;
        }
    }
</script>

<div class="relative flex-1">
    <input
        {id}
        type="text"
        {placeholder}
        bind:value
        oninput={handleInput}
        onkeydown={handleKeydown}
        onblur={() => setTimeout(() => showDropdown = false, 200)}
        onfocus={() => { if (searchResults.length > 0) showDropdown = true; }}
        class="ui-input-sm w-full"
    />

    {#if showDropdown}
        <div class="absolute z-50 w-full mt-1 bg-popover border border-border rounded-lg shadow-xl overflow-hidden max-h-60 overflow-y-auto">
            {#each searchResults as result, i}
                <button
                    type="button"
                    class="w-full text-left px-3 py-2 text-xs hover:bg-accent transition-colors flex items-center justify-between {i === dropdownIndex ? 'bg-accent text-accent-foreground' : 'text-popover-foreground'}"
                    onclick={() => selectSeries(result.name)}
                >
                    <span class="font-medium">{result.name}</span>
                    <span class="text-[10px] text-muted-foreground">{result.bookCount} books</span>
                </button>
            {/each}
        </div>
    {/if}
</div>
