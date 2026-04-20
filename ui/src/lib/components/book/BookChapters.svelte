<script lang="ts">
    import { t } from '$lib/i18n';
    import {formatDuration} from '$lib/utils';
    import {MediaType, type MetadataAggregate} from '$lib/types/models';

    let {
        metadata,
        mediaType = MediaType.EBOOK
    } = $props<{
        metadata: MetadataAggregate;
        mediaType?: MediaType | string;
    }>();

    function getChapters(metadata: MetadataAggregate): MetadataAggregate['editions'][number]['chapters'] {
        return metadata.editions.flatMap((edition) => edition.chapters);
    }

    const chapters = $derived(getChapters(metadata));
</script>

{#if chapters.length > 0}
    <section>
        <h2 class="text-xl font-bold text-primary mb-4 flex items-center">
            {$t('books.chapters.title')}
            <span class="h-px bg-card flex-1 ml-4"></span>
        </h2>
        <div class="bg-card/30 border border-border rounded-lg overflow-hidden">
            <div class="max-h-125 overflow-y-auto custom-scrollbar">
                <table class="w-full text-left text-sm border-collapse">
                    <thead class="bg-card/50 sticky top-0">
                    <tr>
                        <th class="px-4 py-3 font-bold text-muted-foreground uppercase tracking-wider text-xs border-b border-border">
                            #
                        </th>
                        <th class="px-4 py-3 font-bold text-muted-foreground uppercase tracking-wider text-xs border-b border-border">
                            {$t('books.chapters.column_title')}
                        </th>
                        {#if mediaType === MediaType.AUDIOBOOK}
                            <th class="px-4 py-3 font-bold text-muted-foreground uppercase tracking-wider text-xs border-b border-border text-right">
                                {$t('books.chapters.column_start_time')}
                            </th>
                            <th class="px-4 py-3 font-bold text-muted-foreground uppercase tracking-wider text-xs border-b border-border text-right">
                                {$t('books.chapters.column_length')}
                            </th>
                        {/if}
                    </tr>
                    </thead>
                    <tbody class="divide-y divide-gray-700/50">
                    {#each chapters as chapter, i (chapter.id)}
                        <tr class="hover:bg-accent/30 transition-colors group">
                            <td class="px-4 py-3 text-muted-foreground font-mono w-12">{chapter.index ?? i + 1}</td>
                            <td class="px-4 py-3 text-muted-foreground group-hover:text-foreground transition-colors">{chapter.title}</td>
                            {#if mediaType === MediaType.AUDIOBOOK}
                                <td class="px-4 py-3 text-muted-foreground font-mono text-right">
                                    {formatDuration(chapter.startTime)}
                                </td>
                                <td class="px-4 py-3 text-muted-foreground font-mono text-right">
                                    {chapter.endTime && chapter.startTime !== undefined ? formatDuration(chapter.endTime - chapter.startTime) : '-'}
                                </td>
                            {/if}
                        </tr>
                    {/each}
                    </tbody>
                </table>
            </div>
        </div>
    </section>
{/if}
