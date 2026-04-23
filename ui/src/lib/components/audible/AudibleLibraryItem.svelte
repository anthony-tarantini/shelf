<script lang="ts">
	import { t } from '$lib/i18n';

	interface AudibleTitle {
		asin: string;
		title: string;
		author: string;
		type: string;
		imageUrl?: string;
	}

	interface Props {
		item: AudibleTitle;
		onImport: (asin: string, title: string) => void;
	}

	let { item, onImport }: Props = $props();
</script>

<div class="group flex items-start gap-4 rounded-xl border border-border bg-card p-4 transition-all hover:bg-accent/30 shadow-sm">
	<div class="relative h-24 w-24 shrink-0 overflow-hidden rounded-lg bg-accent/20 shadow-md">
		{#if item.imageUrl}
			<img src={item.imageUrl} alt={item.title} class="h-full w-full object-cover transition-transform group-hover:scale-105" />
		{:else}
			<div class="flex h-full w-full items-center justify-center text-muted-foreground/30">
				<svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10" fill="none" viewBox="0 0 24 24" stroke="currentColor">
					<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
				</svg>
			</div>
		{/if}
		<div class="absolute right-1 top-1 rounded bg-black/60 px-1.5 py-0.5 text-[8px] font-bold text-white uppercase tracking-wider backdrop-blur-sm">
			{item.type}
		</div>
	</div>

	<div class="flex flex-1 flex-col justify-between self-stretch py-1">
		<div>
			<h3 class="line-clamp-2 text-sm font-bold text-foreground group-hover:text-primary transition-colors">
				{item.title}
			</h3>
			<p class="mt-1 text-xs text-muted-foreground line-clamp-1">{item.author}</p>
		</div>

		<div class="flex justify-end mt-4">
			<button
				type="button"
				onclick={() => onImport(item.asin, item.title)}
				class="rounded-lg bg-primary/10 px-4 py-1.5 text-xs font-bold text-primary transition-all hover:bg-primary hover:text-primary-foreground"
			>
				{$t('audible.import_action')}
			</button>
		</div>
	</div>
</div>
