<script lang="ts">
	import Button from '$lib/components/ui/Button/Button.svelte';
	import { t } from '$lib/i18n';
	import { installState } from '$lib/state/install.svelte';

	let { compact = false } = $props<{ compact?: boolean }>();

	const isIos = $derived(installState.platform === 'ios-safari');
	const isChromium = $derived(installState.platform === 'chromium');
	const shouldShow = $derived(installState.shouldShowInstallUi);

	async function handleInstall() {
		await installState.promptInstall();
	}
</script>

{#if shouldShow}
	<div class="rounded-[1.5rem] border border-border/70 bg-card/85 p-4 shadow-lg shadow-black/5 backdrop-blur-md">
		<div class="flex items-start justify-between gap-3">
			<div class="space-y-2">
				<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-primary">
					{$t('common.install.eyebrow')}
				</p>
				<h2 class="font-display text-xl text-foreground">
					{$t('common.install.title')}
				</h2>
				<p class="text-sm leading-6 text-muted-foreground">
					{#if isIos}
						{$t('common.install.ios_message')}
					{:else if isChromium}
						{$t('common.install.chromium_message')}
					{/if}
				</p>
				{#if isIos}
					<p class="text-xs font-semibold text-muted-foreground">
						{$t('common.install.ios_steps')}
					</p>
				{/if}
			</div>
			<button
				type="button"
				class="rounded-full p-2 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
				onclick={() => installState.dismiss()}
				aria-label={$t('common.actions.dismiss')}
			>
				<svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18 18 6M6 6l12 12" />
				</svg>
			</button>
		</div>

		{#if isChromium}
			<div class="mt-4 flex flex-wrap gap-3">
				<Button onclick={handleInstall} size="sm">
					{$t('common.install.install_now')}
				</Button>
				<Button onclick={() => installState.dismiss()} variant="ghost" size="sm">
					{$t('common.install.maybe_later')}
				</Button>
			</div>
		{:else if !compact}
			<div class="mt-4 text-xs text-muted-foreground">
				{$t('common.install.ios_footer')}
			</div>
		{/if}
	</div>
{/if}
