<script lang="ts">
	import { t } from '$lib/i18n';
	import { goto } from '$app/navigation';
	import { resolve } from '$app/paths';
	import { api } from '$lib/api/client';
	import StatusBanner from '$lib/components/ui/StatusBanner.svelte';
	import FormField from '$lib/components/ui/FormField.svelte';
	import type { SeriesRoot } from '$lib/types/models';

	let { data } = $props();

	let allSeries = $derived<SeriesRoot[]>(data.series);

	// Form state
	let feedUrl = $state('');
	let selectedSeriesId = $state<string | null>(null);
	let newSeriesTitle = $state('');
	let autoFetch = $state(true);
	let autoSanitize = $state(false);
	let fetchIntervalMinutes = $state(60);

	let submitting = $state(false);
	let error = $state<string | null>(null);
	let success = $state(false);

	// Series search/filter
	let seriesSearch = $state('');
	let showSeriesDropdown = $state(false);

	let filteredSeries = $derived.by(() => {
		if (!seriesSearch) return allSeries;
		const q = seriesSearch.toLowerCase();
		return allSeries.filter((s) => s.name.toLowerCase().includes(q));
	});

	let selectedSeriesName = $derived.by(() => {
		if (!selectedSeriesId) return '';
		return allSeries.find((s) => s.id === selectedSeriesId)?.name ?? '';
	});

	function selectSeries(s: SeriesRoot) {
		selectedSeriesId = s.id;
		seriesSearch = '';
		showSeriesDropdown = false;
		newSeriesTitle = '';
	}

	function clearSeriesSelection() {
		selectedSeriesId = null;
		seriesSearch = '';
	}

	async function handleSubmit() {
		error = null;

		if (!feedUrl.trim()) {
			error = $t('podcasts.subscribe.feed_url_required');
			return;
		}

		submitting = true;
		let seriesId = selectedSeriesId;

		// Create new series if needed
		if (!seriesId && newSeriesTitle.trim()) {
			const seriesResult = await api.post<SeriesRoot>('/series', { title: newSeriesTitle.trim() });
			if (seriesResult.left) {
				error = seriesResult.left.message || $t('podcasts.subscribe.create_series_failed');
				submitting = false;
				return;
			}
			seriesId = seriesResult.right!.id;
		}

		if (!seriesId) {
			error = $t('podcasts.subscribe.series_required');
			submitting = false;
			return;
		}

		const result = await api.post<unknown>('/podcasts', {
			seriesId,
			feedUrl: feedUrl.trim(),
			autoFetch,
			autoSanitize,
			fetchIntervalMinutes,
		});

		submitting = false;

		if (result.left) {
			error = result.left.message;
		} else {
			success = true;
		}
	}
</script>

<svelte:head>
	<title>{$t('podcasts.subscribe.title')} | Shelf</title>
</svelte:head>

<div class="mx-auto max-w-3xl">
	<header class="mb-8 rounded-[1.75rem] border border-border/70 bg-card/70 p-6 shadow-xl shadow-black/5 backdrop-blur-md">
		<div class="flex items-start justify-between gap-4">
			<div>
				<p class="mb-2 text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{$t('podcasts.subscribe.eyebrow')}</p>
				<h2 class="font-display text-4xl font-bold text-primary">{$t('podcasts.subscribe.title')}</h2>
				<p class="mt-2 text-muted-foreground">{$t('podcasts.subscribe.subtitle')}</p>
			</div>
			<a
				href={resolve('/podcasts')}
				class="shrink-0 rounded-full border border-border/70 bg-background/80 px-4 py-2 text-sm font-medium text-foreground transition-all hover:border-primary/40 hover:bg-card"
			>
				{$t('podcasts.subscribe.back')}
			</a>
		</div>
	</header>

	{#if success}
		<div class="rounded-[1.5rem] border border-primary/20 bg-primary/10 p-8 text-center shadow-xl">
			<div class="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-primary/20">
				<svg xmlns="http://www.w3.org/2000/svg" class="h-7 w-7 text-primary" fill="none" viewBox="0 0 24 24" stroke="currentColor">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
				</svg>
			</div>
			<p class="text-[11px] font-bold uppercase tracking-[0.28em] text-primary">{$t('podcasts.subscribe.success_title')}</p>
			<p class="mt-2 text-sm text-foreground">{$t('podcasts.subscribe.success_message')}</p>
			<a
				href={resolve('/podcasts')}
				class="mt-6 inline-flex rounded-xl bg-primary px-6 py-3 font-bold text-primary-foreground transition-colors hover:bg-primary/90"
			>
				{$t('podcasts.subscribe.back')}
			</a>
		</div>
	{:else}
		<form
			onsubmit={(e) => { e.preventDefault(); handleSubmit(); }}
			class="space-y-8 rounded-[1.5rem] border border-border bg-card p-6 shadow-xl shadow-primary/5"
		>
			<!-- Feed URL -->
			<FormField label={$t('podcasts.subscribe.feed_url_label')} forId="feedUrl" hint={$t('podcasts.subscribe.feed_url_hint')}>
				<input
					id="feedUrl"
					type="url"
					bind:value={feedUrl}
					placeholder={$t('podcasts.subscribe.feed_url_placeholder')}
					class="ui-input"
					required
				/>
			</FormField>

			<!-- Series picker -->
			<div class="space-y-4">
				<FormField label={$t('podcasts.subscribe.series_label')} forId="seriesSelect" hint={$t('podcasts.subscribe.series_hint')}>
					{#if selectedSeriesId}
						<div class="flex items-center gap-2 rounded-xl border border-primary/30 bg-primary/5 px-4 py-3">
							<span class="flex-1 text-sm font-semibold text-foreground">{selectedSeriesName}</span>
							<button
							        type="button"
							        onclick={clearSeriesSelection}
							        aria-label={$t('common.actions.clear_selection')}
							        class="rounded-full p-1 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
							>								<svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
									<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
								</svg>
							</button>
						</div>
					{:else}
						<div class="relative">
							<input
								id="seriesSelect"
								type="text"
								bind:value={seriesSearch}
								onfocus={() => (showSeriesDropdown = true)}
								onblur={() => setTimeout(() => (showSeriesDropdown = false), 200)}
								placeholder={$t('podcasts.subscribe.series_search_placeholder')}
								class="ui-input"
							/>
							{#if showSeriesDropdown && filteredSeries.length > 0}
								<div class="absolute z-10 mt-1 max-h-48 w-full overflow-y-auto rounded-xl border border-border bg-card shadow-lg">
									{#each filteredSeries as s (s.id)}
										<button
											type="button"
											onmousedown={() => selectSeries(s)}
											class="w-full px-4 py-2.5 text-left text-sm text-foreground transition-colors hover:bg-accent first:rounded-t-xl last:rounded-b-xl"
										>
											{s.name}
										</button>
									{/each}
								</div>
							{/if}
						</div>
					{/if}
				</FormField>

				{#if !selectedSeriesId}
					<FormField label={$t('podcasts.subscribe.series_new_label')} forId="newSeries">
						<input
							id="newSeries"
							type="text"
							bind:value={newSeriesTitle}
							placeholder={$t('podcasts.subscribe.series_new_placeholder')}
							class="ui-input"
						/>
					</FormField>
				{/if}
			</div>

			<!-- Options -->
			<div class="grid gap-6 sm:grid-cols-2">
				<div class="flex items-center gap-3">
					<input
						id="autoFetch"
						type="checkbox"
						bind:checked={autoFetch}
						class="h-5 w-5 rounded border-border text-primary accent-primary"
					/>
					<div>
						<label for="autoFetch" class="text-sm font-semibold text-foreground">{$t('podcasts.subscribe.auto_fetch_label')}</label>
						<p class="text-xs text-muted-foreground">{$t('podcasts.subscribe.auto_fetch_hint')}</p>
					</div>
				</div>

				<div class="flex items-center gap-3">
					<input
						id="autoSanitize"
						type="checkbox"
						bind:checked={autoSanitize}
						class="h-5 w-5 rounded border-border text-primary accent-primary"
						disabled
					/>
					<div>
						<label for="autoSanitize" class="text-sm font-semibold text-muted-foreground">{$t('podcasts.subscribe.auto_sanitize_label')}</label>
						<p class="text-xs text-muted-foreground">{$t('podcasts.subscribe.auto_sanitize_hint')}</p>
					</div>
				</div>
			</div>

			{#if autoFetch}
				<FormField label={$t('podcasts.subscribe.fetch_interval_label')} forId="fetchInterval" hint={$t('podcasts.subscribe.fetch_interval_hint')}>
					<input
						id="fetchInterval"
						type="number"
						bind:value={fetchIntervalMinutes}
						min="1"
						max="10080"
						class="ui-input max-w-[12rem]"
					/>
				</FormField>
			{/if}

			{#if error}
				<StatusBanner kind="error" title={$t('podcasts.subscribe.failed_title')} message={error} />
			{/if}

			<button
				type="submit"
				disabled={submitting}
				class="flex w-full items-center justify-center rounded-xl bg-primary px-4 py-3 font-bold text-primary-foreground transition-colors hover:bg-primary/90 disabled:bg-muted"
			>
				{#if submitting}
					<span class="mr-2 h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white"></span>
					{$t('podcasts.subscribe.loading')}
				{:else}
					{$t('podcasts.subscribe.action')}
				{/if}
			</button>
		</form>
	{/if}
</div>
