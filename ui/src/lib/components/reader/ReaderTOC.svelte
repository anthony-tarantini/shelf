<script lang="ts">
    import { t } from '$lib/i18n';

    let { 
        toc = [], 
        currentTheme, 
        themes,
        isOpen = $bindable(false),
        onJumpTo
    } = $props<{
        toc: any[];
        currentTheme: 'light' | 'sepia' | 'dark';
        themes: any;
        isOpen: boolean;
        onJumpTo: (href: string) => void;
    }>();
</script>

{#if isOpen}
    <aside class="absolute inset-y-0 left-0 z-30 w-full border-r shadow-2xl animate-in slide-in-from-left duration-200 transition-colors duration-500 sm:w-[min(24rem,100vw)]" style="background-color: {themes[currentTheme].bg}; border-color: {themes[currentTheme].border}; padding-top: env(safe-area-inset-top); padding-bottom: env(safe-area-inset-bottom);">
        <div class="p-4 border-b flex justify-between items-center bg-black/5" style="border-color: {themes[currentTheme].border}">
            <div>
                <p class="text-[10px] font-bold uppercase tracking-[0.28em] text-primary">{$t('common.reader.navigation')}</p>
                <h2 class="mt-1 font-bold text-sm uppercase tracking-widest text-primary">{$t('common.reader.contents')}</h2>
            </div>
            <button
                onclick={() => isOpen = false}
                aria-label={$t('common.reader.close_table_of_contents')}
                class="p-1 hover:bg-black/5 rounded-full text-muted-foreground transition-colors"
            >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                </svg>
            </button>
        </div>
        <nav class="h-[calc(100%-72px-env(safe-area-inset-top)-env(safe-area-inset-bottom))] overflow-y-auto p-2 custom-scrollbar">
            {#each toc as item}
                <button 
                    onclick={() => onJumpTo(item.href)}
                    class="w-full border-b p-3 text-left text-sm transition-colors last:border-0 hover:bg-primary/10 hover:text-primary"
                    style="border-color: {themes[currentTheme].border}; color: {themes[currentTheme].fg}"
                >
                    <span class="line-clamp-2">{item.label}</span>
                </button>
            {/each}
        </nav>
    </aside>
    <!-- Overlay to close TOC -->
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div class="absolute inset-0 bg-black/20 backdrop-blur-sm z-20" onclick={() => isOpen = false}></div>
{/if}
