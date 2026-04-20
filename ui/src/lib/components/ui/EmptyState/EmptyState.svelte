<script lang="ts">
    import { resolve } from '$app/paths';

    let {
        message = '',
        title = '',
        eyebrow = '',
        actionText = undefined,
        href = undefined
    } = $props<{
        message?: string;
        title?: string;
        eyebrow?: string;
        actionText?: string;
        href?: string;
    }>();
</script>
<div class="text-center py-20 bg-card/50 rounded-[1.5rem] border border-border border-dashed shadow-lg shadow-black/5">
    <div class="mx-auto mb-5 flex h-14 w-14 items-center justify-center rounded-full border border-border bg-background/70 text-primary">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-7 w-7" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.8" d="M12 6v12m6-6H6" />
        </svg>
    </div>
    {#if eyebrow}
        <p class="mb-2 text-[10px] font-bold uppercase tracking-[0.3em] text-muted-foreground">{eyebrow}</p>
    {/if}
    {#if title}
        <h3 class="font-display text-3xl font-bold text-foreground mb-3">{title}</h3>
    {/if}
    <div class="mx-auto mb-4 max-w-xl text-muted-foreground">{message}</div>
    {#if actionText && href}
        <a href={resolve(href)}
           class="inline-block px-6 py-3 bg-primary text-primary-foreground font-medium rounded-xl hover:bg-primary/90 transition-colors shadow-lg shadow-primary/20">
            {actionText}
        </a>
    {/if}
</div>
