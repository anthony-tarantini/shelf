<script lang="ts">
	import { t } from '$lib/i18n';
	import { resolve } from '$app/paths';
	import { Axis, Bars, Chart, Svg, Tooltip } from 'layerchart';
	import { scaleTime } from 'd3-scale';
	import { formatMinutes, formatDate, formatTime } from '$lib/charts/theme';

	let { data } = $props();

	type Bar = { startedAt: Date; endedAt: Date; minutes: number; pages: number };

	const bars: Bar[] = $derived(
		data.sessions.map((s) => ({
			startedAt: new Date(s.startedAt),
			endedAt: new Date(s.endedAt),
			minutes: Math.round(s.durationSeconds / 60),
			pages: s.pagesRead,
		})),
	);

	const totals = $derived(data.totals);
</script>

<svelte:head>
	<title>{$t('stats.book_detail.sessions_heading')} | Shelf</title>
</svelte:head>

<div class="space-y-8">
	<header class="rounded-[1.75rem] border border-border/70 bg-card/70 p-6 shadow-xl shadow-black/5 backdrop-blur-md">
		<a class="text-xs font-semibold text-muted-foreground hover:text-primary" href={resolve('/stats/books')}>← {$t('stats.book_detail.back')}</a>
		<h2 class="mt-3 font-display text-3xl font-bold text-primary">{$t('stats.book_detail.sessions_heading')}</h2>
		<p class="mt-1 text-sm text-muted-foreground">{$t('stats.book_detail.sessions_subtitle')}</p>

		{#if totals}
			<div class="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-5">
				<div class="rounded-2xl border border-border/70 bg-background/70 px-4 py-3">
					<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('stats.book_detail.summary.sessions')}</p>
					<p class="mt-1 font-display text-xl text-foreground">{totals.sessionCount.toLocaleString()}</p>
				</div>
				<div class="rounded-2xl border border-border/70 bg-background/70 px-4 py-3">
					<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('stats.book_detail.summary.duration')}</p>
					<p class="mt-1 font-display text-xl text-foreground">{formatMinutes(totals.totalDurationSeconds)}</p>
				</div>
				<div class="rounded-2xl border border-border/70 bg-background/70 px-4 py-3">
					<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('stats.book_detail.summary.pages')}</p>
					<p class="mt-1 font-display text-xl text-foreground">{totals.totalPagesRead.toLocaleString()}</p>
				</div>
				<div class="rounded-2xl border border-border/70 bg-background/70 px-4 py-3">
					<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('stats.book_detail.summary.first')}</p>
					<p class="mt-1 text-sm font-medium text-foreground">{totals.firstSessionAt ? formatDate(totals.firstSessionAt) : '—'}</p>
				</div>
				<div class="rounded-2xl border border-border/70 bg-background/70 px-4 py-3">
					<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('stats.book_detail.summary.last')}</p>
					<p class="mt-1 text-sm font-medium text-foreground">{totals.lastSessionAt ? formatDate(totals.lastSessionAt) : '—'}</p>
				</div>
			</div>
		{/if}
	</header>

	{#if data.unavailable}
		<div class="rounded-[1.5rem] border border-border/70 bg-card/70 p-8 text-center">
			<h3 class="font-display text-xl text-foreground">{$t('stats.overview.unavailable_title')}</h3>
			<p class="mx-auto mt-2 max-w-md text-sm text-muted-foreground">{$t('stats.overview.unavailable_body')}</p>
		</div>
	{/if}

	{#if bars.length === 0}
		<div class="rounded-[1.5rem] border border-border/70 bg-card/70 p-8 text-center text-muted-foreground">
			{$t('stats.book_detail.empty_sessions')}
		</div>
	{:else}
		<section class="rounded-[1.5rem] border border-border/70 bg-card/70 p-6 shadow-lg shadow-black/5 backdrop-blur-md">
			<div class="h-[300px] w-full">
				<Chart
					data={bars}
					x={['startedAt', 'endedAt']}
					xScale={scaleTime()}
					y="minutes"
					yDomain={[0, null]}
					yNice
					padding={{ left: 36, bottom: 28, right: 12, top: 8 }}
					tooltip={{ mode: 'band' }}
				>
					<Svg>
						<Axis placement="left" grid rule format={(v) => `${v}m`} ticks={4} class="text-[10px] text-muted-foreground" />
						<Axis placement="bottom" rule format={(v) => (v as Date).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} class="text-[10px] text-muted-foreground" />
						<Bars class="fill-primary" radius={2} />
					</Svg>
					<Tooltip.Root let:data={d}>
						<Tooltip.Header>{formatDate(d.startedAt as Date)}</Tooltip.Header>
						<Tooltip.List>
							<Tooltip.Item label="Start" value={formatTime(d.startedAt as Date)} />
							<Tooltip.Item label="End" value={formatTime(d.endedAt as Date)} />
							<Tooltip.Item label="Minutes" value={d.minutes} />
							<Tooltip.Item label="Pages" value={d.pages} />
						</Tooltip.List>
					</Tooltip.Root>
				</Chart>
			</div>
		</section>
	{/if}
</div>
