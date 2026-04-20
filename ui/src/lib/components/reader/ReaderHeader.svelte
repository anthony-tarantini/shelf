<script lang="ts">
    import { resolve } from '$app/paths';
    import { t } from '$lib/i18n';

    interface Props {
        bookId: string;
        title: string;
        currentTheme: 'light' | 'sepia' | 'dark';
        themes: Record<'light' | 'sepia' | 'dark', { bg: string, fg: string, border: string, ui: string }>;
        onPrev: () => void;
        onNext: () => void;
        onToggleToc: () => void;
    }

    let { 
        bookId, 
        title, 
        currentTheme = $bindable(), 
        themes,
        onPrev,
        onNext,
        onToggleToc
    }: Props = $props();

    let themeKeys = $derived(Object.keys(themes) as Array<keyof typeof themes>);
</script>

<header class="z-20 shrink-0 border-b px-3 py-2 shadow-sm transition-colors duration-500 sm:px-4" style="background-color: {themes[currentTheme].bg}; border-color: {themes[currentTheme].border}; padding-top: max(0.75rem, env(safe-area-inset-top));">
    <div class="mx-auto flex max-w-6xl items-center justify-between gap-2 sm:gap-4">
    <div class="flex min-w-0 items-center gap-2">
        <a href={resolve(`/books/${bookId}`)} class="shrink-0 rounded-full p-2 transition-colors hover:bg-black/5" title={$t('common.reader.back_to_book')} style="color: {themes[currentTheme].fg}">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
            </svg>
        </a>
        <div class="min-w-0">
            <p class="text-[10px] font-bold uppercase tracking-[0.28em] opacity-60" style="color: {themes[currentTheme].fg}">{$t('common.reader.eyebrow')}</p>
            <h1 class="max-w-[9rem] truncate text-sm font-bold sm:max-w-xs md:max-w-md md:text-base" style="color: {themes[currentTheme].fg}">{title}</h1>
        </div>
    </div>

    <div class="flex items-center gap-2 sm:gap-3">
        <!-- Theme Selector -->
        <div class="hidden items-center rounded-xl border p-1 transition-colors duration-500 sm:flex" style="background-color: {themes[currentTheme].ui}; border-color: {themes[currentTheme].border}">
            {#each themeKeys as themeName (themeName)}
                <button 
                    onclick={() => currentTheme = themeName}
                    class="w-8 h-8 rounded-md flex items-center justify-center transition-all {currentTheme === themeName ? 'bg-white shadow-sm ring-1 ring-black/5' : 'hover:bg-white/50'}"
                    title={`${themeName.charAt(0).toUpperCase() + themeName.slice(1)} Mode`}
                >
                    <span class="w-4 h-4 rounded-full border border-black/10" style="background-color: {themes[themeName].bg}"></span>
                </button>
            {/each}
        </div>

        <!-- Page Navigation -->
        <div class="hidden items-center rounded-xl border p-1 transition-colors duration-500 md:flex" style="background-color: {themes[currentTheme].ui}; border-color: {themes[currentTheme].border}">
            <button 
                onclick={onPrev}
                class="p-1.5 hover:bg-primary hover:text-primary-foreground rounded-md transition-all flex items-center gap-1 group"
                title={$t('common.reader.previous_page')}
            >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
                </svg>
                <span class="hidden md:inline text-xs font-bold uppercase tracking-wider">{$t('common.reader.previous_short')}</span>
            </button>
            <div class="w-px h-4 mx-1 transition-colors duration-500" style="background-color: {themes[currentTheme].border}"></div>
            <button 
                onclick={onNext}
                class="p-1.5 hover:bg-primary hover:text-primary-foreground rounded-md transition-all flex items-center gap-1 group"
                title={$t('common.reader.next_page')}
            >
                <span class="hidden md:inline text-xs font-bold uppercase tracking-wider">{$t('common.reader.next_short')}</span>
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
                </svg>
            </button>
        </div>

        <button 
            onclick={onToggleToc} 
            class="flex items-center gap-2 rounded-xl border border-transparent p-2 transition-colors hover:border-black/10 hover:bg-black/5"
            title={$t('common.reader.table_of_contents')}
            style="color: {themes[currentTheme].fg}"
        >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
            </svg>
            <span class="hidden sm:inline text-xs font-bold uppercase tracking-wider">{$t('common.reader.contents')}</span>
        </button>

        <div class="flex items-center rounded-xl border p-1 transition-colors duration-500 sm:hidden" style="background-color: {themes[currentTheme].ui}; border-color: {themes[currentTheme].border}">
            {#each themeKeys as themeName (themeName)}
                <button
                    onclick={() => currentTheme = themeName}
                    class="flex h-8 w-8 items-center justify-center rounded-md transition-all {currentTheme === themeName ? 'bg-white shadow-sm ring-1 ring-black/5' : 'hover:bg-white/50'}"
                    title={`${themeName.charAt(0).toUpperCase() + themeName.slice(1)} Mode`}
                >
                    <span class="h-4 w-4 rounded-full border border-black/10" style="background-color: {themes[themeName].bg}"></span>
                </button>
            {/each}
        </div>
    </div>
    </div>
</header>
