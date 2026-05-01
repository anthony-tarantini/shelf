<script lang="ts">
	import { page } from '$app/state';
	import { t } from '$lib/i18n';
	import { api } from '$lib/api/client';
	import type { SavedPodcastRoot } from '$lib/types/models';
	import StatusBanner from '$lib/components/ui/StatusBanner.svelte';
	import ConfirmDialog from '$lib/components/ui/ConfirmDialog.svelte';

	interface Props {
		podcast: SavedPodcastRoot;
		onUpdate: (updated: SavedPodcastRoot) => void;
	}

	let { podcast, onUpdate }: Props = $props();

	let isRotating = $state(false);
	let isRevoking = $state(false);
	let showRotateConfirm = $state(false);
	let showRevokeConfirm = $state(false);
	let error = $state<string | null>(null);
	let successMessage = $state<string | null>(null);

	const rssUrl = $derived.by(() => {
		const base = page.url.origin;
		return `${base}/api/rss/podcasts/${podcast.feedToken}`;
	});

	async function copyRssUrl() {
		await navigator.clipboard.writeText(rssUrl);
		successMessage = $t('podcasts.settings.rss_url_copied');
		setTimeout(() => (successMessage = null), 3000);
	}

	async function rotateToken() {
		isRotating = true;
		error = null;
		const result = await api.post<SavedPodcastRoot>(`/podcasts/${podcast.id}/rotate-token`, {});
		isRotating = false;
		showRotateConfirm = false;
		if (result.right) {
			onUpdate(result.right);
		} else if (result.left) {
			error = result.left.message;
		}
	}

	async function revokeToken() {
		isRevoking = true;
		error = null;
		const result = await api.post<SavedPodcastRoot>(`/podcasts/${podcast.id}/revoke-token`, {});
		isRevoking = false;
		showRevokeConfirm = false;
		if (result.right) {
			onUpdate(result.right);
		} else if (result.left) {
			error = result.left.message;
		}
	}
</script>

<section class="rounded-2xl border border-border bg-card p-6 shadow-sm">
	<h2 class="mb-2 text-xl font-bold text-foreground">{$t('podcasts.settings.rss_feed_section')}</h2>
	<p class="mb-6 text-sm text-muted-foreground">{$t('podcasts.settings.rss_feed_description')}</p>

	{#if error}
		<div class="mb-4">
			<StatusBanner kind="error" title="Error" message={error} />
		</div>
	{/if}

	{#if successMessage}
		<div class="mb-4">
			<StatusBanner kind="success" title="Success" message={successMessage} />
		</div>
	{/if}

	<div class="flex flex-col gap-4">
		<div class="flex flex-col gap-2 rounded-xl border border-primary/20 bg-primary/5 p-4 md:flex-row md:items-center">
			<code class="flex-1 break-all text-xs font-mono font-medium text-primary">
				{rssUrl}
			</code>
			<button
				type="button"
				onclick={copyRssUrl}
				class="shrink-0 rounded-lg bg-primary px-4 py-2 text-xs font-bold text-primary-foreground transition-all hover:bg-primary/90"
			>
				{$t('podcasts.settings.copy_rss_url')}
			</button>
		</div>

		<div class="grid grid-cols-1 gap-4 md:grid-cols-2">
			<div class="flex flex-col gap-2 rounded-xl border border-border p-4">
				<h3 class="text-sm font-bold text-foreground">{$t('podcasts.settings.rotate_token')}</h3>
				<p class="text-xs text-muted-foreground">{$t('podcasts.settings.rotate_token_hint')}</p>
				<button
					type="button"
					disabled={isRotating}
					onclick={() => (showRotateConfirm = true)}
					class="mt-2 rounded-lg border border-border bg-background px-4 py-2 text-xs font-bold text-foreground transition-all hover:bg-accent disabled:opacity-50"
				>
					{isRotating ? '...' : $t('podcasts.settings.rotate_token')}
				</button>
			</div>

			<div class="flex flex-col gap-2 rounded-xl border border-border p-4">
				<h3 class="text-sm font-bold text-foreground">{$t('podcasts.settings.revoke_token')}</h3>
				<p class="text-xs text-muted-foreground">{$t('podcasts.settings.revoke_token_hint')}</p>
				<button
					type="button"
					disabled={isRevoking}
					onclick={() => (showRevokeConfirm = true)}
					class="mt-2 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-2 text-xs font-bold text-destructive transition-all hover:bg-destructive/10 disabled:opacity-50"
				>
					{isRevoking ? '...' : $t('podcasts.settings.revoke_token')}
				</button>
			</div>
		</div>
	</div>
</section>

<ConfirmDialog
	bind:open={showRotateConfirm}
	title={$t('podcasts.settings.rotate_token_confirm_title')}
	message={$t('podcasts.settings.rotate_token_confirm_message')}
	confirmLabel={$t('podcasts.settings.rotate_token')}
	processing={isRotating}
	onConfirm={rotateToken}
	onCancel={() => (showRotateConfirm = false)}
/>

<ConfirmDialog
	bind:open={showRevokeConfirm}
	title={$t('podcasts.settings.revoke_token_confirm_title')}
	message={$t('podcasts.settings.revoke_token_confirm_message')}
	confirmLabel={$t('podcasts.settings.revoke_token')}
	variant="destructive"
	processing={isRevoking}
	onConfirm={revokeToken}
	onCancel={() => (showRevokeConfirm = false)}
/>
