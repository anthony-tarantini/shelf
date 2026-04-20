<script lang="ts">
	let {
		kind = 'info',
		title = '',
		message,
		compact = false
	} = $props<{
		kind?: 'success' | 'error' | 'info';
		title?: string;
		message: string;
		compact?: boolean;
	}>();
</script>

<div class="rounded-2xl border px-4 py-3 text-sm {kind === 'error' ? 'border-destructive/20 bg-destructive/10 text-destructive' : kind === 'success' ? 'border-primary/20 bg-primary/10 text-foreground' : 'border-border bg-accent/60 text-foreground'}">
	{#if title}
		<p class="text-[11px] font-bold uppercase tracking-[0.28em] {kind === 'error' ? 'text-destructive' : kind === 'success' ? 'text-primary' : 'text-muted-foreground'}">
			{title}
		</p>
	{/if}
	<p class:mt-2={!compact && !!title}>{message}</p>
</div>
