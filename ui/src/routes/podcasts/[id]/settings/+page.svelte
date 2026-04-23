<script lang="ts">
	import { resolve } from '$app/paths';
	import { page } from '$app/state';
	import { t } from '$lib/i18n';
	import { api } from '$lib/api/client';
	import { goto } from '$app/navigation';
	import FormField from '$lib/components/ui/FormField.svelte';
	import StatusBanner from '$lib/components/ui/StatusBanner.svelte';
	import ConfirmDialog from '$lib/components/ui/ConfirmDialog.svelte';
	import type { PageData } from './$types';
	import type { SavedPodcastRoot, SavedPodcastAggregate } from '$lib/types/models';

	let { data }: { data: PageData } = $props();
	let aggregate = $state<SavedPodcastAggregate>(data.aggregate!);
	let podcast = $derived(aggregate.podcast);

	let isSaving = $state(false);
	let isRotating = $state(false);
	let isRevoking = $state(false);
	let error = $state<string | null>(null);
	let successMessage = $state<string | null>(null);

	let showUnsubscribeConfirm = $state(false);
	let isUnsubscribing = $state(false);

	// Local form state
	let autoFetch = $state(data.aggregate!.podcast.autoFetch);
	let autoSanitize = $state(data.aggregate!.podcast.autoSanitize);
	let fetchIntervalMinutes = $state(data.aggregate!.podcast.fetchIntervalMinutes);

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
		if (result.right) {
			aggregate = { ...aggregate, podcast: result.right };
		} else if (result.left) {
			error = result.left.message;
		}
	}

	async function revokeToken() {
		isRevoking = true;
		error = null;
		const result = await api.post<SavedPodcastRoot>(`/podcasts/${podcast.id}/revoke-token`, {});
		isRevoking = false;
		if (result.right) {
			aggregate = { ...aggregate, podcast: result.right };
		} else if (result.left) {
			error = result.left.message;
		}
	}

	async function handleSave() {
		isSaving = true;
		error = null;
		successMessage = null;
		const result = await api.put<SavedPodcastRoot>(`/podcasts/${podcast.id}`, {
			autoFetch,
			autoSanitize,
			fetchIntervalMinutes
		});
		isSaving = false;
		if (result.right) {
			aggregate = { ...aggregate, podcast: result.right };
			successMessage = $t('podcasts.settings.settings_saved');
			setTimeout(() => (successMessage = null), 3000);
		} else if (result.left) {
			error = result.left.message;
		}
	}

	async function handleUnsubscribe() {
		isUnsubscribing = true;
		const result = await api.delete(`/podcasts/${podcast.id}`);
		if (result.right !== undefined) {
			goto(resolve('/podcasts'));
		} else if (result.left) {
			isUnsubscribing = false;
			error = result.left.message;
			showUnsubscribeConfirm = false;
		}
	}
</script>

<svelte:head>
	<title>{$t('podcasts.settings.title')} | {aggregate.seriesTitle}</title>
</svelte:head>

<div class="mx-auto max-w-3xl">
	<div class="mb-8 flex flex-col items-start gap-4 md:flex-row md:items-center md:justify-between">
		<div>
			<a
				href={resolve(`/podcasts/${podcast.id}`)}
				class="group mb-2 flex items-center gap-2 text-sm font-medium text-muted-foreground transition-colors hover:text-primary"
			>
				<span class="transition-transform group-hover:-translate-x-1">←</span>
				{aggregate.seriesTitle}
			</a>
			<h1 class="font-display text-4xl font-bold text-foreground">{$t('podcasts.settings.title')}</h1>
		</div>
	</div>

	<div class="space-y-8">
		{#if error}
			<StatusBanner kind="error" title="Error" message={error} />
		{/if}

		{#if successMessage}
			<StatusBanner kind="success" title="Success" message={successMessage} />
		{/if}

		<!-- RSS Feed Section -->
		<section class="rounded-2xl border border-border bg-card p-6 shadow-sm">
			<h2 class="mb-2 text-xl font-bold text-foreground">{$t('podcasts.settings.rss_feed_section')}</h2>
			<p class="mb-6 text-sm text-muted-foreground">{$t('podcasts.settings.rss_feed_description')}</p>

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
							onclick={rotateToken}
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
							onclick={revokeToken}
							class="mt-2 rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-2 text-xs font-bold text-destructive transition-all hover:bg-destructive/10 disabled:opacity-50"
						>
							{isRevoking ? '...' : $t('podcasts.settings.revoke_token')}
						</button>
					</div>
				</div>
			</div>
		</section>

		<!-- Configuration Section -->
		<section class="rounded-2xl border border-border bg-card p-6 shadow-sm">
			<h2 class="mb-6 text-xl font-bold text-foreground">{$t('podcasts.settings.config_section')}</h2>

			<form onsubmit={(e) => { e.preventDefault(); handleSave(); }} class="space-y-6">
				<div class="space-y-4">
					<label class="flex cursor-pointer items-start gap-3 rounded-xl border border-border p-4 transition-colors hover:bg-accent/30">
						<input type="checkbox" bind:checked={autoFetch} class="mt-1 h-4 w-4 rounded border-border text-primary focus:ring-primary" />
						<div>
							<p class="text-sm font-semibold text-foreground">{$t('podcasts.subscribe.auto_fetch_label')}</p>
							<p class="text-xs text-muted-foreground">{$t('podcasts.subscribe.auto_fetch_hint')}</p>
						</div>
					</label>

					<label class="flex cursor-not-allowed items-start gap-3 rounded-xl border border-border bg-muted/30 p-4 opacity-50">
						<input type="checkbox" bind:checked={autoSanitize} disabled class="mt-1 h-4 w-4 rounded border-border text-primary focus:ring-primary" />
						<div>
							<p class="text-sm font-semibold text-foreground">{$t('podcasts.subscribe.auto_sanitize_label')}</p>
							<p class="text-xs text-muted-foreground">{$t('podcasts.subscribe.auto_sanitize_hint')}</p>
						</div>
					</label>
				</div>

				<FormField label={$t('podcasts.subscribe.fetch_interval_label')} forId="fetchInterval" hint={$t('podcasts.subscribe.fetch_interval_hint')}>
					<input
						id="fetchInterval"
						type="number"
						bind:value={fetchIntervalMinutes}
						min="1"
						max="10080"
						class="w-full rounded-xl border border-border bg-background px-4 py-3 text-foreground focus:border-primary focus:ring-1 focus:ring-primary"
					/>
				</FormField>

				<div class="flex justify-end">
					<button
						type="submit"
						disabled={isSaving}
						class="rounded-xl bg-primary px-8 py-3 font-bold text-primary-foreground shadow-lg shadow-primary/20 transition-all hover:bg-primary/90 disabled:opacity-50"
					>
						{isSaving ? '...' : $t('podcasts.settings.save_settings')}
					</button>
				</div>
			</form>
		</section>

		<!-- Danger Zone -->
		<section class="rounded-2xl border border-destructive/20 bg-destructive/5 p-6 shadow-sm">
			<h2 class="mb-2 text-xl font-bold text-destructive">{$t('podcasts.settings.danger_zone')}</h2>
			<div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
				<p class="text-sm text-muted-foreground">{$t('podcasts.settings.unsubscribe_confirm')}</p>
				<button
					type="button"
					onclick={() => (showUnsubscribeConfirm = true)}
					class="rounded-xl bg-destructive px-6 py-2.5 text-sm font-bold text-destructive-foreground shadow-lg shadow-destructive/20 transition-all hover:bg-destructive/90"
				>
					{$t('podcasts.settings.unsubscribe')}
				</button>
			</div>
		</section>
	</div>
</div>

<ConfirmDialog
	bind:open={showUnsubscribeConfirm}
	title={$t('podcasts.settings.unsubscribe')}
	message={$t('podcasts.settings.unsubscribe_confirm')}
	confirmLabel={$t('podcasts.settings.unsubscribe')}
	variant="destructive"
	processing={isUnsubscribing}
	onConfirm={handleUnsubscribe}
	onCancel={() => (showUnsubscribeConfirm = false)}
/>
