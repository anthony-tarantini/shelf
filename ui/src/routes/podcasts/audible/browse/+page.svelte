<script lang="ts">
	import { resolve } from '$app/paths';
	import { t } from '$lib/i18n';
	import { api } from '$lib/api/client';
	import AudibleLibraryItem from '$lib/components/audible/AudibleLibraryItem.svelte';
	import AudibleImportModal from '$lib/components/audible/AudibleImportModal.svelte';
	import StatusBanner from '$lib/components/ui/StatusBanner.svelte';
	import type { PageData } from './$types';

	let { data }: { data: PageData } = $props();
	
	let filter = $state('ALL');
	let importTarget = $state<{ asin: string, title: string } | null>(null);
	let successMessage = $state<string | null>(null);
	let globalError = $state<string | null>(data.loadError || null);

	let filteredItems = $derived.by(() => {
		const items = data.items || [];
		if (filter === 'ALL') return items;
		return items.filter((item: any) => item.type === filter);
	});

	async function handleImport(seriesId: string, autoFetch: boolean, autoSanitize: boolean) {
		if (!importTarget) return;
		
		const result = await api.post<any>('/podcasts/audible/import', {
			asin: importTarget.asin,
			seriesId,
			autoFetch,
			autoSanitize
		});
		
		if (result.right) {
			successMessage = $t('audible.import_success');
			setTimeout(() => successMessage = null, 3000);
		} else if (result.left) {
			globalError = result.left.message;
		}
	}
</script>

<svelte:head>
	<title>{$t('audible.browse_title')} | Shelf</title>
</svelte:head>

<div class="flex flex-col gap-8">
	<!-- Header -->
	<div class="flex flex-col gap-2">
		<a
			href={resolve('/podcasts')}
			class="group flex items-center gap-2 text-sm font-medium text-muted-foreground transition-colors hover:text-primary"
		>
			<span class="transition-transform group-hover:-translate-x-1">←</span>
			{$t('podcasts.detail.back')}
		</a>
		<h1 class="font-display text-4xl font-bold text-foreground">{$t('audible.browse_title')}</h1>
		<p class="text-muted-foreground">{$t('audible.browse_subtitle')}</p>
	</div>

	{#if globalError}
		<StatusBanner kind="error" title="Error" message={globalError} />
	{/if}

	{#if successMessage}
		<StatusBanner kind="success" title="Success" message={successMessage} />
	{/if}

	<!-- Filter Bar -->
	<div class="flex items-center gap-2 border-b border-border pb-4">
		<button
			onclick={() => filter = 'ALL'}
			class={`rounded-lg px-4 py-2 text-xs font-bold transition-all ${filter === 'ALL' ? 'bg-primary text-primary-foreground' : 'bg-accent/5 text-muted-foreground hover:bg-accent'}`}
		>
			{$t('audible.filter_all')}
		</button>
		<button
			onclick={() => filter = 'PODCAST'}
			class={`rounded-lg px-4 py-2 text-xs font-bold transition-all ${filter === 'PODCAST' ? 'bg-primary text-primary-foreground' : 'bg-accent/5 text-muted-foreground hover:bg-accent'}`}
		>
			{$t('audible.filter_podcasts')}
		</button>
		<button
			onclick={() => filter = 'AUDIOBOOK'}
			class={`rounded-lg px-4 py-2 text-xs font-bold transition-all ${filter === 'AUDIOBOOK' ? 'bg-primary text-primary-foreground' : 'bg-accent/5 text-muted-foreground hover:bg-accent'}`}
		>
			{$t('audible.filter_audiobooks')}
		</button>
	</div>

	<!-- Library Grid -->
	{#if filteredItems.length > 0}
		<div class="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
			{#each filteredItems as item (item.asin)}
				<AudibleLibraryItem 
					{item} 
					onImport={(asin, title) => importTarget = { asin, title }} 
				/>
			{/each}
		</div>
	{:else}
		<div class="flex flex-col items-center justify-center rounded-3xl border border-dashed border-border bg-card/30 py-32 text-center">
			<div class="mb-6 rounded-3xl bg-accent/30 p-6 text-muted-foreground">
				<svg xmlns="http://www.w3.org/2000/svg" class="h-16 w-16 opacity-20" fill="none" viewBox="0 0 24 24" stroke="currentColor">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
				</svg>
			</div>
			<p class="text-xl font-medium text-muted-foreground">{$t('audible.empty_library')}</p>
		</div>
	{/if}
</div>

<AudibleImportModal
	open={importTarget !== null}
	asin={importTarget?.asin || ''}
	title={importTarget?.title || ''}
	onClose={() => importTarget = null}
	onImport={handleImport}
/>
