<script lang="ts">
	import '../app.css';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { resolve } from '$app/paths';
	import { t } from '$lib/i18n';
	import { auth } from '$lib/auth.svelte';
	import { safeHtml } from '$lib/actions/safeHtml';
	import ReloadPrompt from '$lib/ReloadPrompt.svelte';
	import PwaInstallPrompt from '$lib/PwaInstallPrompt.svelte';
	import ToastViewport from '$lib/components/ui/ToastViewport.svelte';
	import GlobalSearch from '$lib/components/GlobalSearch.svelte';
	import GlobalProgress from '$lib/components/ui/GlobalProgress.svelte';
	import { progress } from '$lib/state/progress.svelte';
	import { getSearchShortcutHint } from '$lib/utils';
	import { UserRole } from '$lib/types/models';
	import { pwaInfo } from 'virtual:pwa-info';

	let { children } = $props();

	let isSearchOpen = $state(false);
	let isMobileNavOpen = $state(false);

	function handleLogout() {
		auth.logout();
		progress.stopPolling();
		goto(resolve('/login'));
	}

	$effect(() => {
		if (auth.currentUser) {
			progress.startPolling();
		}
	});

	$effect(() => {
		const isAuthPage = page.url.pathname === '/login' || page.url.pathname === '/register';
		const isSetupPage = page.url.pathname === '/setup';
		if (auth.isInitialized) {
			if (auth.isSetupRequired && !isSetupPage) {
				goto(resolve('/setup'));
			} else if (!auth.isSetupRequired && isSetupPage) {
				goto(resolve('/login'));
			} else if (!auth.currentUser && !isAuthPage && !isSetupPage) {
				goto(resolve('/login'));
			}
		}
	});

	$effect(() => {
		void page.url.pathname;
		isMobileNavOpen = false;
	});

	const isAdminPage = $derived(page.url.pathname.startsWith('/admin'));
	const isAuthPage = $derived(
		page.url.pathname === '/login' || page.url.pathname === '/register' || page.url.pathname === '/setup'
	);
	const isReaderPage = $derived(page.url.pathname.startsWith('/read/'));

	const catalogNavItems = [
		{ href: '/', label: 'common.app_shell.library', icon: 'library' },
		{ href: '/authors', label: 'common.app_shell.authors', icon: 'authors' },
		{ href: '/series', label: 'common.app_shell.series', icon: 'series' }
	];

	const toolNavItems = [
		{ href: '/import', label: 'common.app_shell.import', icon: 'import' },
		{ href: '/import/staged', label: 'common.app_shell.staging', icon: 'staging' },
		{ href: '/settings/koreader', label: 'common.app_shell.koreader_sync', icon: 'sync' }
	];

	function isActive(href: string) {
		if (href === '/') return page.url.pathname === href;
		if (href === '/import') return page.url.pathname === '/import';
		return page.url.pathname.startsWith(href);
	}

	function iconPath(icon: string) {
		switch (icon) {
			case 'library':
				return 'M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20';
			case 'authors':
				return 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75M9 7a4 4 0 1 0 0 .01';
			case 'series':
				return 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01';
			case 'import':
				return 'M12 3v12M7 10l5 5 5-5M5 21h14';
			case 'staging':
				return 'M5 12l5 5L20 7';
			case 'sync':
				return 'M21 12a9 9 0 0 1-15.55 6.36L3 16m18-4-2.45-2.36A9 9 0 0 0 3 12m0 0a9 9 0 0 1 15.55-6.36L21 8';
			default:
				return 'M12 12h.01';
		}
	}
</script>

<svelte:head>
	<span use:safeHtml={pwaInfo?.webManifest.linkTag ?? ''}></span>
</svelte:head>

<ReloadPrompt />
<ToastViewport />
<GlobalSearch bind:isOpen={isSearchOpen} />

{#if !auth.isInitialized}
	<div class="flex min-h-dvh items-center justify-center bg-background">
		<div class="flex flex-col items-center">
			<div class="mb-4 h-12 w-12 animate-spin rounded-full border-4 border-primary/20 border-t-primary"></div>
			<p class="font-medium text-muted-foreground">{$t('common.shelf_loading')}</p>
		</div>
	</div>
{:else}
	<div class="min-h-dvh bg-background text-foreground">
		{#if isAuthPage || isReaderPage || isAdminPage}
			<main class="min-h-dvh w-full">
				{@render children()}
			</main>
		{:else}
			<div class="min-h-dvh bg-[radial-gradient(circle_at_top_left,_color-mix(in_srgb,var(--primary)_16%,transparent),transparent_26rem)] lg:flex">
				<aside class="hidden w-72 shrink-0 border-r border-border bg-card/90 backdrop-blur-md lg:flex lg:min-h-dvh lg:flex-col">
					<div class="p-6 pb-4">
						<p class="mb-3 text-[10px] uppercase tracking-[0.3em] text-muted-foreground">
							{$t('common.app_shell.sidebar_eyebrow')}
						</p>
						<h1 class="font-display text-3xl font-bold tracking-tight text-primary">Shelf</h1>
						<p class="mt-3 text-sm leading-relaxed text-muted-foreground">
							{$t('common.app_shell.sidebar_description')}
						</p>
					</div>
					<div class="px-4 pb-3">
						<button
							onclick={() => (isSearchOpen = true)}
							class="group flex w-full items-center gap-3 rounded-xl border border-border bg-background/60 px-4 py-2.5 text-left text-muted-foreground transition-all hover:border-primary/50 hover:text-foreground"
						>
							<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 shrink-0 transition-colors group-hover:text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
								<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0 1 14 0z" />
							</svg>
							<span class="flex-1 text-sm">{$t('common.search.trigger')}</span>
							<kbd class="rounded border border-border bg-accent/50 px-1.5 py-0.5 text-[10px]">
								{getSearchShortcutHint()}
							</kbd>
						</button>
					</div>
					<nav class="flex-1 space-y-1 px-4">
						{#each catalogNavItems as item (item.href)}
							<a
								href={item.href}
								class="block rounded-xl border px-4 py-3 transition-all {isActive(item.href) ? 'border-primary bg-primary text-primary-foreground shadow-lg shadow-primary/15' : 'border-transparent text-muted-foreground hover:bg-accent/70 hover:text-foreground'}"
							>
								<span class="text-sm font-semibold">{$t(item.label)}</span>
							</a>
						{/each}
						<div class="my-2 border-t border-border/50"></div>
						{#each toolNavItems as item (item.href)}
							<a
								href={item.href}
								class="block rounded-xl border px-4 py-3 transition-all {isActive(item.href) ? 'border-primary bg-primary text-primary-foreground shadow-lg shadow-primary/15' : 'border-transparent text-muted-foreground hover:bg-accent/70 hover:text-foreground'}"
							>
								<span class="text-sm font-semibold">{$t(item.label)}</span>
							</a>
						{/each}
					</nav>
					<GlobalProgress />
					<div class="space-y-4 border-t border-border p-4">
						{#if auth.currentUser}
							<div>
								<div class="mb-2 rounded-xl border border-border bg-background/60 px-4 py-3 shadow-sm">
									<p class="mb-1 text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
										{$t('common.app_shell.logged_in_as')}
									</p>
									<p class="truncate text-sm font-semibold leading-tight text-primary">{auth.currentUser.username}</p>
								</div>

								<div class="space-y-1">
									{#if auth.currentUser.role === UserRole.ADMIN}
										<a href={resolve('/admin/users')} class="group flex items-center gap-2 rounded-md px-4 py-2 text-sm font-medium text-muted-foreground transition-all hover:bg-accent hover:text-foreground">
											<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="text-primary/70 transition-colors group-hover:text-primary">
												<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path>
											</svg>
											{$t('common.app_shell.admin_dashboard')}
										</a>
									{/if}

									<button
										onclick={handleLogout}
										class="group flex w-full items-center gap-2 rounded-md px-4 py-2 text-sm font-medium text-muted-foreground transition-all hover:bg-destructive/10 hover:text-destructive"
									>
										<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="transition-colors group-hover:text-destructive">
											<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line>
										</svg>
										{$t('common.app_shell.logout')}
									</button>
								</div>
							</div>
						{:else}
							<a href={resolve('/login')} class="block w-full rounded-md bg-primary px-4 py-2 text-center text-sm font-bold text-primary-foreground shadow-sm transition-all hover:bg-primary/90">
								{$t('common.app_shell.login')}
							</a>
						{/if}
					</div>
				</aside>

				<div class="flex min-h-dvh min-w-0 flex-1 flex-col">
					<header class="sticky top-0 z-30 border-b border-border/70 bg-background/85 backdrop-blur-md lg:hidden" style="padding-top: max(0.75rem, env(safe-area-inset-top));">
						<div class="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 pb-3">
							<div class="min-w-0">
								<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">
									{$t('common.app_shell.mobile_eyebrow')}
								</p>
								<h1 class="font-display text-2xl text-primary">Shelf</h1>
							</div>
							<div class="flex items-center gap-2">
								<button
									type="button"
									onclick={() => (isSearchOpen = true)}
									class="rounded-xl border border-border bg-card/80 p-3 text-foreground transition-colors hover:bg-accent"
									aria-label={$t('common.search.open_mobile')}
								>
									<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
										<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0 1 14 0z" />
									</svg>
								</button>
								<button
									type="button"
									onclick={() => (isMobileNavOpen = true)}
									class="rounded-xl border border-border bg-card/80 p-3 text-foreground transition-colors hover:bg-accent"
									aria-label={$t('common.app_shell.open_navigation')}
								>
									<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
										<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
									</svg>
								</button>
							</div>
						</div>
					</header>

					<main class="min-w-0 flex-1 overflow-y-auto">
						<div class="mx-auto flex w-full max-w-7xl flex-col gap-6 px-4 py-4 pb-[calc(5.5rem+env(safe-area-inset-bottom))] sm:px-6 sm:py-6 md:gap-8 md:px-8 lg:px-10 lg:py-8 lg:pb-10">
							<div class="hidden lg:block">
								<PwaInstallPrompt />
							</div>
							{@render children()}
						</div>
					</main>
				</div>

				<nav class="fixed inset-x-0 bottom-0 z-30 border-t border-border/70 bg-card/95 px-2 py-2 shadow-[0_-8px_24px_rgba(0,0,0,0.12)] backdrop-blur-md lg:hidden" style="padding-bottom: max(0.5rem, env(safe-area-inset-bottom));">
					<div class="mx-auto grid max-w-xl grid-cols-4 gap-1">
						{#each catalogNavItems as item (item.href)}
							<a
								href={item.href}
								class="flex min-h-14 flex-col items-center justify-center gap-1 rounded-2xl px-2 py-2 text-[11px] font-semibold transition-colors {isActive(item.href) ? 'bg-primary text-primary-foreground' : 'text-muted-foreground hover:bg-accent hover:text-foreground'}"
							>
								<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
									<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d={iconPath(item.icon)} />
								</svg>
								<span>{$t(item.label)}</span>
							</a>
						{/each}
						<button
							type="button"
							onclick={() => (isSearchOpen = true)}
							class="flex min-h-14 flex-col items-center justify-center gap-1 rounded-2xl px-2 py-2 text-[11px] font-semibold text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
						>
							<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
								<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0 1 14 0z" />
							</svg>
							<span>{$t('common.search.short_label')}</span>
						</button>
					</div>
				</nav>

				{#if isMobileNavOpen}
					<div class="fixed inset-0 z-40 lg:hidden">
						<button type="button" class="absolute inset-0 bg-black/45 backdrop-blur-sm" onclick={() => (isMobileNavOpen = false)} aria-label={$t('common.actions.close')}></button>
						<aside class="absolute inset-y-0 right-0 flex w-[min(24rem,100vw)] flex-col border-l border-border bg-card/98 shadow-2xl" style="padding-top: max(1rem, env(safe-area-inset-top)); padding-bottom: max(1rem, env(safe-area-inset-bottom));">
							<div class="flex items-start justify-between gap-4 px-5 pb-4">
								<div>
									<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-primary">
										{$t('common.app_shell.mobile_menu')}
									</p>
									<h2 class="mt-2 font-display text-3xl text-foreground">Shelf</h2>
									<p class="mt-2 text-sm leading-6 text-muted-foreground">
										{$t('common.app_shell.sidebar_description')}
									</p>
								</div>
								<button type="button" class="rounded-full p-2 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground" onclick={() => (isMobileNavOpen = false)} aria-label={$t('common.actions.close')}>
									<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
										<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18 18 6M6 6l12 12" />
									</svg>
								</button>
							</div>

							<div class="flex-1 space-y-6 overflow-y-auto px-5">
								<PwaInstallPrompt compact={true} />

								<section class="space-y-2">
									<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">
										{$t('common.app_shell.catalog')}
									</p>
									<div class="space-y-1">
										{#each catalogNavItems as item (item.href)}
											<a
												href={item.href}
												class="flex items-center gap-3 rounded-2xl border px-4 py-3 transition-colors {isActive(item.href) ? 'border-primary bg-primary text-primary-foreground' : 'border-border/70 bg-background/70 text-foreground hover:bg-accent'}"
											>
												<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
													<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d={iconPath(item.icon)} />
												</svg>
												<span class="font-semibold">{$t(item.label)}</span>
											</a>
										{/each}
									</div>
								</section>

								<section class="space-y-2">
									<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">
										{$t('common.app_shell.tools')}
									</p>
									<div class="space-y-1">
										{#each toolNavItems as item (item.href)}
											<a
												href={item.href}
												class="flex items-center gap-3 rounded-2xl border px-4 py-3 transition-colors {isActive(item.href) ? 'border-primary bg-primary text-primary-foreground' : 'border-border/70 bg-background/70 text-foreground hover:bg-accent'}"
											>
												<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
													<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d={iconPath(item.icon)} />
												</svg>
												<span class="font-semibold">{$t(item.label)}</span>
											</a>
										{/each}
									</div>
								</section>
							</div>

							<div class="space-y-3 border-t border-border/70 px-5 pt-4">
								{#if auth.currentUser}
									<div class="rounded-2xl border border-border/70 bg-background/70 px-4 py-3">
										<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">
											{$t('common.app_shell.logged_in_as')}
										</p>
										<p class="mt-1 text-sm font-semibold text-primary">{auth.currentUser.username}</p>
									</div>

									{#if auth.currentUser.role === UserRole.ADMIN}
										<a href={resolve('/admin/users')} class="flex items-center justify-center rounded-2xl border border-border bg-background/70 px-4 py-3 text-sm font-semibold text-foreground transition-colors hover:bg-accent">
											{$t('common.app_shell.admin_dashboard')}
										</a>
									{/if}

									<button onclick={handleLogout} class="flex w-full items-center justify-center rounded-2xl bg-destructive px-4 py-3 text-sm font-semibold text-destructive-foreground transition-colors hover:bg-destructive/90">
										{$t('common.app_shell.logout')}
									</button>
								{:else}
									<a href={resolve('/login')} class="flex items-center justify-center rounded-2xl bg-primary px-4 py-3 text-sm font-semibold text-primary-foreground">
										{$t('common.app_shell.login')}
									</a>
								{/if}
							</div>
						</aside>
					</div>
				{/if}
			</div>
		{/if}
	</div>
{/if}
