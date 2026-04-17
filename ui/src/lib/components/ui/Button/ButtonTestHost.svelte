<script lang="ts">
    import Button from './Button.svelte';
    let { children, ...props } = $props<{ children?: () => unknown } & Record<string, unknown>>();
</script>

<Button {...props}>
    {#if children}
        {@render children()}
    {:else}
        Default Text
    {/if}
</Button>
