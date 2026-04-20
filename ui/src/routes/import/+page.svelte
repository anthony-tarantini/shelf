<script lang="ts">
	import { api } from '$lib/api/client';
	import { resolve } from '$app/paths';
	import StatusBanner from '$lib/components/ui/StatusBanner.svelte';
	import FormField from '$lib/components/ui/FormField.svelte';
	import { t } from '$lib/i18n';
	import { progress } from '$lib/state/progress.svelte';

	let fileInput: HTMLInputElement | null = $state(null);
	let uploading = $state(false);
	let error = $state<string | null>(null);
	let success = $state(false);

	let scanPath = $state('');
	let scanning = $state(false);
	let scanError = $state<string | null>(null);
	let scanSuccess = $state(false);

	async function handleUpload() {
		if (!fileInput?.files?.[0]) {
			error = $t('import.ingest.direct_upload.error_missing_file');
			return;
		}

		uploading = true;
		error = null;
		success = false;

		const formData = new FormData();
		formData.append('file', fileInput.files[0]);

		const result = await api.upload<unknown>('/books/import', formData);

		uploading = false;
		if (result.left) {
			error = result.left.message;
		} else {
			success = true;
			// Reset the form
			if (fileInput) fileInput.value = '';
		}
	}

	async function handleScan() {
		if (!scanPath) {
			scanError = $t('import.ingest.scan.error_missing_path');
			return;
		}

		scanning = true;
		scanError = null;
		scanSuccess = false;

		const result = await api.post<unknown>('/books/import/scan', {
			path: scanPath
		});

		scanning = false;
		if (result.left) {
			scanError = result.left.message;
		} else {
			progress.ensurePolling();
			scanSuccess = true;
			scanPath = '';
		}
	}
</script>

<div class="mx-auto max-w-6xl">
	<header class="mb-8 rounded-[1.75rem] border border-border/70 bg-card/70 p-6 shadow-xl shadow-black/5 backdrop-blur-md">
		<div class="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
			<div>
				<p class="mb-2 text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{$t('import.ingest.eyebrow')}</p>
				<h2 class="font-display text-4xl font-bold text-primary">{$t('import.ingest.title')}</h2>
				<p class="text-muted-foreground mt-2 max-w-2xl">{$t('import.ingest.subtitle')}</p>
			</div>
		</div>
	</header>

	<div class="grid gap-8 xl:grid-cols-[minmax(0,1.65fr)_minmax(18rem,0.85fr)] xl:items-stretch">
		<div class="h-full">
			<div class="grid h-full grid-cols-1 gap-8 md:grid-cols-2">
				<div class="flex h-full flex-col rounded-[1.5rem] border border-border bg-card p-6 shadow-xl shadow-primary/5">
					<div class="mb-5 flex items-start justify-between gap-4">
						<div>
							<p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('import.ingest.direct_upload.eyebrow')}</p>
							<h3 class="mt-2 text-xl font-semibold text-foreground">{$t('import.ingest.direct_upload.title')}</h3>
						</div>
						<div class="rounded-full bg-primary/10 p-3 text-primary">
							<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
						</div>
					</div>
					<p class="mb-6 text-sm leading-6 text-muted-foreground">{$t('import.ingest.direct_upload.description')}</p>
				<form onsubmit={(e) => { e.preventDefault(); handleUpload(); }} class="flex flex-1 flex-col space-y-6">
					<FormField label={$t('import.ingest.direct_upload.field_label')} forId="file">
						<input
							id="file"
							type="file"
							accept=".epub"
							bind:this={fileInput}
							class="ui-input cursor-pointer px-4 py-2 file:mr-4 file:rounded-lg file:border-0 file:bg-primary file:px-4 file:py-2 file:text-sm file:font-semibold file:text-primary-foreground hover:file:bg-primary/90"
						/>
					</FormField>

					{#if error}
						<StatusBanner kind="error" title={$t('import.ingest.direct_upload.failed_title')} message={error} />
					{/if}

					{#if success}
						<div class="flex items-center justify-between rounded-2xl border border-primary/20 bg-primary/10 px-4 py-3 text-sm text-foreground">
							<div>
								<p class="text-[11px] font-bold uppercase tracking-[0.28em] text-primary">{$t('import.ingest.direct_upload.success_title')}</p>
								<p class="mt-2">{$t('import.ingest.direct_upload.success_message')}</p>
							</div>
							<a href={resolve("/import/staged")} class="rounded-xl bg-primary px-3 py-2 text-center text-primary-foreground transition-colors hover:bg-primary/90">
								{$t('import.ingest.direct_upload.success_action')}
							</a>
						</div>
					{/if}

					<button
						type="submit"
						disabled={uploading}
						class="mt-auto flex w-full items-center justify-center rounded-xl bg-primary px-4 py-3 font-bold text-primary-foreground transition-colors hover:bg-primary/90 disabled:bg-muted"
					>
						{#if uploading}
							<span class="mr-2 h-4 w-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
							{$t('import.ingest.direct_upload.loading')}
						{:else}
							{$t('import.ingest.direct_upload.action')}
						{/if}
					</button>
				</form>
				</div>

				<div class="flex h-full flex-col rounded-[1.5rem] border border-border bg-card p-6 shadow-xl shadow-primary/5">
					<div class="mb-5 flex items-start justify-between gap-4">
						<div>
							<p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('import.ingest.scan.eyebrow')}</p>
							<h3 class="mt-2 text-xl font-semibold text-foreground">{$t('import.ingest.scan.title')}</h3>
						</div>
						<div class="rounded-full bg-primary/10 p-3 text-primary">
							<svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><path d="m21 21-4.3-4.3"/></svg>
						</div>
					</div>
					<p class="mb-6 text-sm leading-6 text-muted-foreground">{$t('import.ingest.scan.description')}</p>
				<form onsubmit={(e) => { e.preventDefault(); handleScan(); }} class="flex flex-1 flex-col space-y-6">
					<FormField
						label={$t('import.ingest.scan.field_label')}
						forId="scanPath"
						hint={$t('import.ingest.scan.field_hint')}
					>
						<input
							id="scanPath"
							type="text"
							bind:value={scanPath}
							placeholder={$t('import.ingest.scan.field_placeholder')}
							class="ui-input"
						/>
					</FormField>

					{#if scanError}
						<StatusBanner kind="error" title={$t('import.ingest.scan.failed_title')} message={scanError} />
					{/if}

					{#if scanSuccess}
						<div class="flex flex-col space-y-3 rounded-2xl border border-primary/20 bg-primary/10 px-4 py-3 text-sm text-foreground">
							<div>
								<p class="text-[11px] font-bold uppercase tracking-[0.28em] text-primary">{$t('import.ingest.scan.success_title')}</p>
								<p class="mt-2">{$t('import.ingest.scan.success_message')}</p>
							</div>
							<a href={resolve("/import/staged")} class="rounded-xl bg-primary px-4 py-2 text-center font-medium text-primary-foreground transition-colors hover:bg-primary/90">
								{$t('import.ingest.scan.success_action')}
							</a>
						</div>
					{/if}

					<button
						type="submit"
						disabled={scanning}
						class="mt-auto flex w-full items-center justify-center rounded-xl bg-primary px-4 py-3 font-bold text-primary-foreground transition-colors hover:bg-primary/90 disabled:bg-muted"
					>
						{#if scanning}
							<span class="mr-2 h-4 w-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
							{$t('import.ingest.scan.loading')}
						{:else}
							{$t('import.ingest.scan.action')}
						{/if}
					</button>
				</form>
				</div>
			</div>
		</div>

		<aside class="h-full">
			<div class="flex h-full flex-col rounded-[1.5rem] border border-border bg-card p-6 shadow-xl shadow-primary/5">
				<p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{$t('import.ingest.workflow.eyebrow')}</p>
				<h3 class="mt-2 text-2xl font-semibold text-foreground">{$t('import.ingest.workflow.title')}</h3>
				<div class="mt-5 flex flex-1 flex-col gap-4">
					<div class="rounded-2xl border border-border bg-background/80 p-4">
						<p class="text-sm font-semibold text-foreground">{$t('import.ingest.workflow.stage_title')}</p>
						<p class="mt-1 text-sm leading-6 text-muted-foreground">{$t('import.ingest.workflow.stage_description')}</p>
					</div>
					<div class="rounded-2xl border border-border bg-background/80 p-4">
						<p class="text-sm font-semibold text-foreground">{$t('import.ingest.workflow.resolve_title')}</p>
						<p class="mt-1 text-sm leading-6 text-muted-foreground">{$t('import.ingest.workflow.resolve_description')}</p>
					</div>
					<div class="rounded-2xl border border-border bg-background/80 p-4">
						<p class="text-sm font-semibold text-foreground">{$t('import.ingest.workflow.publish_title')}</p>
						<p class="mt-1 text-sm leading-6 text-muted-foreground">{$t('import.ingest.workflow.publish_description')}</p>
					</div>
				</div>
			</div>
		</aside>
	</div>
</div>
