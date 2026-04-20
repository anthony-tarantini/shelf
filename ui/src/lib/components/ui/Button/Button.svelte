<script lang="ts">
  import type { Snippet } from 'svelte';

  let {
    variant = 'primary',
    size = 'default',
    disabled = false,
    class: className = '',
    children,
    ...rest
  }: {
    variant?: 'primary' | 'secondary' | 'ghost' | 'destructive';
    size?: 'sm' | 'default' | 'lg';
    disabled?: boolean;
    class?: string;
    children?: Snippet;
    [key: string]: unknown;
  } = $props();

  const buttonClasses = $derived.by(() => {
    const base = "inline-flex items-center justify-center gap-2 font-medium rounded-xl border border-transparent transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-primary/40 focus:ring-offset-2 focus:ring-offset-background disabled:pointer-events-none disabled:opacity-50 shadow-sm";
    
    const variants = {
      primary: "bg-primary text-primary-foreground hover:bg-primary/90 hover:shadow-lg hover:shadow-primary/20",
      secondary: "border-border bg-secondary text-secondary-foreground hover:bg-accent hover:border-primary/20",
      ghost: "text-foreground hover:bg-accent hover:text-accent-foreground hover:border-border",
      destructive: "bg-destructive text-destructive-foreground hover:bg-destructive/90 hover:shadow-lg hover:shadow-destructive/20"
    };
    
    const sizes = {
      sm: "h-8 px-3 text-xs",
      default: "h-10 px-4 py-2 text-sm",
      lg: "h-12 px-8 text-base"
    };
    
    return `${base} ${variants[variant]} ${sizes[size]} ${className}`.trim();
  });
</script>

<button class={buttonClasses} {disabled} {...rest}>
  {@render children?.()}
</button>
