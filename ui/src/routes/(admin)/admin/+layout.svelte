<script lang="ts">
    import { t } from '$lib/i18n';
    import { auth } from '$lib/auth.svelte';
    import { goto } from '$app/navigation';
    import { resolve } from '$app/paths';
    import { page } from '$app/state';

    let { children } = $props();

    $effect(() => {
        if (auth.isInitialized) {
            if (!auth.currentUser) {
                goto(resolve('/login'));
            } else if (auth.currentUser.role !== 'ADMIN') {
                goto(resolve('/'));
            } else {
                goto(resolve('/admin/users'));
            }
        }
    });

    const isUsersPage = $derived(page.url.pathname === '/admin/users');
    const isLibraryPage = $derived(page.url.pathname === '/admin/library');
    const isSettingsPage = $derived(page.url.pathname === '/admin/settings');
</script>

{#if auth.isInitialized && auth.currentUser?.role === 'ADMIN'}
    <div class="flex min-h-dvh w-full flex-col bg-background text-foreground lg:flex-row">
        <!-- Admin Sidebar -->
        <aside class="relative z-10 flex shrink-0 flex-col border-b border-border bg-card shadow-lg lg:min-h-dvh lg:w-72 lg:border-b-0 lg:border-r">
            <div class="border-b border-border bg-card px-4 py-5 sm:px-6 sm:py-7">
                <div class="flex items-center gap-3">
                    <div class="rounded-xl bg-primary/10 p-3 text-primary">
                        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/></svg>
                    </div>
                    <div>
                        <p class="text-[11px] font-bold uppercase tracking-[0.28em] text-primary">{$t('admin.layout.eyebrow')}</p>
                        <h1 class="mt-1 text-xl font-bold tracking-tight text-foreground">{$t('admin.admin')}</h1>
                        <p class="text-xs font-semibold uppercase tracking-wider text-muted-foreground">{$t('admin.dashboard')}</p>
                    </div>
                </div>
                <p class="mt-4 text-sm leading-6 text-muted-foreground">
                    {$t('admin.layout.description')}
                </p>
            </div>
            
            <nav class="flex-1 space-y-2 overflow-y-auto p-4">
                <a href={resolve("/admin/users")} 
                   class="flex items-center gap-3 px-4 py-3 rounded-md transition-all font-medium {isUsersPage ? 'bg-primary text-primary-foreground shadow-md' : 'text-foreground hover:bg-accent hover:text-accent-foreground'}">
                   <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
                    {$t('admin.users.title')}
                </a>
                
                <a href={resolve("/admin/library")} 
                   class="flex items-center gap-3 px-4 py-3 rounded-md transition-all font-medium {isLibraryPage ? 'bg-primary text-primary-foreground shadow-md' : 'text-foreground hover:bg-accent hover:text-accent-foreground'}">
                   <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20"/></svg>
                    {$t('admin.library.title')}
                </a>
                
                <a href={resolve("/admin/settings")} 
                   class="flex items-center gap-3 px-4 py-3 rounded-md transition-all font-medium {isSettingsPage ? 'bg-primary text-primary-foreground shadow-md' : 'text-foreground hover:bg-accent hover:text-accent-foreground'}">
                   <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"/><circle cx="12" cy="12" r="3"/></svg>
                    {$t('admin.settings.title')}
                </a>
            </nav>
            
            <div class="border-t border-border bg-card/50 p-4">
                <div class="mb-4 rounded-2xl border border-border bg-background/80 p-4">
                    <p class="text-[11px] font-bold uppercase tracking-[0.28em] text-primary">{$t('common.scope')}</p>
                    <p class="mt-2 text-sm text-foreground">{$t('admin.layout.scope_title')}</p>
                    <p class="mt-1 text-xs leading-5 text-muted-foreground">{$t('admin.layout.scope_description')}</p>
                </div>
                <a href={resolve("/")} 
                   class="flex items-center justify-center gap-2 w-full px-4 py-2 text-sm font-medium border border-border rounded-md hover:bg-accent transition-all">
                   <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m15 18-6-6 6-6"/></svg>
                    {$t('admin.exit')}
                </a>
            </div>
        </aside>

        <!-- Admin Content -->
        <main class="flex-1 overflow-y-auto bg-background/50">
            <div class="mx-auto max-w-6xl p-4 sm:p-6 md:p-8">
                {@render children()}
            </div>
        </main>
    </div>
{:else}
    <div class="flex min-h-dvh items-center justify-center bg-background">
        <div class="h-10 w-10 border-4 border-primary border-t-transparent rounded-full animate-spin"></div>
    </div>
{/if}
