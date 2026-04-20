<script lang="ts">
	let { label, selected, onToggle }: { label: string; selected: boolean; onToggle: () => void } = $props();
</script>

<button
	type="button"
	onclick={onToggle}
	class="px-3 py-1 rounded-full text-xs group flex items-center gap-1 transition-all {selected ? 'bg-primary text-primary-foreground border border-primary shadow-lg shadow-primary/20 hover:bg-primary' : 'bg-card text-primary border border-border hover:bg-primary/20 hover:border-primary'}"
>
	<span>{label}</span>
	{#if selected}
		<span class="opacity-50 group-hover:opacity-100">×</span>
	{/if}
</button>
