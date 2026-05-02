<script lang="ts">
	import { t } from '$lib/i18n';
	import { Area, Axis, Calendar, Chart, Svg, Tooltip } from 'layerchart';
	import { scaleTime } from 'd3-scale';
	import { formatMinutes } from '$lib/charts/theme';

	let { data } = $props();

	type DayPoint = { date: Date; minutes: number; pages: number; sessions: number };

	const points: DayPoint[] = $derived(
		data.daily.map((d) => ({
			date: new Date(d.day),
			minutes: Math.round(d.totalDurationSeconds / 60),
			pages: d.totalPagesRead,
			sessions: d.sessionCount,
		})),
	);

	const totalMinutes = $derived(points.reduce((acc, p) => acc + p.minutes, 0));
	const totalPages = $derived(points.reduce((acc, p) => acc + p.pages, 0));
	const activeDays = $derived(points.filter((p) => p.minutes > 0).length);

	const calendarStart = $derived(new Date(data.range.from));
	const calendarEnd = $derived(new Date(data.range.to));
</script>

<svelte:head>
	<title>{$t('stats.page_title')} | Shelf</title>
</svelte:head>

<div class="space-y-8">
	<header class="rounded-[1.75rem] border border-border/70 bg-card/70 p-6 shadow-xl shadow-black/5 backdrop-blur-md">
		<p class="mb-2 text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{$t('stats.eyebrow')}</p>
		<h2 class="font-display text-4xl font-bold text-primary">{$t('stats.page_title')}</h2>
		<p class="mt-2 max-w-2xl text-muted-foreground">{$t('stats.subtitle')}</p>

		<div class="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-3">
			<div class="rounded-2xl border border-border/70 bg-background/70 px-4 py-3">
				<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('stats.overview.total_minutes')}</p>
				<p class="mt-1 font-display text-2xl text-foreground">{totalMinutes.toLocaleString()}</p>
			</div>
			<div class="rounded-2xl border border-border/70 bg-background/70 px-4 py-3">
				<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('stats.overview.total_pages')}</p>
				<p class="mt-1 font-display text-2xl text-foreground">{totalPages.toLocaleString()}</p>
			</div>
			<div class="rounded-2xl border border-border/70 bg-background/70 px-4 py-3">
				<p class="text-[10px] font-bold uppercase tracking-[0.28em] text-muted-foreground">{$t('stats.overview.active_days')}</p>
				<p class="mt-1 font-display text-2xl text-foreground">{activeDays.toLocaleString()}</p>
			</div>
		</div>
	</header>

	{#if data.unavailable || points.length === 0}
		<div class="rounded-[1.5rem] border border-border/70 bg-card/70 p-8 text-center">
			<svg xmlns="http://www.w3.org/2000/svg" class="mx-auto mb-4 h-10 w-10 text-muted-foreground/60" fill="none" viewBox="0 0 24 24" stroke="currentColor">
				<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M3 3v18h18M7 16l4-6 4 4 5-8" />
			</svg>
			<h3 class="font-display text-xl text-foreground">{$t('stats.overview.unavailable_title')}</h3>
			<p class="mx-auto mt-2 max-w-md text-sm text-muted-foreground">
				{data.unavailable ? $t('stats.overview.unavailable_body') : $t('stats.overview.empty')}
			</p>
		</div>
	{:else}
		<section class="rounded-[1.5rem] border border-border/70 bg-card/70 p-6 shadow-lg shadow-black/5 backdrop-blur-md">
			<div class="mb-4 flex items-baseline justify-between">
				<div>
					<h3 class="font-display text-2xl text-foreground">{$t('stats.overview.daily_heading')}</h3>
					<p class="text-sm text-muted-foreground">{$t('stats.overview.daily_subtitle')}</p>
				</div>
			</div>
			<div class="h-[280px] w-full">
				<Chart
					data={points}
					x="date"
					xScale={scaleTime()}
					y="minutes"
					yDomain={[0, null]}
					yNice
					padding={{ left: 36, bottom: 28, right: 12, top: 8 }}
					tooltip={{ mode: 'bisect-x' }}
				>
					<Svg>
						<Axis placement="left" grid rule format={(v) => `${v}m`} ticks={4} class="text-[10px] text-muted-foreground" />
						<Axis placement="bottom" rule format={(v) => (v as Date).toLocaleDateString(undefined, { month: 'short' })} class="text-[10px] text-muted-foreground" />
						<Area
							line={{ class: 'stroke-primary stroke-2' }}
							fill="color-mix(in srgb, var(--primary) 25%, transparent)"
						/>
					</Svg>
					<Tooltip.Root let:data={d}>
						<Tooltip.Header>{(d.date as Date).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })}</Tooltip.Header>
						<Tooltip.List>
							<Tooltip.Item label={$t('stats.overview.total_minutes')} value={`${d.minutes}m`} />
							<Tooltip.Item label={$t('stats.overview.total_pages')} value={d.pages} />
						</Tooltip.List>
					</Tooltip.Root>
				</Chart>
			</div>
		</section>

		<section class="rounded-[1.5rem] border border-border/70 bg-card/70 p-6 shadow-lg shadow-black/5 backdrop-blur-md">
			<div class="mb-4">
				<h3 class="font-display text-2xl text-foreground">{$t('stats.overview.calendar_heading')}</h3>
				<p class="text-sm text-muted-foreground">{$t('stats.overview.calendar_subtitle')}</p>
			</div>
			<div class="h-[160px] w-full overflow-x-auto">
				<Chart
					data={points}
					x="date"
					c="minutes"
					cRange={['color-mix(in srgb, var(--muted) 60%, transparent)', 'var(--primary)']}
					cDomain={[0, Math.max(1, ...points.map((p) => p.minutes))]}
					padding={{ top: 14, left: 4, right: 4, bottom: 4 }}
				>
					<Svg>
						<Calendar start={calendarStart} end={calendarEnd} monthPath />
					</Svg>
				</Chart>
			</div>
			<p class="mt-2 text-[11px] text-muted-foreground">
				{formatMinutes(totalMinutes * 60)} read across {activeDays} active days.
			</p>
		</section>
	{/if}
</div>
