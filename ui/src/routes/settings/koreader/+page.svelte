<script lang="ts">
    import { onMount } from 'svelte';
    import { t } from '$lib/i18n';
    import { auth } from '$lib/auth.svelte';
    import { api } from '$lib/api/client';
    import { resolve } from '$app/paths';
    import { toast } from '$lib/state/toast.svelte';
    import FormField from '$lib/components/ui/FormField.svelte';
    import LoadingState from '$lib/components/ui/LoadingState/LoadingState.svelte';
    import EmptyState from '$lib/components/ui/EmptyState/EmptyState.svelte';
    import StatusBanner from '$lib/components/ui/StatusBanner.svelte';

    interface ApiToken {
        id: string;
        token: string;
        description: string;
        createdAt: string;
    }

    let tokens = $state<ApiToken[]>([]);
    let newTokenDescription = $state('');
    let createdToken = $state<ApiToken | null>(null);
    let loading = $state(true);

    const syncServerUrl = $derived(`${window.location.origin}/koreader/sync`);
    const webdavUrl = $derived(`${window.location.origin}/koreader/webdav/`);

    async function fetchTokens() {
        try {
            const result = await api.get<ApiToken[]>('/tokens');
            if (result.right) {
                tokens = result.right;
            } else {
                toast.error(result.left?.message || $t('settings.koreader.toasts.fetch_failed'));
            }
        } catch (e) {
            toast.error($t('settings.koreader.toasts.network_error'));
        } finally {
            loading = false;
        }
    }

    async function createToken() {
        if (!newTokenDescription) return;
        try {
            const result = await api.post<ApiToken>('/tokens', { description: newTokenDescription });
            if (result.right) {
                createdToken = result.right;
                newTokenDescription = '';
                toast.success($t('settings.koreader.toasts.created'));
                await fetchTokens();
            } else {
                toast.error(result.left?.message || $t('settings.koreader.toasts.create_failed'));
            }
        } catch (e) {
            toast.error($t('settings.koreader.toasts.network_error'));
        }
    }

    async function deleteToken(id: string) {
        try {
            const result = await api.delete(`/tokens/${id}`);
            if (!result.left) {
                toast.success($t('settings.koreader.toasts.revoked'));
                await fetchTokens();
            } else {
                toast.error(result.left.message || $t('settings.koreader.toasts.delete_failed'));
            }
        } catch (e) {
            toast.error($t('settings.koreader.toasts.network_error'));
        }
    }

    onMount(fetchTokens);
</script>

<div class="max-w-6xl mx-auto space-y-8">
    <header class="rounded-[1.75rem] border border-border/70 bg-card/70 p-6 shadow-xl shadow-black/5 backdrop-blur-md">
        <div class="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
            <div>
                <p class="mb-2 text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{$t('settings.koreader.eyebrow')}</p>
                <h2 class="font-display text-4xl font-bold text-primary">{$t('settings.koreader.title')}</h2>
                <p class="text-muted-foreground mt-2 max-w-2xl">{$t('settings.koreader.subtitle')}</p>
            </div>
        </div>
    </header>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-8">
        <!-- Connection Details -->
        <section class="bg-card border border-border rounded-lg p-6 space-y-4">
            <h2 class="text-xl font-semibold border-b border-border pb-2">{$t('settings.koreader.connection.title')}</h2>
            <div class="space-y-4">
                <div>
                    <span class="text-xs font-bold uppercase text-muted-foreground block mb-1">{$t('settings.koreader.connection.sync_server_url')}</span>
                    <code class="block p-2 bg-muted rounded text-sm overflow-x-auto">{syncServerUrl}</code>
                    <p class="text-xs text-muted-foreground mt-1">{$t('settings.koreader.connection.sync_server_hint')}</p>
                </div>
                <div>
                    <span class="text-xs font-bold uppercase text-muted-foreground block mb-1">{$t('settings.koreader.connection.webdav_url')}</span>
                    <code class="block p-2 bg-muted rounded text-sm overflow-x-auto">{webdavUrl}</code>
                    <p class="text-xs text-muted-foreground mt-1">{$t('settings.koreader.connection.webdav_hint')}</p>
                </div>
                <div>
                    <span class="text-xs font-bold uppercase text-muted-foreground block mb-1">{$t('settings.koreader.connection.username')}</span>
                    <code class="block p-2 bg-muted rounded text-sm">{auth.currentUser?.username}</code>
                </div>
            </div>

            <div class="rounded-lg border border-border bg-muted/30 p-4">
                <h3 class="font-semibold">{$t('settings.koreader.instructions.title')}</h3>
                <ol class="mt-2 list-decimal list-inside text-sm space-y-2 text-muted-foreground">
                    <li>{$t('settings.koreader.instructions.step_1')}</li>
                    <li>{$t('settings.koreader.instructions.step_2')}</li>
                    <li>{$t('settings.koreader.instructions.step_3')}</li>
                    <li>{$t('settings.koreader.instructions.step_4')}</li>
                    <li>{$t('settings.koreader.instructions.step_5')}</li>
                </ol>
            </div>
        </section>

        <!-- Token Management -->
        <section class="bg-card border border-border rounded-lg p-6 space-y-4">
            <h2 class="text-xl font-semibold border-b border-border pb-2">{$t('settings.koreader.tokens.title')}</h2>

            {#if createdToken}
                <div class="space-y-3 rounded-2xl border border-primary/20 bg-primary/10 p-4">
                    <StatusBanner kind="success" title={$t('settings.koreader.tokens.new_token_title')} message={$t('settings.koreader.tokens.new_token_message')} compact={true} />
                    <code class="block p-2 bg-background border border-border rounded text-sm break-all select-all font-mono font-bold">{createdToken.token}</code>
                    <button onclick={() => createdToken = null} class="text-xs text-primary hover:underline">{$t('settings.koreader.tokens.clear_notice')}</button>
                </div>
            {/if}

            <div class="flex gap-2 items-end">
                <FormField
                    label={$t('settings.koreader.tokens.description_label')}
                    forId="new-token-description"
                    hint={$t('settings.koreader.tokens.description_hint')}
                >
                    <input
                        id="new-token-description"
                        type="text"
                        bind:value={newTokenDescription}
                        placeholder={$t('settings.koreader.tokens.description_placeholder')}
                        class="ui-input-sm flex-1"
                    />
                </FormField>
                <button
                    onclick={createToken}
                    disabled={!newTokenDescription}
                    class="mb-[1px] rounded-xl bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50"
                >
                    {$t('settings.koreader.tokens.generate')}
                </button>
            </div>

            {#if loading}
                <LoadingState title={$t('settings.koreader.tokens.loading_title')} message={$t('settings.koreader.tokens.loading_message')} compact={true} />
            {:else if tokens.length === 0}
                <div class="rounded-2xl border border-dashed border-border bg-card/60 p-4">
                    <EmptyState
                        eyebrow={$t('settings.koreader.tokens.eyebrow')}
                        title={$t('settings.koreader.tokens.empty_title')}
                        message={$t('settings.koreader.tokens.empty_message')}
                    />
                </div>
            {:else}
                <ul class="divide-y divide-border border border-border rounded-md overflow-hidden">
                    {#each tokens as token (token.id)}
                        <li class="flex items-center justify-between p-3 bg-background hover:bg-accent/50 transition-colors">
                            <div>
                                <p class="text-sm font-medium">{token.description}</p>
                                <p class="text-xs text-muted-foreground">{$t('settings.koreader.tokens.added_on', { date: new Date(token.createdAt).toLocaleDateString() })}</p>
                            </div>
                            <button
                                onclick={() => deleteToken(token.id)}
                                class="p-2 text-muted-foreground hover:text-destructive transition-colors"
                                title={$t('settings.koreader.tokens.revoke')}
                            >
                                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                </svg>
                            </button>
                        </li>
                    {/each}
                </ul>
            {/if}
        </section>
    </div>

</div>
