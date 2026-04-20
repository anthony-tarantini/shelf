<script lang="ts">
    import { onMount } from 'svelte';
    import { api } from '$lib/api/client';
    import { t } from '$lib/i18n';
    import type { ReadingProgress } from '$lib/types/models';

    let { 
        bookId, 
        title 
    } = $props<{
        bookId: string;
        title: string;
    }>();

    let audioElement = $state<HTMLAudioElement | null>(null);
    let pendingRestoreSeconds = $state<number | null>(null);
    let saveTimeout: ReturnType<typeof setTimeout> | null = null;

    onMount(() => {
        void restoreProgress();

        return () => {
            flushProgress();
            if (saveTimeout) clearTimeout(saveTimeout);
        };
    });

    async function restoreProgress() {
        const result = await api.get<ReadingProgress>(`/books/${bookId}/progress`);
        const seconds = result.right?.positionSeconds;
        if (typeof seconds === 'number' && Number.isFinite(seconds) && seconds >= 0) {
            pendingRestoreSeconds = seconds;
            syncRestorePosition();
        }
    }

    function syncRestorePosition() {
        if (!audioElement || pendingRestoreSeconds === null) return;
        if (!Number.isFinite(audioElement.duration) || audioElement.duration <= 0) return;
        audioElement.currentTime = Math.min(pendingRestoreSeconds, audioElement.duration);
        pendingRestoreSeconds = null;
    }

    function queueProgressSave() {
        if (saveTimeout) clearTimeout(saveTimeout);
        saveTimeout = setTimeout(() => {
            void saveProgress();
        }, 750);
    }

    function flushProgress() {
        if (saveTimeout) {
            clearTimeout(saveTimeout);
            saveTimeout = null;
        }
        void saveProgress();
    }

    async function saveProgress() {
        if (!audioElement) return;
        const duration =
            Number.isFinite(audioElement.duration) && audioElement.duration > 0
                ? audioElement.duration
                : null;
        const position = Number.isFinite(audioElement.currentTime) ? audioElement.currentTime : 0;
        const progressPercent = duration ? Math.min(1, position / duration) : null;

        await api.put(`/books/${bookId}/progress`, {
            kind: 'AUDIOBOOK',
            positionSeconds: position,
            durationSeconds: duration,
            progressPercent
        });
    }
</script>

<section class="p-6 bg-card/50 border border-border rounded-lg shadow-xl">
    <div class="flex items-center gap-4 mb-4">
        <div class="p-3 bg-primary/20 text-primary rounded-full">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
            </svg>
        </div>
        <div>
            <h2 class="text-xl font-bold text-foreground">{$t('books.audio.title')}</h2>
            <p class="text-muted-foreground text-sm font-medium">{title}</p>
        </div>
    </div>
    <audio 
        id="audio-player"
        bind:this={audioElement}
        src="/api/books/{bookId}/stream" 
        controls
        preload="metadata"
        class="w-full"
        onloadedmetadata={syncRestorePosition}
        ontimeupdate={queueProgressSave}
        onpause={flushProgress}
        onended={flushProgress}
    >
        {$t('books.audio.unsupported')}
    </audio>
</section>
