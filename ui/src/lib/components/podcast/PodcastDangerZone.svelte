<script lang="ts">
	import { resolve } from '$app/paths';
	import { t } from '$lib/i18n';
	import { api } from '$lib/api/client';
	import { goto } from '$app/navigation';
	import ConfirmDialog from '$lib/components/ui/ConfirmDialog.svelte';
	import StatusBanner from '$lib/components/ui/StatusBanner.svelte';

	interface Props {
		podcastId: string;
	}

	let { podcastId }: Props = $props();

	let showUnsubscribeConfirm = $state(false);
	let isUnsubscribing = $state(false);
	let error = $state<string | null>(null);

	async function handleUnsubscribe() {
		isUnsubscribing = true;
		const result = await api.delete(`/podcasts/${podcastId}`);
		if (result.right !== undefined) {
			goto(resolve('/podcasts'));
		} else if (result.left) {
			isUnsubscribing = false;
			error = result.left.message;
			showUnsubscribeConfirm = false;
		}
	}
</script>

<section class="rounded-2xl border border-destructive/20 bg-destructive/5 p-6 shadow-sm">
	<h2 class="mb-2 text-xl font-bold text-destructive">{$t('podcasts.settings.danger_zone')}</h2>

	{#if error}
		<div class="mb-4">
			<StatusBanner kind="error" title="Error" message={error} />
		</div>
	{/if}

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
