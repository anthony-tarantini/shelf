<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		title,
		disabled = false,
		variant = 'default',
		children,
		...rest
	} = $props<{
		title: string;
		disabled?: boolean;
		variant?: 'default' | 'destructive';
		children?: Snippet;
		[key: string]: unknown;
	}>();
</script>

<div class="group/row-action relative">
	<button
		class="flex h-8 w-8 items-center justify-center rounded-lg border border-transparent transition-colors disabled:opacity-30 {variant === 'destructive' ? 'text-muted-foreground hover:border-destructive/20 hover:bg-destructive/10 hover:text-destructive' : 'text-muted-foreground hover:border-primary/20 hover:bg-primary/10 hover:text-primary'}"
		aria-label={title}
		{title}
		{disabled}
		{...rest}
	>
		{@render children?.()}
	</button>

	{#if !disabled}
		<div
			class="pointer-events-none absolute bottom-full right-0 z-20 mb-2 whitespace-nowrap rounded-lg border border-border bg-card/95 px-2 py-1 text-[11px] font-medium text-foreground opacity-0 shadow-lg shadow-black/10 transition-opacity group-hover/row-action:opacity-100 group-focus-within/row-action:opacity-100"
		>
			{title}
		</div>
	{/if}
</div>
