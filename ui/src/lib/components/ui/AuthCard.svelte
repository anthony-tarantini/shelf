<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		eyebrow = '',
		title,
		subtitle = '',
		children,
		footer
	} = $props<{
		eyebrow?: string;
		title: string;
		subtitle?: string;
		children?: Snippet;
		footer?: Snippet;
	}>();
</script>

<div class="flex min-h-[70vh] items-center justify-center">
	<div class="w-full max-w-md">
		<div class="rounded-[1.75rem] border border-border bg-card p-8 shadow-2xl shadow-black/10">
			<header class="mb-8 text-center">
				{#if eyebrow}
					<p class="mb-3 text-[10px] font-bold uppercase tracking-[0.3em] text-primary">{eyebrow}</p>
				{/if}
				<h2 class="text-3xl font-bold text-primary">{title}</h2>
				{#if subtitle}
					<p class="mt-2 text-muted-foreground">{subtitle}</p>
				{/if}
			</header>

			{@render children?.()}

			{#if footer}
				<footer class="mt-8 border-t border-border pt-6 text-center">
					{@render footer()}
				</footer>
			{/if}
		</div>
	</div>
</div>
