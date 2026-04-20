<script lang="ts">
	import { api } from '$lib/api/client';
	import type { ExternalAuthorResult } from '$lib/types/models';

	let {
		open = false,
		authorId,
		authorName,
		onClose,
		onSaved
	} = $props<{
		open: boolean;
		authorId: string;
		authorName: string;
		onClose: () => void;
		onSaved: () => void;
	}>();

	type Tab = 'upload' | 'hardcover';
	let activeTab: Tab = $state('upload');

	// Upload tab state
	let fileInput: HTMLInputElement = $state(undefined as unknown as HTMLInputElement);
	let selectedFile: File | null = $state(null);
	let uploadPreviewUrl: string | null = $state(null);
	let uploading = $state(false);
	let uploadError: string | null = $state(null);

	// Hardcover tab state - initialize from prop via derived to avoid stale capture warning
	let searchQuery = $state('');
	$effect(() => {
		if (open) searchQuery = authorName;
	});
	let searching = $state(false);
	let searchResults: ExternalAuthorResult[] = $state([]);
	let searchError: string | null = $state(null);
	let selectedResult: ExternalAuthorResult | null = $state(null);
	let applyingUrl = $state(false);

	function handleFileChange(e: Event) {
		const input = e.currentTarget as HTMLInputElement;
		const file = input.files?.[0] ?? null;
		selectedFile = file;
		uploadError = null;
		if (uploadPreviewUrl) {
			URL.revokeObjectURL(uploadPreviewUrl);
		}
		uploadPreviewUrl = file ? URL.createObjectURL(file) : null;
	}

	async function handleUpload() {
		if (!selectedFile) return;
		uploading = true;
		uploadError = null;

		const formData = new FormData();
		formData.append('file', selectedFile);

		const result = await api.upload(`/authors/${authorId}/image`, formData);
		uploading = false;

		if (result.left) {
			uploadError = result.left.message;
		} else {
			onSaved();
		}
	}

	async function handleSearch() {
		if (!searchQuery.trim()) return;
		searching = true;
		searchError = null;
		searchResults = [];
		selectedResult = null;

		const result = await api.get<ExternalAuthorResult[]>(
			`/authors/hardcover/search?query=${encodeURIComponent(searchQuery.trim())}`
		);
		searching = false;

		if (result.left) {
			searchError = result.left.message;
		} else {
			searchResults = result.right ?? [];
		}
	}

	async function handleApplyUrl() {
		if (!selectedResult?.imageUrl) return;
		applyingUrl = true;

		const result = await api.post(`/authors/${authorId}/image/url`, { url: selectedResult.imageUrl as string });
		applyingUrl = false;

		if (result.left) {
			searchError = result.left.message;
		} else {
			onSaved();
		}
	}

	function handleClose() {
		selectedFile = null;
		if (uploadPreviewUrl) {
			URL.revokeObjectURL(uploadPreviewUrl);
			uploadPreviewUrl = null;
		}
		uploadError = null;
		searchResults = [];
		selectedResult = null;
		searchError = null;
		onClose();
	}
</script>

{#if open}
	<div class="fixed inset-0 z-50 flex items-center justify-center bg-background/80 p-4 backdrop-blur-sm">
		<div class="flex w-full max-w-lg flex-col overflow-hidden rounded-[1.75rem] border border-border bg-card shadow-2xl shadow-black/10">

			<!-- Header -->
			<div class="border-b border-border bg-muted/20 px-6 py-5">
				<p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">Author Image</p>
				<h3 class="mt-1 text-xl font-semibold text-foreground">{authorName}</h3>
			</div>

			<!-- Tabs -->
			<div class="flex border-b border-border">
				<button
					onclick={() => (activeTab = 'upload')}
					class="flex-1 px-4 py-3 text-sm font-medium transition-colors {activeTab === 'upload'
						? 'border-b-2 border-primary text-primary'
						: 'text-muted-foreground hover:text-foreground'}"
				>
					Upload Image
				</button>
				<button
					onclick={() => (activeTab = 'hardcover')}
					class="flex-1 px-4 py-3 text-sm font-medium transition-colors {activeTab === 'hardcover'
						? 'border-b-2 border-primary text-primary'
						: 'text-muted-foreground hover:text-foreground'}"
				>
					Search Hardcover
				</button>
			</div>

			<!-- Upload tab -->
			{#if activeTab === 'upload'}
				<div class="flex flex-col gap-4 p-6">
					<div class="flex flex-col items-center gap-4">
						{#if uploadPreviewUrl}
							<img
								src={uploadPreviewUrl}
								alt="Preview"
								class="h-32 w-32 rounded-full border border-border object-cover"
							/>
						{:else}
							<div class="flex h-32 w-32 items-center justify-center rounded-full border-2 border-dashed border-border bg-muted/30 text-muted-foreground">
								<svg class="h-10 w-10" fill="none" stroke="currentColor" viewBox="0 0 24 24">
									<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
								</svg>
							</div>
						{/if}

						<label class="cursor-pointer rounded-xl border border-border bg-accent px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-accent/80">
							Choose Image
							<input
								bind:this={fileInput}
								type="file"
								accept="image/*"
								class="hidden"
								onchange={handleFileChange}
							/>
						</label>
					</div>

					{#if uploadError}
						<p class="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">{uploadError}</p>
					{/if}
				</div>
			{/if}

			<!-- Hardcover tab -->
			{#if activeTab === 'hardcover'}
				<div class="flex flex-col gap-4 p-6">
					<div class="flex gap-2">
						<input
							bind:value={searchQuery}
							type="text"
							placeholder="Search for an author..."
							class="flex-1 rounded-xl border border-border bg-background px-4 py-2 text-sm outline-none ring-primary focus:ring-1"
							onkeydown={(e) => e.key === 'Enter' && handleSearch()}
						/>
						<button
							onclick={handleSearch}
							disabled={searching}
							class="rounded-xl bg-primary px-4 py-2 text-sm font-bold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-50"
						>
							{searching ? 'Searching…' : 'Search'}
						</button>
					</div>

					{#if searchError}
						<p class="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">{searchError}</p>
					{/if}

					{#if searchResults.length > 0}
						<div class="grid max-h-64 grid-cols-4 gap-3 overflow-y-auto">
							{#each searchResults as result (result.id)}
								<button
									onclick={() => (selectedResult = result)}
									class="group flex flex-col items-center gap-1 rounded-xl border p-2 transition-all {selectedResult?.id === result.id
										? 'border-primary bg-primary/10'
										: 'border-border hover:border-primary/50'}"
								>
									{#if result.imageUrl}
										<img
											src={result.imageUrl}
											alt={result.name}
											class="h-16 w-16 rounded-full object-cover"
										/>
									{:else}
										<div class="flex h-16 w-16 items-center justify-center rounded-full bg-muted text-muted-foreground text-xs">
											No image
										</div>
									{/if}
									<span class="line-clamp-2 text-center text-[10px] text-muted-foreground">{result.name}</span>
								</button>
							{/each}
						</div>
					{:else if !searching && searchQuery.trim()}
						<p class="text-center text-sm text-muted-foreground">No results yet — press Search to find authors.</p>
					{/if}
				</div>
			{/if}

			<!-- Footer -->
			<div class="flex justify-end gap-3 border-t border-border bg-card/80 px-6 py-4">
				<button
					onclick={handleClose}
					class="rounded-xl border border-border bg-accent px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-accent/80"
				>
					Cancel
				</button>

				{#if activeTab === 'upload'}
					<button
						onclick={handleUpload}
						disabled={!selectedFile || uploading}
						class="rounded-xl bg-primary px-4 py-2 text-sm font-bold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-50"
					>
						{#if uploading}
							<span class="mr-2 inline-block h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white"></span>
						{/if}
						Save Image
					</button>
				{:else}
					<button
						onclick={handleApplyUrl}
						disabled={!selectedResult?.imageUrl || applyingUrl}
						class="rounded-xl bg-primary px-4 py-2 text-sm font-bold text-primary-foreground transition-colors hover:bg-primary/90 disabled:opacity-50"
					>
						{#if applyingUrl}
							<span class="mr-2 inline-block h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white"></span>
						{/if}
						Use This Image
					</button>
				{/if}
			</div>
		</div>
	</div>
{/if}
