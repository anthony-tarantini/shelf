<script lang="ts">
    import type {BookRoot, EditionWithChapters} from '$lib/types/models';
    import {MediaType} from '$lib/types/models';
    import {formatDuration} from '$lib/utils';
    import AudioPlayer from './AudioPlayer.svelte';
    import {resolve} from '$app/paths';
    import {api} from '$lib/api/client';

    interface Props {
        book: BookRoot;
        editions: EditionWithChapters[];
    }

    let { 
        book, 
        editions = []
    }: Props = $props();

    let activePlayerId = $state<string | null>(null);

    // A simple derived helper to calculate human-readable file size
    const formatBytes = (bytes?: number) => {
        if (!bytes || isNaN(bytes) || bytes === 0) return '0 Bytes';
        const k = 1024;
        const dm = 2;
        const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        if (i < 0) return '0 Bytes';
        return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
    };

    function getExtension(path?: string, format?: MediaType) {
        return path?.split('.').pop()?.toLowerCase() ?? (format === MediaType.AUDIOBOOK ? 'mp3' : 'epub');
    }

    function togglePlayer(id: string) {
        if (activePlayerId === id) {
            activePlayerId = null;
        } else {
            activePlayerId = id;
        }
    }

    async function downloadFile(editionId: string) {
        const token = api.getToken();
        const response = await fetch(`/api/books/${book.id}/download`, {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });
        
        if (response.ok) {
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            const edition = editions.find(e => e.edition.id === editionId);
            a.download = `${book.title}.${getExtension(edition?.edition.path, edition?.edition.format)}`;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
        }
    }
</script>

<section>
    <h2 class="text-xl font-bold text-primary mb-4 flex items-center">
        Files
        <span class="h-px bg-border flex-1 ml-4"></span>
    </h2>
    <div class="flex flex-col gap-4">
        {#each editions as { edition } (edition.id)}
            <div class="bg-card/50 border border-border rounded-lg overflow-hidden">
                <div class="p-4 flex items-center justify-between">
                    <div class="flex items-center gap-4">
                        <div class="p-3 bg-primary/20 text-primary rounded-md">
                            {#if edition.format === MediaType.AUDIOBOOK}
                                <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3" />
                                </svg>
                            {:else}
                                <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
                                </svg>
                            {/if}
                        </div>
                        <div>
                            <p class="font-bold text-foreground tracking-tight">
                                {edition.format === MediaType.AUDIOBOOK ? 'Audiobook' : 'Ebook'} 
                                <span class="text-muted-foreground font-normal ml-1">({getExtension(edition.path, edition.format).toUpperCase()})</span>
                            </p>
                            <div class="flex items-center gap-2 text-sm text-muted-foreground font-medium">
                                <span>{formatBytes(edition.size)}</span>
                                {#if edition.format === MediaType.AUDIOBOOK && edition.totalTime}
                                    <span class="before:content-['•'] before:mr-2">{formatDuration(edition.totalTime)}</span>
                                {/if}
                            </div>
                        </div>
                    </div>
                    <div class="flex gap-2">
                        <button 
                            onclick={() => downloadFile(edition.id)}
                            class="px-4 py-2 bg-card hover:bg-accent text-foreground font-bold text-sm rounded transition-all border border-border"
                        >
                            Download
                        </button>
                        {#if edition.format === MediaType.AUDIOBOOK}
                            <button 
                                onclick={() => togglePlayer(edition.id)}
                                class="px-4 py-2 bg-primary hover:bg-primary/90 text-primary-foreground font-bold text-sm rounded transition-all shadow-lg shadow-primary/20"
                            >
                                {activePlayerId === edition.id ? 'Close' : 'Listen'}
                            </button>
                        {:else}
                            <a 
                                href={resolve(`/read/${book.id}`)}
                                class="px-4 py-2 bg-primary hover:bg-primary/90 text-primary-foreground font-bold text-sm rounded transition-all shadow-lg shadow-primary/20 text-center"
                            >
                                Read
                            </a>
                        {/if}
                    </div>
                </div>
                {#if activePlayerId === edition.id}
                    <div class="border-t border-border animate-in slide-in-from-top-4 duration-300">
                        <AudioPlayer bookId={book.id} title={book.title} />
                    </div>
                {/if}
            </div>
        {/each}
    </div>
</section>