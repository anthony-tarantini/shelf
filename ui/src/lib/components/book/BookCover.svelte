<script lang="ts">
    import AuthenticatedImage from '$lib/components/ui/AuthenticatedImage.svelte';

    let {
        bookId,
        coverPath,
        title,
        aspectRatio = 'aspect-[2/3]'
    } = $props<{
        bookId: string;
        coverPath?: string | null;
        title: string;
        aspectRatio?: string;
    }>();
</script>

<div class="{aspectRatio} bg-card rounded-lg shadow-2xl overflow-hidden border border-border relative mb-8">
    {#if coverPath}
        <AuthenticatedImage
            src={`/api/books/${bookId}/cover?v=${encodeURIComponent(coverPath)}`}
            alt={title}
            class="w-full h-full object-cover"
        />
    {/if}
    <div class="absolute inset-0 flex items-center justify-center p-6 text-center z-[-1]">
        <span class="text-xl font-medium text-muted-foreground">
            {title}
        </span>
    </div>
</div>
