<script lang="ts">
	import { t } from '$lib/i18n';
	import { resolve } from '$app/paths';
	import { formatMinutes } from '$lib/charts/theme';

	let { data } = $props();

	const rows = $derived(data.rows);
	const maxMinutes = $derived(
		Math.max(1, ...rows.map((r) => Math.round((r.totals?.totalDurationSeconds ?? 0) / 60))),
	);

	const chartData = $derived(
		rows.map((r) => ({
			id: r.id,
			editionId: r.editionId,
			label: r.title,
			minutes: Math.round((r.totals?.totalDurationSeconds ?? 0) / 60),
			pages: r.totals?.totalPagesRead ?? 0,
			sessions: r.totals?.sessionCount ?? 0,
		})),
	);
</script>

<svelte:head>
	<title>{$t('stats.books.heading')} | Shelf</title>
</svelte:head>

<div class="space-y-8">
	<header class="rounded-[1.75rem] border border-border/70 bg-card/70 p-6 shadow-xl shadow-black/5 backdrop-blur-md">
		<p class="mb-2 text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{$t('stats.eyebrow')}</p>
		<h2 class="font-display text-4xl font-bold text-primary">{$t('stats.books.heading')}</h2>
		<p class="mt-2 max-w-2xl text-muted-foreground">{$t('stats.books.subtitle')}</p>
	</header>

	{#if data.unavailable || rows.length === 0}
		<div class="rounded-[1.5rem] border border-border/70 bg-card/70 p-8 text-center">
			<svg xmlns="http://www.w3.org/2000/svg" class="mx-auto mb-4 h-10 w-10 text-muted-foreground/60" fill="none" viewBox="0 0 24 24" stroke="currentColor">
				<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20" />
			</svg>
			<h3 class="font-display text-xl text-foreground">
				{data.unavailable ? $t('stats.overview.unavailable_title') : $t('stats.books.empty')}
			</h3>
			{#if data.unavailable}
				<p class="mx-auto mt-2 max-w-md text-sm text-muted-foreground">{$t('stats.overview.unavailable_body')}</p>
			{/if}
		</div>
	{:else}
		<section class="rounded-[1.5rem] border border-border/70 bg-card/70 p-6 shadow-lg shadow-black/5 backdrop-blur-md">
			<div class="overflow-hidden rounded-2xl border border-border/70">
				<table class="w-full text-sm">
					<thead class="bg-background/70 text-[10px] font-bold uppercase tracking-[0.2em] text-muted-foreground">
						<tr>
							<th class="px-4 py-3 text-left">{$t('stats.books.col_title')}</th>
							<th class="hidden px-4 py-3 text-right md:table-cell">{$t('stats.books.col_pages')}</th>
							<th class="hidden px-4 py-3 text-right md:table-cell">{$t('stats.books.col_sessions')}</th>
							<th class="px-4 py-3 text-right">{$t('stats.books.col_duration')}</th>
							<th class="w-[35%] px-4 py-3 text-left"></th>
						</tr>
					</thead>
					<tbody>
						{#each chartData as row (row.id)}
							{@const widthPct = (row.minutes / maxMinutes) * 100}
							<tr class="border-t border-border/50 hover:bg-accent/40">
								<td class="px-4 py-3">
									{#if row.editionId}
										<a class="font-medium text-foreground hover:text-primary" href={resolve(`/stats/books/${row.editionId}`)}>{row.label}</a>
									{:else}
										<span class="text-muted-foreground">{row.label}</span>
									{/if}
								</td>
								<td class="hidden px-4 py-3 text-right md:table-cell">{row.pages.toLocaleString()}</td>
								<td class="hidden px-4 py-3 text-right md:table-cell">{row.sessions.toLocaleString()}</td>
								<td class="px-4 py-3 text-right font-mono">{formatMinutes(row.minutes * 60)}</td>
								<td class="px-4 py-3">
									<div class="h-2 rounded-full bg-muted/40">
										<div class="h-2 rounded-full bg-primary" style="width: {widthPct}%"></div>
									</div>
								</td>
							</tr>
						{/each}
					</tbody>
				</table>
			</div>
		</section>

	{/if}

	{#if data.unmatched.length > 0}
		<section class="rounded-[1.5rem] border border-border/70 bg-card/70 p-6 shadow-lg shadow-black/5 backdrop-blur-md">
			<h3 class="font-display text-xl text-foreground">{$t('stats.books.unmatched_heading')}</h3>
			<p class="mt-1 text-sm text-muted-foreground">{$t('stats.books.unmatched_subtitle')}</p>
			<ul class="mt-4 space-y-2">
				{#each data.unmatched as book (book.id)}
					<li class="rounded-xl border border-border/70 bg-background/60 px-4 py-3">
						<p class="font-medium text-foreground">{book.title}</p>
						{#if book.authors}
							<p class="text-xs text-muted-foreground">{book.authors}</p>
						{/if}
					</li>
				{/each}
			</ul>
		</section>
	{/if}
</div>
