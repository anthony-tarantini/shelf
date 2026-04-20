<script lang="ts">
	import type { AuthorSummary } from '../types/models.ts';
	import { resolve } from '$app/paths';
	import { t } from '$lib/i18n';
	import AuthenticatedImage from './ui/AuthenticatedImage.svelte';

	interface Props {
		authors: AuthorSummary[];
		sortBy?: string;
		sortDir?: 'ASC' | 'DESC';
	}

	let {
		authors,
		sortBy = $bindable('name'),
		sortDir = $bindable('ASC'),
	}: Props = $props();

	function handleSort(column: string) {
		if (sortBy === column) {
			sortDir = sortDir === 'ASC' ? 'DESC' : 'ASC';
		} else {
			sortBy = column;
			sortDir = 'ASC';
		}
	}
</script>

<div class="w-full overflow-x-auto rounded-lg border border-border shadow-xl">
	<table class="w-full border-collapse text-left text-sm">
		<thead class="sticky top-0 z-10 bg-card/95 text-muted-foreground backdrop-blur">
			<tr>
				<th class="p-3 w-16">{$t('authors.table.image')}</th>
				<th class="p-3 cursor-pointer hover:text-foreground transition-colors" onclick={() => handleSort('name')}>
					{$t('authors.table.name')} {sortBy === 'name' ? (sortDir === 'ASC' ? '↑' : '↓') : ''}
				</th>
				<th class="p-3 cursor-pointer hover:text-foreground transition-colors" onclick={() => handleSort('bookCount')}>
					{$t('authors.table.books')} {sortBy === 'bookCount' ? (sortDir === 'ASC' ? '↑' : '↓') : ''}
				</th>
			</tr>
		</thead>
		<tbody class="divide-y divide-border bg-card/80">
			{#each authors as author (author.id)}
				<tr class="group transition-colors hover:bg-accent/30">
					<td class="p-3">
						<a href={resolve(`/authors/${author.id}`)} class="block">
							{#if author.imagePath}
								<AuthenticatedImage
									src={`/api/authors/${author.id}/image`}
									alt=""
									class="w-10 h-14 object-contain p-0.5 rounded shadow-sm bg-background hover:opacity-80 transition-opacity"
								/>
							{:else}
								<div class="w-10 h-14 bg-background rounded border border-border flex items-center justify-center text-[8px] text-muted-foreground text-center px-1 hover:opacity-80 transition-opacity">
									{$t('authors.table.no_image')}
								</div>
							{/if}
						</a>
					</td>
					<td class="p-3 font-medium text-foreground">
						<a href={resolve(`/authors/${author.id}`)} class="hover:text-primary transition-colors">
							{author.name}
						</a>
					</td>
					<td class="p-3 text-muted-foreground">
						{$t(author.bookCount === 1 ? 'common.counts.book_one' : 'common.counts.book_other', { count: author.bookCount })}
					</td>
				</tr>
			{/each}
		</tbody>
	</table>
</div>
