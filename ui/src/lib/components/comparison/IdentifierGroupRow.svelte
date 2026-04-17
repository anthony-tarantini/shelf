<script lang="ts">
    import IdentifierButton from "./IdentifierButton.svelte";
    import type { StagedEditionMetadata } from "$lib/types/models";
    import { t } from '$lib/i18n';

    interface IdentifierData {
        isbn10?: string | null;
        isbn13?: string | null;
        asin?: string | null;
    }

    let {
        label,
        currentMetadata,
        externalData,
        onUseExternal
    } = $props<{
        label: string;
        currentMetadata?: StagedEditionMetadata | null;
        externalData?: IdentifierData | null;
        onUseExternal: (type: 'isbn10' | 'isbn13' | 'asin', identifier: string) => void;
    }>();

    let hasExternalOptions = $derived(
        externalData?.isbn10 || externalData?.isbn13 || externalData?.asin
    );
</script>

{#if hasExternalOptions}
    <tr class="bg-background">
        <td class="p-3 font-medium text-muted-foreground">{label}</td>
        <td class="p-3 text-foreground">
            <div class="flex flex-col gap-1 text-sm">
                {#if currentMetadata?.isbn13}<div>{$t('metadata.comparison.identifier_isbn13')}: {currentMetadata.isbn13}</div>{/if}
                {#if currentMetadata?.isbn10}<div>{$t('metadata.comparison.identifier_isbn10')}: {currentMetadata.isbn10}</div>{/if}
                {#if currentMetadata?.asin}<div>{$t('metadata.comparison.identifier_asin')}: {currentMetadata.asin}</div>{/if}
                {#if !currentMetadata?.isbn13 && !currentMetadata?.isbn10 && !currentMetadata?.asin}
                    <span class="text-muted-foreground italic">{$t('common.none')}</span>
                {/if}
            </div>
        </td>
        <td class="p-3">
            <div class="flex flex-wrap gap-2">
                {#if externalData?.isbn10}
                    <IdentifierButton value={externalData.isbn10} typeKey='ISBN10' onUse={(val) => onUseExternal('isbn10', val)}/>
                {/if}
                {#if externalData?.isbn13}
                    <IdentifierButton value={externalData.isbn13} typeKey='ISBN13' onUse={(val) => onUseExternal('isbn13', val)}/>
                {/if}
                {#if externalData?.asin}
                    <IdentifierButton value={externalData.asin} typeKey='ASIN' onUse={(val) => onUseExternal('asin', val)}/>
                {/if}
            </div>
        </td>
    </tr>
{/if}
