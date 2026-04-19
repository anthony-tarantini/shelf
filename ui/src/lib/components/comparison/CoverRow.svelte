<script lang="ts">
    import { t } from '$lib/i18n';

    let {
        bookId,
        originalCoverPath,
        mergedCoverUrl,
        externalCoverUrl,
        onUseExternal,
        internalImageComponent,
        internalCoverApiPath = `/api/books/staged/${bookId}/cover`
    } = $props<{
        bookId: string;
        originalCoverPath: string | null | undefined;
        mergedCoverUrl: string | null | undefined;
        externalCoverUrl: string | null | undefined;
        onUseExternal: () => void;
        internalImageComponent?: typeof import('$lib/components/ui/AuthenticatedImage.svelte').default;
        internalCoverApiPath?: string;
    }>();

    let isInsecure = $derived(externalCoverUrl && !externalCoverUrl.startsWith('https://'));

    let internalMergedUrl = $derived.by(() => {
        if (!mergedCoverUrl) return null;
        return mergedCoverUrl === originalCoverPath ? internalCoverApiPath : null;
    });

    const InternalImage = $derived(internalImageComponent);

    let externalMergedUrl = $derived.by(() => {
        if (!mergedCoverUrl) return null;
        return mergedCoverUrl === originalCoverPath ? null : mergedCoverUrl;
    });
</script>

{#if externalCoverUrl}
    <tr class="bg-background">
        <td class="p-3 font-medium text-muted-foreground">{$t('metadata.cover.label')}</td>

        <td class="p-3 text-foreground">
            {#if internalMergedUrl && InternalImage}
                <InternalImage
                    src={internalMergedUrl}
                    alt={$t('metadata.cover.current_alt')}
                    class="h-24 w-16 object-cover rounded border border-border bg-card"
                />
            {:else if externalMergedUrl}
                <img
                    src={externalMergedUrl}
                    alt={$t('metadata.cover.current_alt')}
                    class="h-24 w-16 object-cover rounded border border-border bg-card"
                />
            {:else}
                <span class="text-muted-foreground italic">{$t('common.none')}</span>
            {/if}
        </td>

        <td class="p-3">
            <button
                    type="button"
                    onclick={onUseExternal}
                    disabled={isInsecure}
                    class="group relative block rounded overflow-hidden border border-border hover:border-primary transition-all disabled:opacity-50 disabled:cursor-not-allowed"
                    title={isInsecure ? $t('metadata.cover.insecure') : `${$t('metadata.use')} ${$t('metadata.cover.external')}`}
            >
                {#if isInsecure}
                    <div class="h-24 w-16 bg-card flex items-center justify-center text-[10px] text-muted-foreground text-center px-1">
                        {$t('metadata.cover.insecure')}
                    </div>
                {:else}
                    <img
                            src={externalCoverUrl}
                            alt={$t('metadata.cover.external_alt')}
                            class="h-24 w-16 object-cover bg-card group-hover:opacity-75 transition-opacity"
                    />
                    <div class="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity bg-primary/20">
                        <span class="text-[10px] font-bold text-primary uppercase bg-background/80 px-1.5 py-0.5 rounded shadow-sm border border-primary/20">{$t('metadata.cover.use')}</span>
                    </div>
                {/if}
            </button>
        </td>
    </tr>
{/if}
