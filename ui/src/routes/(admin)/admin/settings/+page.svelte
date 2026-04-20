<script lang="ts">
    import { t } from '$lib/i18n';
</script>

<div class="space-y-6">
    <div class="flex items-center justify-between">
        <div>
            <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('admin.settings.cards.configuration')}</p>
            <h2 class="mt-2 text-3xl font-bold tracking-tight">{$t('admin.settings.title')}</h2>
            <p class="mt-1 text-muted-foreground">{$t('admin.settings.subtitle')}</p>
        </div>
    </div>

    <div class="grid gap-4 md:grid-cols-3">
        <div class="rounded-2xl border border-border bg-card p-5 shadow-sm">
            <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('admin.settings.cards.provider_model')}</p>
            <p class="mt-3 text-lg font-semibold text-foreground">{$t('admin.settings.cards.provider_model_value')}</p>
            <p class="mt-1 text-sm text-muted-foreground">{$t('admin.settings.cards.provider_model_description')}</p>
        </div>
        <div class="rounded-2xl border border-border bg-card p-5 shadow-sm">
            <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('admin.settings.cards.change_cadence')}</p>
            <p class="mt-3 text-lg font-semibold text-foreground">{$t('admin.settings.cards.change_cadence_value')}</p>
            <p class="mt-1 text-sm text-muted-foreground">{$t('admin.settings.cards.change_cadence_description')}</p>
        </div>
        <div class="rounded-2xl border border-border bg-card p-5 shadow-sm">
            <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('admin.settings.cards.current_state')}</p>
            <p class="mt-3 text-lg font-semibold text-foreground">{$t('admin.settings.cards.current_state_value')}</p>
            <p class="mt-1 text-sm text-muted-foreground">{$t('admin.settings.cards.current_state_description')}</p>
        </div>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
        <!-- Metadata Providers -->
        <div class="bg-card border border-border rounded-[1.5rem] shadow-sm overflow-hidden">
            <div class="p-6 border-b border-border bg-muted/30">
                <div class="flex items-center justify-between gap-4">
                    <h3 class="font-bold text-lg flex items-center gap-2">
                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-primary"><path d="M12 2v20"/><path d="m17 5-5-3-5 3"/><path d="m7 19 5 3 5-3"/></svg>
                    {$t('admin.settings.providers.title')}
                    </h3>
                    <span class="rounded-full border border-border bg-background px-3 py-1 text-xs font-semibold uppercase tracking-[0.24em] text-muted-foreground">{$t('admin.settings.cards.read_only')}</span>
                </div>
            </div>
            <div class="p-6 space-y-4">
                <p class="text-sm text-muted-foreground">
                    {$t('admin.settings.providers.description')}
                </p>
                <div class="space-y-3">
                    <div>
                        <span class="block text-xs font-bold text-muted-foreground uppercase tracking-wider mb-1">{$t('admin.settings.providers.hardcover.title')}</span>
                        <div class="bg-muted p-2 rounded-md font-mono text-xs text-muted-foreground truncate">{$t('admin.settings.providers.hardcover.description')}</div>
                    </div>
                </div>
                <button class="px-4 py-2 bg-muted text-muted-foreground rounded-md text-sm font-medium w-full mt-4 cursor-not-allowed" disabled>
                    {$t('admin.settings.providers.edit')}
                </button>
            </div>
        </div>
    </div>
</div>
