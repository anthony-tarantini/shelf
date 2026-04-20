<script lang="ts">
    import { t } from '$lib/i18n';
    import { MediaType } from '$lib/types/models';

    let { type, class: className = '' } = $props<{
        type: MediaType | string;
        class?: string;
    }>();
</script>

{#if type === MediaType.AUDIOBOOK || type === 'AUDIOBOOK'}
    <div class="flex items-center gap-1.5 px-2 py-1 rounded-md bg-primary/15 text-primary border border-primary/20 w-fit {className}" title={$t('common.audiobook')}>
        <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
        </svg>
        <span class="text-[10px] font-bold uppercase tracking-wider">{$t('common.audio')}</span>
    </div>
{:else}
    <div class="flex items-center gap-1.5 px-2 py-1 rounded-md bg-blue-500/15 text-blue-400 border border-blue-500/20 w-fit {className}" title={$t('common.ebook')}>
        <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
        </svg>
        <span class="text-[10px] font-bold uppercase tracking-wider">{$t('common.ebook')}</span>
    </div>
{/if}
