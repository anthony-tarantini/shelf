<script lang="ts">
    import { resolve } from '$app/paths';
    import { t } from '$lib/i18n';

    let { 
        bookId,
        isLoading, 
        errorMessage, 
        currentTheme, 
        themes,
        onPrev,
        onNext
    } = $props<{
        bookId: string;
        isLoading: boolean;
        errorMessage: string | null;
        currentTheme: 'light' | 'sepia' | 'dark';
        themes: any;
        onPrev: () => void;
        onNext: () => void;
    }>();
</script>

{#if isLoading}
    <div class="absolute inset-0 flex flex-col items-center justify-center gap-4 z-10 transition-colors duration-500" style="background-color: {themes[currentTheme].bg}">
        <div class="h-12 w-12 border-4 border-black/10 border-t-black/40 rounded-full animate-spin"></div>
        <div class="max-w-sm px-6 text-center">
            <p class="text-xs font-bold uppercase tracking-[0.28em] opacity-50" style="color: {themes[currentTheme].fg}">{$t('common.reader.eyebrow')}</p>
            <p class="mt-3 animate-pulse font-medium opacity-70" style="color: {themes[currentTheme].fg}">{$t('common.reader.preparing_title')}</p>
            <p class="mt-2 text-sm opacity-55" style="color: {themes[currentTheme].fg}">{$t('common.reader.preparing_message')}</p>
        </div>
    </div>
{/if}

{#if errorMessage}
    <div class="absolute inset-0 flex flex-col items-center justify-center gap-6 z-10 p-8 text-center transition-colors duration-500" style="background-color: {themes[currentTheme].bg}">
        <div class="p-4 bg-destructive/10 text-destructive rounded-full">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-12 w-12" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
        </div>
        <div class="max-w-md">
            <h2 class="text-xl font-bold mb-2" style="color: {themes[currentTheme].fg}">{$t('common.reader.error_title')}</h2>
            <p class="opacity-70" style="color: {themes[currentTheme].fg}">{errorMessage}</p>
        </div>
        <a href={resolve(`/books/${bookId}`)} class="px-6 py-2 bg-primary text-primary-foreground font-bold rounded-md transition-all shadow-lg shadow-primary/20">
            {$t('common.reader.return_to_library')}
        </a>
    </div>
{/if}

<div class="pointer-events-none absolute inset-x-0 bottom-4 z-10 flex justify-between px-3 sm:hidden" style="padding-bottom: env(safe-area-inset-bottom);">
    <button
        onclick={onPrev}
        class="pointer-events-auto rounded-full border px-4 py-2 text-xs font-bold uppercase tracking-[0.24em] shadow-lg backdrop-blur-md"
        aria-label={$t('common.reader.previous_page')}
        style="background-color: color-mix(in srgb, {themes[currentTheme].bg} 88%, transparent); color: {themes[currentTheme].fg}; border-color: {themes[currentTheme].border};"
    >
        {$t('common.reader.previous_short')}
    </button>
    <button
        onclick={onNext}
        class="pointer-events-auto rounded-full border px-4 py-2 text-xs font-bold uppercase tracking-[0.24em] shadow-lg backdrop-blur-md"
        aria-label={$t('common.reader.next_page')}
        style="background-color: color-mix(in srgb, {themes[currentTheme].bg} 88%, transparent); color: {themes[currentTheme].fg}; border-color: {themes[currentTheme].border};"
    >
        {$t('common.reader.next_short')}
    </button>
</div>

<!-- Invisible click areas for page navigation -->
<button 
    onclick={onPrev}
    class="absolute bottom-0 left-0 top-0 z-10 hidden w-1/12 cursor-w-resize md:block md:w-1/6"
    aria-label={$t('common.reader.previous_page')}
></button>
<button 
    onclick={onNext}
    class="absolute bottom-0 right-0 top-0 z-10 hidden w-1/12 cursor-e-resize md:block md:w-1/6"
    aria-label={$t('common.reader.next_page')}
></button>
