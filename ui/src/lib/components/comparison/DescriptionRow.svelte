<script lang="ts">
    import DOMPurify from 'dompurify';
    import {safeHtml} from '$lib/actions/safeHtml';
    import {t} from '$lib/i18n'

    let {
        currentHtml,
        externalHtml,
        onUseExternal
    } = $props<{
        currentHtml: string | null | undefined;
        externalHtml: string | null | undefined;
        onUseExternal: () => void;
    }>();

    // Reactive calculation to save CPU cycles on rendering
    let sanitizedCurrent = $derived(currentHtml ? DOMPurify.sanitize(currentHtml) : null);
    let sanitizedExternal = $derived(externalHtml ? DOMPurify.sanitize(externalHtml) : null);

    let showModal = $state(false);
    let modalType = $state<'current' | 'external'>('current');

    function openModal(type: 'current' | 'external') {
        modalType = type;
        showModal = true;
    }

    function closeModal() {
        showModal = false;
    }

    function handleUseExternal() {
        onUseExternal();
        closeModal();
    }
</script>

{#if sanitizedExternal}
    <tr class="bg-background">
        <td class="p-3 font-medium text-muted-foreground">{$t('common.description')}</td>

        <!-- Current Description Column -->
        <td class="p-3 text-foreground">
            <button
                    type="button"
                    onclick={() => openModal('current')}
                    class="group relative block w-full text-left rounded border border-transparent hover:border-primary hover:bg-primary/5 transition-all p-1"
            >
                <span class="line-clamp-3 text-xs">
                    {#if sanitizedCurrent}
                        <span use:safeHtml={sanitizedCurrent}></span>
                    {:else}
                        <span class="text-muted-foreground italic">{$t('metadata.description_row.none')}</span>
                    {/if}
                </span>
                {#if sanitizedCurrent}
                    <div class="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity bg-primary/10">
                        <span class="text-[10px] font-bold text-primary uppercase bg-background/90 px-1.5 py-0.5 rounded shadow-sm border border-primary/20">{$t('metadata.description_row.more')}</span>
                    </div>
                {/if}
            </button>
        </td>

        <!-- External Description Column -->
        <td class="p-3">
            <button
                    type="button"
                    onclick={() => openModal('external')}
                    class="group relative block w-full text-left rounded border border-transparent hover:border-primary hover:bg-primary/5 transition-all p-1">
                <span class="line-clamp-3 text-xs text-primary/80" use:safeHtml={sanitizedExternal}></span>
                <span class="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity bg-primary/10">
                    <span class="text-[10px] font-bold text-primary uppercase bg-background/90 px-1.5 py-0.5 rounded shadow-sm border border-primary/20">{$t('metadata.description_row.more')}</span>
                </span>
            </button>
        </td>
    </tr>
{/if}

<!-- Description Modal -->
{#if showModal}
    <div class="fixed inset-0 z-100 flex items-center justify-center p-4">
        <!-- Backdrop -->
        <button
                class="absolute inset-0 bg-black/60 backdrop-blur-sm w-full h-full cursor-default"
                onclick={closeModal}
                aria-label={$t('metadata.description_row.close_modal')}
        ></button>

        <!-- Modal Content -->
        <div
                role="dialog"
                aria-modal="true"
                aria-labelledby="modal-title"
                tabindex="-1"
                class="relative z-10 bg-card border border-border rounded-lg shadow-2xl w-full max-w-2xl max-h-[80vh] flex flex-col overflow-hidden focus:outline-none"
                onkeydown={(e) => e.key === 'Escape' && closeModal()}
        >
            <div class="flex items-center justify-between p-4 border-b border-border bg-muted/30">
                <h3 id="modal-title" class="font-bold text-primary">
                    {modalType === 'current' ? $t('metadata.current_description') : $t('metadata.external_description')}
                </h3>
                <button
                        onclick={closeModal}
                        class="text-muted-foreground hover:text-foreground p-1"
                        aria-label={$t('metadata.description_row.close_modal')}
                >
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
                        <path fill-rule="evenodd"
                              d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z"
                              clip-rule="evenodd"/>
                    </svg>
                </button>
            </div>

            <div class="p-6 overflow-y-auto text-sm leading-relaxed text-foreground prose prose-invert max-w-none">
                <span use:safeHtml={(modalType === 'current' ? sanitizedCurrent : sanitizedExternal) ?? ''}></span>
            </div>

            <div class="p-4 border-t border-border bg-muted/30 flex justify-end gap-3">
                <button
                        onclick={closeModal}
                        class="px-4 py-2 bg-accent hover:bg-accent/80 text-foreground font-bold rounded-md transition-colors"
                >
                    {$t('common.actions.close')}
                </button>
                {#if modalType === 'external'}
                    <button
                            onclick={handleUseExternal}
                            class="px-4 py-2 bg-primary hover:bg-primary/90 text-primary-foreground font-bold rounded-md transition-colors"
                    >
                        {$t('metadata.description_row.use_description')}
                    </button>
                {/if}
            </div>
        </div>
    </div>
{/if}
