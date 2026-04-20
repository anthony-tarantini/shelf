<script lang="ts">
    import {t} from '$lib/i18n';
    import {api} from '$lib/api/client';
    import { toast } from '$lib/state/toast.svelte';
    import FormField from '$lib/components/ui/FormField.svelte';

    let scanning = $state(false);
    let targetPath = $state('');
    const scanRootHint = $derived(t.get('admin.library.scan_root_hint'));

    async function handleScan() {
        if (!targetPath.trim()) {
            toast.error($t('admin.library.prompt'));
            return;
        }

        scanning = true;

        const result = await api.post('/import/scan', {path: targetPath.trim()});
        if (result.right) {
            toast.success($t('admin.library.initialized'));
            targetPath = '';
        } else {
            toast.error($t('admin.library.failed', {error: result.left?.message || $t('errors.unknown')}));
        }

        scanning = false;
    }
</script>

<div class="space-y-6">
    <div class="flex items-center justify-between">
        <div>
            <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('admin.library.eyebrow')}</p>
            <h2 class="mt-2 text-3xl font-bold tracking-tight">{$t('admin.library.title')}</h2>
            <p class="mt-1 text-muted-foreground">{$t('admin.library.configure')}</p>
        </div>
    </div>

    <div class="grid gap-4 md:grid-cols-3">
        <div class="rounded-2xl border border-border bg-card p-5 shadow-sm">
            <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('admin.library.stats.scope_title')}</p>
            <p class="mt-3 text-lg font-semibold text-foreground">{$t('admin.library.stats.scope_value')}</p>
            <p class="mt-1 text-sm text-muted-foreground">{$t('admin.library.stats.scope_description')}</p>
        </div>
        <div class="rounded-2xl border border-border bg-card p-5 shadow-sm">
            <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('admin.library.stats.impact_title')}</p>
            <p class="mt-3 text-lg font-semibold text-foreground">{$t('admin.library.stats.impact_value')}</p>
            <p class="mt-1 text-sm text-muted-foreground">{$t('admin.library.stats.impact_description')}</p>
        </div>
        <div class="rounded-2xl border border-border bg-card p-5 shadow-sm">
            <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('admin.library.stats.best_use_title')}</p>
            <p class="mt-3 text-lg font-semibold text-foreground">{$t('admin.library.stats.best_use_value')}</p>
            <p class="mt-1 text-sm text-muted-foreground">{$t('admin.library.stats.best_use_description')}</p>
        </div>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <!-- Scan Card -->
        <div class="bg-card border border-border rounded-lg shadow-sm overflow-hidden">
            <div class="p-6 border-b border-border bg-muted/30">
                <h3 class="font-bold text-lg flex items-center gap-2">
                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                         class="text-primary">
                        <path d="m21 16-4 4-4-4"/>
                        <path d="M17 20V4"/>
                        <path d="m3 8 4-4 4 4"/>
                        <path d="M7 4v16"/>
                    </svg>
                    {$t('admin.library.scan')}
                </h3>
            </div>
            <div class="p-6 space-y-4">
                <p class="text-sm text-muted-foreground">
                    {$t('admin.library.scan_description')}
                </p>
                <div class="rounded-md border border-border bg-muted/50 p-3 text-sm text-muted-foreground">
                    {scanRootHint}
                </div>
                <FormField
                    label={$t('admin.library.path_label')}
                    forId="scan-path"
                    hint={$t('admin.library.path_hint')}
                >
                    <input
                        id="scan-path"
                        type="text"
                        bind:value={targetPath}
                        placeholder={$t('admin.library.path_placeholder')}
                        class="ui-input-sm"
                    />
                </FormField>
                <button
                        onclick={handleScan}
                        disabled={scanning}
                        class="flex w-full items-center justify-center gap-2 rounded-xl bg-primary px-4 py-3 font-bold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-50">
                    {#if scanning}
                        <div class="h-5 w-5 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                        {$t('admin.library.initializing')}
                    {:else}
                        <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none"
                             stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="11" cy="11" r="8"/>
                            <path d="m21 21-4.3-4.3"/>
                        </svg>
                        {$t('admin.library.scan')}
                    {/if}
                </button>
            </div>
        </div>

        <!-- Storage Config Card (Placeholder) -->
        <div class="bg-card border border-border rounded-lg shadow-sm overflow-hidden opacity-90">
            <div class="p-6 border-b border-border bg-muted/30">
                <h3 class="font-bold text-lg flex items-center gap-2">
                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
                         stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                         class="text-primary">
                        <path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"/>
                    </svg>
                    {$t('admin.library.storage_paths')}
                </h3>
            </div>
            <div class="p-6 space-y-4">
                <p class="text-sm text-muted-foreground">
                    {$t('admin.library.storage_paths_description')}
                </p>
                <div class="space-y-2 pt-2 border-t border-border">
                    <div>
                        <span class="block text-xs font-bold text-muted-foreground uppercase tracking-wider mb-1">{$t('admin.library.primary_storage')}</span>
                        <div class="bg-muted p-2 rounded-md text-sm">{scanRootHint}</div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
