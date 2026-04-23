<script lang="ts">
	import { t } from '$lib/i18n';
	import { api } from '$lib/api/client';
	import type { SavedPodcastRoot } from '$lib/types/models';
	import FormField from '$lib/components/ui/FormField.svelte';
	import StatusBanner from '$lib/components/ui/StatusBanner.svelte';

	interface Props {
		podcast: SavedPodcastRoot;
		onUpdate: (updated: SavedPodcastRoot) => void;
	}

	let { podcast, onUpdate }: Props = $props();

	let isSaving = $state(false);
	let error = $state<string | null>(null);
	let successMessage = $state<string | null>(null);

	// Local form state
	let autoFetch = $state(false);
	let autoSanitize = $state(false);
	let fetchIntervalMinutes = $state(60);

	$effect(() => {
		autoFetch = podcast.autoFetch;
		autoSanitize = podcast.autoSanitize;
		fetchIntervalMinutes = podcast.fetchIntervalMinutes;
	});

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
			onUpdate(result.right);
			successMessage = $t('podcasts.settings.settings_saved');
			setTimeout(() => (successMessage = null), 3000);
		} else if (result.left) {
			error = result.left.message;
		}
	}
</script>

<section class="rounded-2xl border border-border bg-card p-6 shadow-sm">
	<h2 class="mb-6 text-xl font-bold text-foreground">{$t('podcasts.settings.config_section')}</h2>

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
