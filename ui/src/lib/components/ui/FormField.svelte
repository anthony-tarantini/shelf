<script lang="ts">
	import type { Snippet } from 'svelte';

	let {
		label,
		forId,
		hint = '',
		children
	} = $props<{
		label: string;
		forId?: string;
		hint?: string;
		children?: Snippet;
	}>();
</script>

<div class="space-y-2">
	<label for={forId} class="block text-xs font-bold uppercase tracking-[0.24em] text-muted-foreground">
		{label}
	</label>
	{@render children?.()}
	{#if hint}
		<p class="text-xs text-muted-foreground">{hint}</p>
	{/if}
</div>
