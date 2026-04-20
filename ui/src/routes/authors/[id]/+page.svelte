<script lang="ts">
	import { t } from '$lib/i18n';
	import { resolve } from '$app/paths';
	import { invalidateAll } from '$app/navigation';
	import type { BookSummary } from '$lib/types/models';
	import AuthenticatedImage from '$lib/components/ui/AuthenticatedImage.svelte';
	import AuthorImageDialog from '$lib/components/AuthorImageDialog.svelte';
	import LoadingState from '$lib/components/ui/LoadingState/LoadingState.svelte';
	import ReadStatusBadge from '$lib/components/book/ReadStatusBadge.svelte';

	let { data } = $props();

	const details = $derived(data.details);
	const author = $derived(details?.author);
	const books = $derived(details?.books ?? []);

	const booksByGroup = $derived.by(() => {
		const groups: Record<string, typeof books> = {};
		const others: typeof books = [];

		for (const book of books) {
			if (book.seriesName) {
				if (!groups[book.seriesName]) groups[book.seriesName] = [];
				groups[book.seriesName].push(book);
			} else {
				others.push(book);
			}
		}

		// Sort each series by index
		for (const name in groups) {
			groups[name].sort((a, b) => (a.seriesIndex ?? 0) - (b.seriesIndex ?? 0));
		}

		// Sort individual books by title
		others.sort((a, b) => a.title.localeCompare(b.title));

		const series = Object.keys(groups)
			.sort()
			.map((name) => ({ name, books: groups[name] }));

		return { series, others };
	});

	let imageDialogOpen = $state(false);

	function getInitials(name: string): string {
		return name
			.split(' ')
			.map((w) => w[0])
			.slice(0, 2)
			.join('')
			.toUpperCase();
	}

	async function handleImageSaved() {
		imageDialogOpen = false;
		await invalidateAll();
	}
</script>

<div class="max-w-6xl mx-auto">
	{#if details && author}
		{#snippet bookCard(book: BookSummary)}
			<a
				href={resolve(`/books/${book.id}`)}
				class="group flex flex-col bg-card/80 border border-border rounded-[1.5rem] overflow-hidden hover:border-primary/50 hover:-translate-y-1 transition-all shadow-xl"
			>
				<div class="aspect-2/3 relative bg-background">
					{#if book.coverPath}
						<AuthenticatedImage
							src={`/api/books/${book.id}/cover?v=${encodeURIComponent(book.coverPath)}`}
							alt={book.title}
							class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
						/>
					{:else}
						<div class="w-full h-full flex items-center justify-center text-gray-700 font-bold p-4 text-center text-sm">
							{book.title}
						</div>
					{/if}
				</div>
				<div class="p-4 flex-1">
					{#if book.userState}
						<div class="mb-2">
							<ReadStatusBadge status={book.userState.readStatus} />
						</div>
					{/if}
					<h3 class="text-sm font-bold text-foreground group-hover:text-primary transition-colors line-clamp-2">
						{book.title}
					</h3>
					{#if book.seriesIndex !== null && book.seriesIndex !== undefined}
						<p class="text-[10px] text-muted-foreground mt-1 font-medium uppercase tracking-wider">
							Book {book.seriesIndex}
						</p>
					{/if}
				</div>
			</a>
		{/snippet}

		<header class="mb-10 rounded-[1.75rem] border border-border/70 bg-card/70 p-6 shadow-xl shadow-black/5 backdrop-blur-md">
			<a
				href={resolve("/authors")}
				class="text-sm font-medium text-primary hover:text-primary transition-colors flex items-center mb-4"
			>
				<span class="mr-1">←</span> {$t('authors.back')}
			</a>

			<div class="flex items-center gap-5">
				<!-- Author avatar with edit button -->
				<div class="relative flex-shrink-0">
					<div class="h-20 w-20 overflow-hidden rounded-full border border-border bg-muted">
						{#if author.imagePath}
							<AuthenticatedImage
								src={`/api/authors/${author.id}/image`}
								alt={author.name}
								class="h-full w-full object-cover"
							/>
						{:else}
							<div class="flex h-full w-full items-center justify-center bg-primary/10 text-xl font-bold text-primary">
								{getInitials(author.name)}
							</div>
						{/if}
					</div>
					<button
						onclick={() => (imageDialogOpen = true)}
						class="absolute -bottom-1 -right-1 flex h-7 w-7 items-center justify-center rounded-full border border-border bg-card shadow-md transition-colors hover:bg-accent"
						title="Edit author image"
					>
						<svg class="h-3.5 w-3.5 text-foreground" fill="none" stroke="currentColor" viewBox="0 0 24 24">
							<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
						</svg>
					</button>
				</div>

				<div>
					<p class="mb-1 text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{$t('authors.detail_eyebrow')}</p>
					<h1 class="font-display text-5xl font-bold text-foreground mb-2">{author.name}</h1>
					<p class="text-muted-foreground text-lg">
						{$t(
							books.length === 1 ? 'common.counts.books_in_library_one' : 'common.counts.books_in_library_other',
							{ count: books.length }
						)}
					</p>
				</div>
			</div>
		</header>

		<div class="space-y-12">
			{#each booksByGroup.series as group}
				<section>
					<div class="flex items-center gap-4 mb-6">
						<h2 class="text-xl font-bold text-foreground">{group.name}</h2>
						<div class="h-px flex-1 bg-border/50"></div>
					</div>
					<div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-6">
						{#each group.books as book (book.id)}
							{@render bookCard(book)}
						{/each}
					</div>
				</section>
			{/each}

			{#if booksByGroup.others.length > 0}
				<section>
					{#if booksByGroup.series.length > 0}
						<div class="flex items-center gap-4 mb-6">
							<h2 class="text-xl font-bold text-foreground">{$t('authors.other_books')}</h2>
							<div class="h-px flex-1 bg-border/50"></div>
						</div>
					{/if}
					<div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-6">
						{#each booksByGroup.others as book (book.id)}
							{@render bookCard(book)}
						{/each}
					</div>
				</section>
			{/if}
		</div>

		<AuthorImageDialog
			open={imageDialogOpen}
			authorId={author.id}
			authorName={author.name}
			onClose={() => (imageDialogOpen = false)}
			onSaved={handleImageSaved}
		/>
	{:else}
		<LoadingState title={$t('authors.detail_loading_title')} message={$t('authors.loading')} />
	{/if}
</div>
