<script lang="ts">
	import { t } from '$lib/i18n';
	import { api } from '$lib/api/client';
	import FormField from '$lib/components/ui/FormField.svelte';
	import StatusBanner from '$lib/components/ui/StatusBanner.svelte';

	interface Props {
		open: boolean;
		asin: string;
		title: string;
		onClose: () => void;
		onImport: (seriesId: string, autoFetch: boolean, autoSanitize: boolean) => Promise<void>;
	}

	let { open, asin, title, onClose, onImport }: Props = $props();

	let isLoading = $state(false);
	let error = $state<string | null>(null);
	let seriesSearch = $state('');
	let foundSeries = $state<Array<{ id: string, name: string }>>([]);
	let selectedSeriesId = $state<string | null>(null);
	let selectedSeriesName = $state<string | null>(null);
	
	let autoFetch = $state(true);
	let autoSanitize = $state(true);
	let fetchIntervalMinutes = $state(1440); // Once a day default for Audible

	$effect(() => {
		if (seriesSearch.length > 1) {
			searchSeries(seriesSearch);
		} else {
			foundSeries = [];
		}
	});

	async function searchSeries(query: string) {
		const result = await api.get<Array<{ id: string, name: string }>>(`/series/search?q=${encodeURIComponent(query)}`);
		if (result.right) {
			foundSeries = result.right;
		}
	}

	function selectSeries(id: string, name: string) {
		selectedSeriesId = id;
		selectedSeriesName = name;
		seriesSearch = '';
		foundSeries = [];
	}

	async function handleSubmit() {
		if (!selectedSeriesId) {
			// Create new series first
			isLoading = true;
			const newSeries = await api.post<{ id: string }>('/series', { title: seriesSearch || title });
			if (newSeries.right) {
				selectedSeriesId = newSeries.right.id;
			} else if (newSeries.left) {
				error = newSeries.left.message;
				isLoading = false;
				return;
			}
		}

		if (selectedSeriesId) {
			isLoading = true;
			await onImport(selectedSeriesId, autoFetch, autoSanitize);
			isLoading = false;
			onClose();
		}
	}
</script>

{#if open}
	<div class="fixed inset-0 z-50 flex items-center justify-center bg-background/80 p-4 backdrop-blur-sm">
		<div class="w-full max-w-lg overflow-hidden rounded-[2rem] border border-border bg-card shadow-2xl">
			<div class="border-b border-border bg-muted/20 px-8 py-6">
				<h2 class="text-2xl font-bold text-foreground">
					{$t('audible.import_modal_title', { title })}
				</h2>
				<p class="mt-1 text-sm text-muted-foreground">
					{$t('audible.import_modal_description')}
				</p>
			</div>

			<div class="p-8">
				{#if error}
					<div class="mb-6">
						<StatusBanner kind="error" title="Error" message={error} />
					</div>
				{/if}

				<form onsubmit={(e) => { e.preventDefault(); handleSubmit(); }} class="space-y-6">
					<FormField label={$t('podcasts.subscribe.series_label')} forId="seriesSearch" hint="Select an existing series or type to create a new one">
						{#if selectedSeriesId}
							<div class="flex items-center gap-2 rounded-xl border border-primary/30 bg-primary/5 px-4 py-3">
								<span class="flex-1 text-sm font-semibold text-foreground">{selectedSeriesName}</span>
								<button
									type="button"
									onclick={() => { selectedSeriesId = null; selectedSeriesName = null; }}
									aria-label={$t('common.actions.clear_selection')}
									class="rounded-full p-1 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
								>
									<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
										<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
									</svg>
								</button>
							</div>
						{:else}
							<div class="relative">
								<input
									id="seriesSearch"
									type="text"
									bind:value={seriesSearch}
									placeholder="Search series..."
									class="w-full rounded-xl border border-border bg-background px-4 py-3 text-sm focus:border-primary focus:ring-1 focus:ring-primary"
								/>
								{#if foundSeries.length > 0}
									<div class="absolute z-10 mt-1 w-full overflow-hidden rounded-xl border border-border bg-card shadow-xl">
										{#each foundSeries as series}
											<button
												type="button"
												onclick={() => selectSeries(series.id, series.name)}
												class="flex w-full px-4 py-3 text-left text-sm hover:bg-accent"
											>
												{series.name}
											</button>
										{/each}
									</div>
								{/if}
							</div>
						{/if}
					</FormField>

					<div class="grid grid-cols-2 gap-4">
						<label class="flex cursor-pointer items-start gap-3 rounded-xl border border-border p-4 transition-colors hover:bg-accent/30">
							<input type="checkbox" bind:checked={autoFetch} class="mt-1 h-4 w-4 rounded border-border text-primary" />
							<span class="text-xs font-semibold">Auto-fetch</span>
						</label>
						<label class="flex cursor-pointer items-start gap-3 rounded-xl border border-border p-4 transition-colors hover:bg-accent/30">
							<input type="checkbox" bind:checked={autoSanitize} class="mt-1 h-4 w-4 rounded border-border text-primary" />
							<span class="text-xs font-semibold">Auto-sanitize</span>
						</label>
					</div>

					<div class="flex justify-end gap-3 pt-4">
						<button
							type="button"
							onclick={onClose}
							class="rounded-xl border border-border bg-background px-6 py-2.5 text-sm font-bold text-foreground transition-all hover:bg-accent"
						>
							Cancel
						</button>
						<button
							type="submit"
							disabled={isLoading}
							class="rounded-xl bg-primary px-8 py-2.5 text-sm font-bold text-primary-foreground shadow-lg shadow-primary/20 transition-all hover:bg-primary/90 disabled:opacity-50"
						>
							{isLoading ? '...' : $t('audible.import_action')}
						</button>
					</div>
				</form>
			</div>
		</div>
	</div>
{/if}
