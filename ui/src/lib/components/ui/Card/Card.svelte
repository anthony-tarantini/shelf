<script lang="ts">
  import type { Snippet } from 'svelte';

  let { 
    variant = 'default', 
    class: className = '', 
    children,
    ...rest
  }: {
    variant?: 'default' | 'outline';
    class?: string;
    children?: Snippet;
    [key: string]: unknown;
  } = $props();

  const cardClasses = $derived.by(() => {
    const base = "rounded-[1.5rem] border border-border/80 bg-card text-card-foreground shadow-lg shadow-black/5";
    return variant === 'outline' ? `${base} border-dashed bg-card/70 ${className}`.trim() : `${base} ${className}`.trim();
  });
</script>

<div class={cardClasses} {...rest}>
  {@render children?.()}
</div>
