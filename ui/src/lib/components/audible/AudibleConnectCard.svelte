<script lang="ts">
	import { t } from '$lib/i18n';
	import { resolve } from '$app/paths';
	import { api } from '$lib/api/client';
	import StatusBanner from '$lib/components/ui/StatusBanner.svelte';

	interface Props {
		isConnected: boolean;
		username?: string;
		onDisconnect?: () => void;
	}

	let { isConnected, username, onDisconnect }: Props = $props();
	let isLoading = $state(false);
	let error = $state<string | null>(null);

	async function handleConnect() {
		isLoading = true;
		error = null;
		const result = await api.post<{ loginUrl: string; sessionId: string }>('/podcasts/audible/connect', {});
		isLoading = false;
		
		if (result.right) {
			// Store sessionId in sessionStorage for finalization
			sessionStorage.setItem('audible_session_id', result.right.sessionId);
			// Redirect to Amazon login
			window.location.href = result.right.loginUrl;
		} else if (result.left) {
			error = result.left.message;
		}
	}
</script>

<div class="rounded-2xl border border-border bg-card p-6 shadow-sm">
	<div class="flex items-center gap-4 mb-6">
		<div class="flex h-12 w-12 items-center justify-center rounded-xl bg-[#F5D000]/10 text-[#F5D000]">
			<svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8" viewBox="0 0 24 24" fill="currentColor">
				<path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-1-13h2v6h-2zm0 8h2v2h-2z"/>
			</svg>
		</div>
		<div>
			<h2 class="text-xl font-bold text-foreground">{$t('podcasts.settings.audible_section')}</h2>
			<p class="text-sm text-muted-foreground">{$t('podcasts.settings.audible_description')}</p>
		</div>
	</div>

	{#if error}
		<div class="mb-4">
			<StatusBanner kind="error" title="Error" message={error} />
		</div>
	{/if}

	<div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between rounded-xl border border-border bg-accent/5 p-4">
		<div class="flex items-center gap-3">
			<div class={`h-2.5 w-2.5 rounded-full ${isConnected ? 'bg-success' : 'bg-muted-foreground/30'}`}></div>
			<span class="text-sm font-semibold text-foreground">
				{isConnected ? $t('podcasts.settings.audible_connected', { username }) : $t('podcasts.settings.audible_not_connected')}
			</span>
		</div>

		<div class="flex gap-2">
			{#if isConnected}
				<a
					href={resolve('/podcasts/audible/browse')}
					class="rounded-lg bg-primary px-4 py-2 text-xs font-bold text-primary-foreground transition-all hover:bg-primary/90"
				>
					{$t('podcasts.settings.audible_browse')}
				</a>
				<button
					type="button"
					onclick={onDisconnect}
					class="rounded-lg border border-border bg-background px-4 py-2 text-xs font-bold text-foreground transition-all hover:bg-accent"
				>
					{$t('podcasts.settings.audible_disconnect')}
				</button>
			{:else}
				<button
					type="button"
					disabled={isLoading}
					onclick={handleConnect}
					class="rounded-lg bg-[#F5D000] px-6 py-2 text-xs font-bold text-black transition-all hover:bg-[#F5D000]/90 disabled:opacity-50"
				>
					{isLoading ? '...' : $t('podcasts.settings.audible_connect')}
				</button>
			{/if}
		</div>
	</div>
</div>
