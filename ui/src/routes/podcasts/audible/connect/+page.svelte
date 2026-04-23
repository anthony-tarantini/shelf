<script lang="ts">
	import { resolve } from '$app/paths';
	import { t } from '$lib/i18n';
	import { api } from '$lib/api/client';
	import StatusBanner from '$lib/components/ui/StatusBanner.svelte';

	let isLoading = $state(false);
	let error = $state<string | null>(null);

	async function startConnect() {
		error = null;
		isLoading = true;
		const result = await api.post<{ loginUrl: string }>('/podcasts/audible/connect', {});
		isLoading = false;
		if (result.right) {
			window.location.href = result.right.loginUrl;
		} else if (result.left) {
			error = result.left.message;
		}
	}
</script>

<svelte:head>
	<title>{$t('audible.connect_title')} | Shelf</title>
</svelte:head>

<div class="mx-auto max-w-xl py-12 px-4">
	<div class="text-center mb-12">
		<div class="inline-flex h-20 w-20 items-center justify-center rounded-3xl bg-[#F5D000]/10 text-[#F5D000] mb-6">
			<svg xmlns="http://www.w3.org/2000/svg" class="h-12 w-12" viewBox="0 0 24 24" fill="currentColor">
				<path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-1-13h2v6h-2zm0 8h2v2h-2z"/>
			</svg>
		</div>
		<h1 class="font-display text-4xl font-bold text-foreground">{$t('audible.connect_title')}</h1>
		<p class="mt-2 text-muted-foreground">{$t('audible.connect_subtitle')}</p>
	</div>

	<div class="rounded-3xl border border-border bg-card p-8 shadow-2xl">
		{#if error}
			<div class="mb-6">
				<StatusBanner kind="error" title="Connection Failed" message={error} />
			</div>
		{/if}

		<div class="space-y-6">
			<p class="text-sm leading-relaxed text-muted-foreground text-center px-4">
				{$t('audible.login_instruction')}
			</p>

			<button
				type="button"
				disabled={isLoading}
				onclick={startConnect}
				class="w-full rounded-2xl bg-[#F5D000] py-4 font-display text-lg font-bold text-black shadow-lg shadow-[#F5D000]/20 transition-all hover:bg-[#F5D000]/90 hover:scale-[1.02] active:scale-[0.98] disabled:opacity-50"
			>
				{isLoading ? '...' : $t('audible.start_login')}
			</button>

			<a
				href={resolve('/podcasts')}
				class="block w-full py-2 text-center text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
			>
				Cancel and go back
			</a>
		</div>
	</div>
</div>
