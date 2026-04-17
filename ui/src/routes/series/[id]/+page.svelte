<script lang="ts">
	import { t } from '$lib/i18n';
	import { resolve } from '$app/paths'
	import AuthenticatedImage from '$lib/components/ui/AuthenticatedImage.svelte';
	import LoadingState from '$lib/components/ui/LoadingState/LoadingState.svelte';
	import ReadStatusBadge from '$lib/components/book/ReadStatusBadge.svelte';

	let { data } = $props();
	
	const details = $derived(data.details);
	const series = $derived(details?.series);
	const authors = $derived(details?.authors ?? []);
	const books = $derived(details?.books ?? []);
</script>

<div class="max-w-6xl mx-auto">
	{#if details && series}
		<header class="mb-10 rounded-[1.75rem] border border-border/70 bg-card/70 p-6 shadow-xl shadow-black/5 backdrop-blur-md">
            <a
                href={resolve("/series")}
                class="mb-4 flex items-center text-sm font-medium text-primary hover:text-primary transition-colors"
            >
                <span class="mr-1">←</span> {$t('series.back')}
            </a>
            <div class="flex flex-col gap-6 md:flex-row md:items-end">
                <div class="w-40 shrink-0 overflow-hidden rounded-[1.25rem] border border-border/70 bg-background shadow-lg">
                    {#if series.coverPath}
                        <AuthenticatedImage
                            src={`/api/series/${series.id}/cover`}
                            alt={series.name}
                            class="aspect-[2/3] h-full w-full object-cover"
                        />
                    {:else}
                        <div class="flex aspect-[2/3] items-center justify-center p-4 text-center text-sm font-bold text-muted-foreground">
                            {series.name}
                        </div>
                    {/if}
                </div>

                <div>
                    <p class="mb-2 text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{$t('series.detail_eyebrow')}</p>
                    <h1 class="mb-2 font-display text-5xl font-bold text-foreground">{series.name}</h1>
                    <div class="flex items-center text-lg text-muted-foreground">
                        <span>{$t('series.by')}</span>
                        {#each authors as author, i (author.id)}
                            <a href={resolve(`/authors/${author.id}`)} class="mx-2 font-medium text-primary hover:text-primary/80">
                                {author.name}
                            </a>
                            {#if i < authors.length - 1}
                                <span class="mr-1">,</span>
                            {/if}
                        {/each}
                    </div>
                </div>
            </div>
		</header>

		<div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-6">
			{#each books as book (book.id)}
				<a
					href={resolve(`/books/${book.id}`)}
					class="group flex flex-col bg-card/80 border border-border rounded-[1.5rem] overflow-hidden hover:border-primary/50 hover:-translate-y-1 transition-all shadow-xl"
				>
					<div class="aspect-2/3 relative bg-background">
						{#if book.coverPath}
							<AuthenticatedImage
								src={`/api/books/${book.id}/cover`}
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
					</div>
				</a>
			{/each}
		</div>
	{:else}
		<LoadingState title={$t('series.detail_loading_title')} message={$t('series.detail_loading_message')} />
	{/if}
</div>
