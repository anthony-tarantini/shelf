<script lang="ts">
    import { t } from '$lib/i18n';
    import { api } from '$lib/api/client';
    import type { UserRoot } from '$lib/types/models';
    import { UserRole } from '$lib/types/models';
    import { onMount } from 'svelte';
    import { auth } from '$lib/auth.svelte';
    import ConfirmDialog from '$lib/components/ui/ConfirmDialog.svelte';
    import { toast } from '$lib/state/toast.svelte';
    import FormField from '$lib/components/ui/FormField.svelte';
    import LoadingState from '$lib/components/ui/LoadingState/LoadingState.svelte';
    import StatusBanner from '$lib/components/ui/StatusBanner.svelte';

    let users = $state<UserRoot[]>([]);
    let loading = $state(true);
    let error = $state<string | null>(null);

    let newUserModalOpen = $state(false);
    let newEmail = $state('');
    let newUsername = $state('');
    let newPassword = $state('');
    let confirmState = $state<{
        type: 'self-demote' | 'delete-user';
        user?: UserRoot;
        nextRole?: UserRole | string;
    } | null>(null);
    let processingUserId = $state<string | null>(null);
    const adminCount = $derived(users.filter((user) => user.role === UserRole.ADMIN).length);
    const standardCount = $derived(users.length - adminCount);

    onMount(async () => {
        await loadUsers();
    });

    async function loadUsers() {
        loading = true;
        const result = await api.get<UserRoot[]>('/admin/users');
        if (result.right) {
            users = result.right;
            error = null;
        } else {
            error = $t('admin.users.failure.load');
        }
        loading = false;
    }

    async function handleRoleChange(user: UserRoot, newRole: UserRole | string) {
        if (user.id === auth.currentUser?.id && newRole !== UserRole.ADMIN) {
            confirmState = { type: 'self-demote', user, nextRole: newRole };
            return;
        }
        await updateRole(user, newRole);
    }

    async function updateRole(user: UserRoot, newRole: UserRole | string) {
        processingUserId = user.id;
        const result = await api.put(`/admin/users/${user.id}/role`, { role: newRole });
        if (result.right) {
            toast.success($t('admin.users.toasts.role_updated', { user: user.username }));
            await loadUsers();
            if (user.id === auth.currentUser?.id && newRole !== UserRole.ADMIN) {
                // Relog via refresh
                window.location.reload();
            }
        } else {
            toast.error($t('admin.users.failure.update'));
        }
        confirmState = null;
        processingUserId = null;
    }

    async function handleDeleteUser(user: UserRoot) {
        if (user.id === auth.currentUser?.id) {
            toast.error($t('admin.users.alert.self_delete'));
            return;
        }
        confirmState = { type: 'delete-user', user };
    }

    async function deleteUser(user: UserRoot) {
        processingUserId = user.id;
        const result = await api.delete(`/admin/users/${user.id}`);
        if (result.right) {
            toast.success($t('admin.users.toasts.deleted', { user: user.username }));
            await loadUsers();
        } else {
            toast.error($t('admin.users.failure.delete'));
        }
        confirmState = null;
        processingUserId = null;
    }

    async function handleCreateUser() {
        if (!newEmail || !newUsername || !newPassword) {
            toast.error($t('admin.users.alert.fill_all'));
            return;
        }
        const result = await api.post('/admin/users', {
            email: newEmail,
            username: newUsername,
            password: newPassword
        });

        if (result.right) {
            const createdUsername = newUsername;
            newUserModalOpen = false;
            newEmail = '';
            newUsername = '';
            newPassword = '';
            toast.success($t('admin.users.toasts.created', { user: createdUsername }));
            await loadUsers();
        } else {
            toast.error(result.left?.message || $t('admin.users.alert.create'));
        }
    }
</script>

<div class="space-y-6">
    <div class="flex items-center justify-between">
        <div>
            <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('common.accounts')}</p>
            <h2 class="mt-2 text-3xl font-bold tracking-tight">{$t('admin.users.management.title')}</h2>
            <p class="mt-1 text-muted-foreground">{$t('admin.users.management.subtitle')}</p>
        </div>
        <button 
            onclick={() => newUserModalOpen = true}
            class="bg-primary text-primary-foreground hover:bg-primary/90 px-4 py-2 rounded-md font-medium transition-colors flex items-center gap-2">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="19" y1="8" x2="19" y2="14"/><line x1="22" y1="11" x2="16" y2="11"/></svg>
            {$t('admin.users.management.add_user')}
        </button>
    </div>

    <div class="grid gap-4 md:grid-cols-3">
        <div class="rounded-2xl border border-border bg-card p-5 shadow-sm">
            <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('common.total')}</p>
            <p class="mt-3 text-3xl font-semibold text-foreground">{users.length}</p>
            <p class="mt-1 text-sm text-muted-foreground">{$t('admin.users.stats.total_description')}</p>
        </div>
        <div class="rounded-2xl border border-border bg-card p-5 shadow-sm">
            <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('common.admins')}</p>
            <p class="mt-3 text-3xl font-semibold text-foreground">{adminCount}</p>
            <p class="mt-1 text-sm text-muted-foreground">{$t('admin.users.stats.admins_description')}</p>
        </div>
        <div class="rounded-2xl border border-border bg-card p-5 shadow-sm">
            <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('common.readers')}</p>
            <p class="mt-3 text-3xl font-semibold text-foreground">{standardCount}</p>
            <p class="mt-1 text-sm text-muted-foreground">{$t('admin.users.stats.readers_description')}</p>
        </div>
    </div>

    {#if loading}
        <LoadingState title={$t('admin.users.status.loading_title')} message={$t('admin.users.status.loading_message')} compact={true} />
    {:else if error}
        <StatusBanner kind="error" title={$t('admin.users.status.load_error_title')} message={error} />
    {:else}
        <div class="overflow-hidden rounded-[1.5rem] border border-border bg-card shadow-sm">
            <table class="w-full text-left border-collapse">
                <thead>
                    <tr class="bg-muted/50 border-b border-border">
                        <th class="px-6 py-4 font-semibold text-muted-foreground text-sm uppercase tracking-wider">{$t('admin.users.management.username')}</th>
                        <th class="px-6 py-4 font-semibold text-muted-foreground text-sm uppercase tracking-wider">{$t('admin.users.management.email')}</th>
                        <th class="px-6 py-4 font-semibold text-muted-foreground text-sm uppercase tracking-wider">{$t('admin.users.management.role')}</th>
                        <th class="px-6 py-4 font-semibold text-muted-foreground text-sm uppercase tracking-wider text-right">{$t('admin.users.management.actions')}</th>
                    </tr>
                </thead>
                <tbody class="divide-y divide-border">
                    {#each users as user (user.id)}
                        <tr class="hover:bg-muted/30 transition-colors">
                            <td class="px-6 py-4">
                                <div class="font-medium text-foreground flex items-center gap-2">
                                    <div class="h-8 w-8 bg-primary/10 text-primary rounded-full flex items-center justify-center font-bold text-xs uppercase">
                                        {user.username.charAt(0)}
                                    </div>
                                    {user.username}
                                    {#if user.id === auth.currentUser?.id}
                                        <span class="ml-2 text-xs bg-primary/20 text-primary px-2 py-0.5 rounded-full font-bold">{$t('admin.users.management.you')}</span>
                                    {/if}
                                </div>
                            </td>
                            <td class="px-6 py-4 text-muted-foreground">{user.email}</td>
                            <td class="px-6 py-4">
                                <select 
                                    class="ui-select"
                                    value={user.role}
                                    onchange={(e) => handleRoleChange(user, e.currentTarget.value)}
                                    disabled={processingUserId === user.id}
                                >
                                    <option value={UserRole.ADMIN}>{$t('admin.users.management.administrator')}</option>
                                    <option value={UserRole.USER}>{$t('admin.users.management.standard_user')}</option>
                                </select>
                            </td>
                            <td class="px-6 py-4 text-right">
                                <button 
                                    onclick={() => handleDeleteUser(user)}
                                    disabled={user.id === auth.currentUser?.id}
                                    class="text-destructive hover:bg-destructive/10 p-2 rounded-md transition-colors disabled:opacity-30 disabled:hover:bg-transparent"
                                    title={$t('admin.users.management.delete_user')}
                                >
                                    <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg>
                                </button>
                            </td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        </div>
    {/if}
</div>

{#if newUserModalOpen}
<div class="fixed inset-0 z-50 flex items-center justify-center bg-background/80 backdrop-blur-sm p-4">
    <div class="w-full max-w-md overflow-hidden rounded-[1.5rem] border border-border bg-card shadow-xl">
        <div class="flex items-center justify-between border-b border-border bg-muted/30 px-6 py-4">
            <div>
                <p class="text-[10px] font-bold uppercase tracking-[0.28em] text-primary">{$t('common.accounts')}</p>
                <h3 class="mt-2 text-lg font-bold">{$t('admin.users.management.add_new')}</h3>
            </div>
            <button onclick={() => newUserModalOpen = false} class="text-muted-foreground hover:text-foreground" aria-label={$t('admin.users.management.close_modal')}>
                <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6 6 18"/><path d="m6 6 12 12"/></svg>
            </button>
        </div>
        <div class="p-6 space-y-4">
            <FormField label={$t('admin.users.management.username')} forId="newUsername">
                <input id="newUsername" type="text" bind:value={newUsername} class="ui-input" placeholder={$t('admin.users.management.placeholders.username')}>
            </FormField>
            <FormField label={$t('admin.users.management.email')} forId="newEmail">
                <input id="newEmail" type="email" bind:value={newEmail} class="ui-input" placeholder={$t('admin.users.management.placeholders.email')}>
            </FormField>
            <FormField label={$t('admin.users.management.password')} forId="newPassword">
                <input id="newPassword" type="password" bind:value={newPassword} class="ui-input">
            </FormField>
        </div>
        <div class="px-6 py-4 border-t border-border bg-muted/30 flex justify-end gap-3">
            <button onclick={() => newUserModalOpen = false} class="rounded-xl border border-border bg-accent px-4 py-2 text-sm font-medium hover:bg-accent/80">{$t('admin.users.management.cancel')}</button>
            <button onclick={handleCreateUser} class="rounded-xl bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90">{$t('admin.users.management.create')}</button>
        </div>
    </div>
</div>
{/if}

<ConfirmDialog
    open={confirmState !== null}
    title={confirmState?.type === 'self-demote'
        ? $t('admin.users.confirmation.self_demote_title')
        : $t('admin.users.confirmation.delete_title', { user: confirmState?.user?.username ?? 'user' })}
    message={confirmState?.type === 'self-demote'
        ? $t('admin.users.confirmation.self_demote_message')
        : $t('admin.users.confirmation.delete_message', { user: confirmState?.user?.username ?? 'this user' })}
    confirmLabel={confirmState?.type === 'self-demote'
        ? $t('admin.users.confirmation.self_demote_confirm')
        : $t('admin.users.confirmation.delete_confirm')}
    variant={confirmState?.type === 'delete-user' ? 'destructive' : 'default'}
    processing={processingUserId !== null}
    onCancel={() => (confirmState = null)}
    onConfirm={() => {
        if (confirmState?.type === 'self-demote' && confirmState.user && confirmState.nextRole) {
            void updateRole(confirmState.user, confirmState.nextRole);
        } else if (confirmState?.type === 'delete-user' && confirmState.user) {
            void deleteUser(confirmState.user);
        }
    }}
/>
