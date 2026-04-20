<script lang="ts">
    import { t } from '$lib/i18n';

    let { 
        progress = 0, 
        isCalculatingProgress = false,
        currentTheme, 
        themes,
        onPrev,
        onNext
    } = $props<{
        progress: number;
        isCalculatingProgress?: boolean;
        currentTheme: 'light' | 'sepia' | 'dark';
        themes: any;
        onPrev: () => void;
        onNext: () => void;
    }>();
</script>

<footer class="z-20 shrink-0 border-t px-3 py-2 shadow-inner transition-colors duration-500 sm:px-4" style="background-color: {themes[currentTheme].bg}; border-color: {themes[currentTheme].border}; padding-bottom: max(0.75rem, env(safe-area-inset-bottom));">
    <div class="mx-auto flex w-full max-w-6xl items-center gap-3">
        <div class="flex-1 flex flex-col gap-1.5">
            <div class="flex justify-between text-[10px] uppercase font-bold tracking-widest opacity-60" style="color: {themes[currentTheme].fg}">
                {#if isCalculatingProgress}
                    <span class="animate-pulse">{$t('common.reader.calculating')}</span>
                {:else}
                    <span>{$t('common.reader.reading_progress')}</span>
                    <span>{Math.round(progress * 100)}%</span>
                {/if}
            </div>
            <div class="h-1.5 rounded-full overflow-hidden" style="background-color: {themes[currentTheme].border}">
                {#if isCalculatingProgress}
                    <div class="bg-primary/30 h-full w-full animate-pulse"></div>
                {:else}
                    <div class="bg-primary h-full transition-all duration-500 ease-out" style="width: {progress * 100}%"></div>
                {/if}
            </div>
        </div>

        {#if !isCalculatingProgress}
            <div class="hidden rounded-full border px-3 py-1 text-xs font-semibold sm:block" style="color: {themes[currentTheme].fg}; border-color: {themes[currentTheme].border}">
                {Math.round(progress * 100)}%
            </div>
        {/if}
    </div>
</footer>
