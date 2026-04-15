<script lang="ts">
    import {t} from '$lib/i18n';
    import {resolve} from '$app/paths';
    import type {AuthorRoot, BookSeriesEntry} from '$lib/types/models';

    interface Props {
        title: string;
        authors?: AuthorRoot[];
        series?: BookSeriesEntry[];
        isAudiobook?: boolean;
        narrator?: string;
        editMode?: boolean;
        onEditToggle: () => void;
    }

    let {
        title,
        authors = [],
        series = [],
        isAudiobook = false,
        narrator,
        editMode = false,
        onEditToggle
    }: Props = $props();
</script>

<header class="mb-8 flex flex-col gap-5 rounded-[1.5rem] border border-border/70 bg-card/70 p-4 shadow-xl shadow-black/5 backdrop-blur-md sm:mb-10 sm:p-6 lg:flex-row lg:items-start lg:justify-between lg:gap-6">
    <div class="flex-1">
        <a href={resolve("/")}
           class="text-sm font-medium text-primary hover:text-primary transition-colors flex items-center mb-4">
            <span class="mr-1">←</span>{$t('books.header.back')}
        </a>

        {#if !editMode}
            <p class="mb-2 text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{$t('books.header.eyebrow')}</p>
            <h1 class="mb-3 font-display text-3xl font-bold leading-tight text-foreground sm:text-4xl lg:text-5xl">{title}</h1>
            <div class="flex flex-wrap items-center gap-x-3 gap-y-2 text-base text-muted-foreground sm:text-lg">
                {#if authors.length > 0}
                    <div class="flex items-center">
                        <span class="mr-2">{$t('books.header.by')}</span>
                        {#each authors as author, i (author.id)}
                            <a href={resolve(`/authors/${author.id}`)}
                               class="text-primary hover:text-primary/80 transition-colors font-medium">
                                {author.name}
                            </a>
                            {#if i < authors.length - 1}
                                <span class="mx-1">,</span>
                            {/if}
                        {/each}
                    </div>
                {:else}
                    <span>{$t('books.header.unknown_author')}</span>
                {/if}

                {#if series.length > 0}
                    <div class="flex items-center before:hidden sm:before:mx-3 sm:before:block sm:before:content-['•'] sm:before:text-gray-600">
                        {#each series as s, i (s.id)}
                            <a href={resolve(`/series/${s.id}`)}
                               class="text-primary hover:text-primary/80 transition-colors font-medium">
                                {s.name}{#if s.index != null} &nbsp;#{s.index}{/if}
                            </a>
                            {#if i < series.length - 1}
                                <span class="mx-1">,</span>
                            {/if}
                        {/each}
                    </div>
                {/if}
            </div>
        {/if}

        {#if isAudiobook && narrator && !editMode}
            <div class="mt-5 inline-flex items-center rounded-full border border-border bg-background/60 px-4 py-2 text-muted-foreground">
                <span class="mr-2 italic">{$t('books.header.narrated_by')}</span>
                <span class="text-primary font-medium">{narrator}</span>
            </div>
        {/if}
    </div>

    <div class="flex w-full gap-3 sm:w-auto">
        {#if !editMode}
            <button
                    onclick={onEditToggle}
                    class="w-full rounded-xl bg-primary px-5 py-3 font-bold text-primary-foreground shadow-lg shadow-primary/20 transition-all hover:bg-primary/90 sm:w-auto sm:px-6"
            >
                {$t('books.header.edit_metadata')}
            </button>
        {/if}
    </div>
</header>
