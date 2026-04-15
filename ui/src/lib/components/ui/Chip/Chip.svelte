<script lang="ts">
  let {
    label,
    selected = false,
    onClick
  }: {
    label: string;
    selected?: boolean;
    onClick?: () => void;
  } = $props();
  
  const chipClasses = $derived(
    `rounded-full border px-3 py-1 text-xs font-medium transition-all ${
      selected 
        ? 'border-primary/30 bg-primary text-primary-foreground shadow-lg shadow-primary/20' 
        : 'border-border bg-card/90 text-muted-foreground hover:border-primary/20 hover:bg-accent hover:text-foreground'
    }`
  );
</script>

<button type="button" class={chipClasses} onclick={onClick}>
  {label}
</button>
