<script lang="ts">
    import { t } from '$lib/i18n';
    import { api } from '$lib/api/client';
    import { onMount } from 'svelte';
    import { toast } from '$lib/state/toast.svelte';

    let syncMetadataToFiles = $state(false);
    let loading = $state(true);
    let saving = $state(false);

    onMount(async () => {
        const result = await api.get<{ syncMetadataToFiles: boolean }>('/settings/user');
        if (result.right) {
            syncMetadataToFiles = result.right.syncMetadataToFiles;
        }
        loading = false;
    });

    async function toggleSync() {
        saving = true;
        const result = await api.patch<{ syncMetadataToFiles: boolean }>('/settings/user', {
            syncMetadataToFiles: !syncMetadataToFiles
        });
        
        if (result.right) {
            syncMetadataToFiles = result.right.syncMetadataToFiles;
            toast.success($t('settings.general.save_success'));
        } else {
            toast.error(result.left?.message || $t('settings.general.save_error'));
        }
        saving = false;
    }
</script>

<div class="space-y-8">
    <header class="rounded-[1.75rem] border border-border/70 bg-card/70 p-6 shadow-xl shadow-black/5 backdrop-blur-md">
        <div class="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
            <div>
                <p class="mb-2 text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{$t('settings.nav.title')}</p>
                <h2 class="font-display text-4xl font-bold text-primary">{$t('settings.general.title')}</h2>
                <p class="text-muted-foreground mt-2 max-w-2xl">{$t('settings.general.description')}</p>
            </div>
        </div>
    </header>

    {#if loading}
        <div class="animate-pulse space-y-4">
            <div class="h-12 bg-muted rounded-xl w-full"></div>
        </div>
    {:else}
        <div class="bg-card/50 border border-border rounded-2xl p-6">
            <div class="flex items-center justify-between gap-4">
                <div class="flex-1">
                    <h3 class="font-bold text-foreground">{$t('settings.general.sync_metadata.title')}</h3>
                    <p class="text-sm text-muted-foreground mt-1">{$t('settings.general.sync_metadata.description')}</p>
                    <p class="text-xs text-primary font-medium mt-2">{$t('settings.general.sync_metadata.warning')}</p>
                </div>
                <button
                    onclick={toggleSync}
                    disabled={saving}
                    class="relative inline-flex h-6 w-11 items-center rounded-full transition-colors focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 {syncMetadataToFiles ? 'bg-primary' : 'bg-muted'}"
                    role="switch"
                    aria-checked={syncMetadataToFiles}
                >
                    <span class="sr-only">{$t('settings.general.sync_metadata.toggle')}</span>
                    <span
                        class="inline-block h-4 w-4 transform rounded-full bg-white transition-transform {syncMetadataToFiles ? 'translate-x-6' : 'translate-x-1'}"
                    ></span>
                </button>
            </div>
        </div>
    {/if}
</div>
