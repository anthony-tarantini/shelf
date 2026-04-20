<script lang="ts">
    import { onMount } from 'svelte';
    import ePub from 'epubjs';
    import { browser } from '$app/environment';
    import { api } from '$lib/api/client';
    import { t } from '$lib/i18n';
    import type { ReadingProgress } from '$lib/types/models';
    
    import ReaderHeader from './ReaderHeader.svelte';
    import ReaderTOC from './ReaderTOC.svelte';
    import ReaderFooter from './ReaderFooter.svelte';
    import ReaderOverlay from './ReaderOverlay.svelte';

    interface Props {
        bookId: string;
        title: string;
    }

    let { bookId, title }: Props = $props();

    let viewerElement = $state<HTMLDivElement>();
    let book: any;
    let rendition: any;
    let toc = $state<any[]>([]);
    let isTocOpen = $state(false);
    let progress = $state(0);
    let isCalculatingProgress = $state(true);
    let currentLocation = $state<string>();
    let lastSavedLocation = $state<string>();
    let savedLocation: string | undefined = undefined;
    let isLoading = $state(true);
    let errorMessage = $state<string | null>(null);
    let currentTheme = $state<'light' | 'sepia' | 'dark'>('sepia');

    const themes = {
        light: {
            bg: '#ffffff',
            fg: '#1a1a1a',
            ui: 'rgba(0,0,0,0.05)',
            border: 'rgba(0,0,0,0.1)'
        },
        sepia: {
            bg: '#f4ecd8',
            fg: '#5b4636',
            ui: 'rgba(91,70,54,0.05)',
            border: 'rgba(91,70,54,0.1)'
        },
        dark: {
            bg: '#1a1a1a',
            fg: '#d1d1d1',
            ui: 'rgba(255,255,255,0.05)',
            border: 'rgba(255,255,255,0.1)'
        }
    };

    onMount(() => {
        if (!browser || !viewerElement) return;

        let disposed = false;
        const renderTarget = viewerElement;
        const loadingTimeout = setTimeout(() => {
            if (isLoading && !errorMessage) {
                errorMessage = t.get('common.reader.slow_loading');
            }
        }, 15000);

        const token = api.getToken();
        const headers: Record<string, string> = {};
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        const setupReader = async () => {
            try {
                const progressRes = await api.get<ReadingProgress>(`/books/${bookId}/progress`);
                if (progressRes.right?.cfi) {
                    savedLocation = progressRes.right.cfi;
                    lastSavedLocation = savedLocation;
                    console.log('Restoring saved location:', savedLocation);
                }
            } catch (e) {
                console.warn("Could not fetch reading progress", e);
            }

            if (disposed) return;

            const explodedUrl = `/api/books/${bookId}/epub/`;
            console.log('Attempting exploded EPUB:', explodedUrl);

            book = ePub(explodedUrl, {
                requestHeaders: headers
            });

            rendition = book.renderTo(renderTarget, {
                width: '100%',
                height: '100%',
                flow: 'paginated',
                manager: 'default'
            });

            Object.entries(themes).forEach(([name, style]) => {
                rendition?.themes.register(name, {
                    body: {
                        background: `${style.bg} !important`,
                        color: `${style.fg} !important`,
                        "font-family": "system-ui, -apple-system, sans-serif !important",
                        "font-size": "18px !important",
                        "line-height": "1.6 !important"
                    },
                    html: {
                        background: `${style.bg} !important`
                    },
                    img: {
                        "max-width": "100% !important",
                        "max-height": "100% !important",
                        "object-fit": "contain !important"
                    }
                });
            });

            rendition.themes.select(currentTheme);

            const tryDisplay = async () => {
                try {
                    await rendition?.display(savedLocation);
                    console.log('Rendition displayed at:', savedLocation || 'start');
                } catch (err) {
                    console.error('Error displaying rendition:', err);
                    if (!errorMessage) {
                        console.warn('Falling back to full EPUB download...');
                        book?.destroy();
                        book = ePub(`/api/books/${bookId}/download`, { requestHeaders: headers });
                        rendition = book.renderTo(renderTarget, {
                            width: '100%',
                            height: '100%',
                            flow: 'paginated'
                        });

                        Object.entries(themes).forEach(([name, style]) => {
                            rendition?.themes.register(name, {
                                body: { background: `${style.bg} !important`, color: `${style.fg} !important` },
                                html: { background: `${style.bg} !important` }
                            });
                        });
                        rendition.themes.select(currentTheme);

                        await rendition.display(savedLocation);
                    }
                }
            };

            book.opened.then(() => {
                console.log('Book opened successfully');
                void tryDisplay();
            }).catch((err: unknown) => {
                console.error('Error opening book:', err);
                console.warn('Falling back to full EPUB download due to open error...');
                book = ePub(`/api/books/${bookId}/download`, { requestHeaders: headers });
                rendition = book.renderTo(renderTarget, {
                    width: '100%',
                    height: '100%',
                    flow: 'paginated'
                });
                rendition.display(savedLocation).catch((_err: unknown) => {
                    errorMessage = t.get('common.reader.load_failed');
                    isLoading = false;
                });
            });

            book.ready.then(() => {
                console.log('Generating book locations...');
                return book?.locations.generate(2000);
            }).then(() => {
                isCalculatingProgress = false;
                console.log('Locations generated successfully');
                if (currentLocation && book?.locations) {
                    progress = book.locations.percentageFromCfi(currentLocation);
                }
            }).catch((err: unknown) => {
                console.warn('Failed to generate locations:', err);
                isCalculatingProgress = false;
            });

            book.loaded.navigation.then((nav: { toc: any[] }) => {
                toc = nav.toc;
            });

            rendition.on('relocated', (location: any) => {
                currentLocation = location.start.cfi;
                if (currentLocation && book?.locations && book.locations.length() > 0) {
                    progress = book.locations.percentageFromCfi(currentLocation);
                }

                if (currentLocation && currentLocation !== lastSavedLocation) {
                    void saveProgress(currentLocation);
                }
            });

            rendition.on('rendered', () => {
                isLoading = false;
                clearTimeout(loadingTimeout);
            });
        };

        // Keyboard navigation
        const handleKeydown = (e: KeyboardEvent) => {
            if (e.key === 'ArrowLeft') prevPage();
            if (e.key === 'ArrowRight') nextPage();
        };
        window.addEventListener('keydown', handleKeydown);

        // Handle window resizing
        const handleResize = () => {
            if (rendition && viewerElement) {
                rendition.resize(viewerElement.clientWidth, viewerElement.clientHeight);
            }
        };
        window.addEventListener('resize', handleResize);

        void setupReader();

        return () => {
            disposed = true;
            window.removeEventListener('keydown', handleKeydown);
            window.removeEventListener('resize', handleResize);
            clearTimeout(loadingTimeout);
            book?.destroy();
        };
    });

    let saveTimeout: ReturnType<typeof setTimeout>;

    async function saveProgress(cfi: string) {
        // Debounce the save to prevent hitting the API on every rapid page turn
        clearTimeout(saveTimeout);
        saveTimeout = setTimeout(async () => {
            console.log('Saving progress to backend:', cfi);
            const result = await api.put(`/books/${bookId}/progress`, { kind: 'EBOOK', cfi });
            if (!result.left) {
                lastSavedLocation = cfi;
            }
        }, 2000);
    }

    function prevPage() {
        rendition?.prev();
    }

    function nextPage() {
        rendition?.next();
    }

    function jumpTo(href: string) {
        rendition?.display(href);
        isTocOpen = false;
    }

    $effect(() => {
        if (rendition) {
            rendition.themes.select(currentTheme);
        }
    });
</script>

<div
    class="relative flex min-h-dvh w-screen flex-col overflow-hidden select-none transition-colors duration-500"
    style="background-color: {themes[currentTheme].bg}; color: {themes[currentTheme].fg}"
>
    <ReaderHeader 
        {bookId} 
        {title} 
        bind:currentTheme 
        {themes} 
        onPrev={prevPage} 
        onNext={nextPage} 
        onToggleToc={() => isTocOpen = !isTocOpen} 
    />

    <div class="relative flex flex-1 overflow-hidden">
        <ReaderTOC 
            {toc} 
            {currentTheme} 
            {themes} 
            bind:isOpen={isTocOpen} 
            onJumpTo={jumpTo} 
        />

        <main class="relative flex flex-1 flex-col items-center justify-center overflow-hidden bg-transparent">
            <ReaderOverlay 
                {bookId}
                {isLoading} 
                {errorMessage} 
                {currentTheme} 
                {themes} 
                onPrev={prevPage} 
                onNext={nextPage} 
            />

            <div
                bind:this={viewerElement}
                class="h-full w-full max-w-5xl overflow-hidden px-0 sm:px-4 md:px-6"
            ></div>
        </main>
    </div>

    <ReaderFooter 
        {progress} 
        {isCalculatingProgress}
        {currentTheme} 
        {themes} 
        onPrev={prevPage} 
        onNext={nextPage} 
    />
</div>

<style>
    :global(.epub-view iframe) {
        background: transparent !important;
    }

    /* Hide scrollbars globally for the reader route */
    :global(body) {
        overflow: hidden !important;
        width: 100%;
        height: 100%;
    }

    /* Aggressively hide any potential scrollbars */
    :global(::-webkit-scrollbar) {
        display: none !important;
    }
    :global(*) {
        scrollbar-width: none !important;
        -ms-overflow-style: none !important;
    }
</style>
