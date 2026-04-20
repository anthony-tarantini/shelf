<script lang="ts">
    import { browser } from '$app/environment';
    import { api } from '$lib/api/client';

    let {
        src,
        alt = '',
        class: className = '',
    } = $props<{
        src: string;
        alt?: string;
        class?: string;
    }>();

    let objectUrl = $state<string | null>(null);
    let loadedSrc = $state<string | null>(null);

    async function loadImage(path: string) {
        if (!browser) return;

        const token = api.getToken();
        if (!token) {
            objectUrl = null;
            return;
        }

        const response = await fetch(path, {
            headers: { Authorization: `Bearer ${token}` },
        });

        if (!response.ok) {
            objectUrl = null;
            return;
        }

        const blob = await response.blob();
        objectUrl = URL.createObjectURL(blob);
        loadedSrc = path;
    }

    $effect(() => {
        const currentSrc = src;

        if (!currentSrc) {
            objectUrl = null;
            loadedSrc = null;
            return;
        }

        if (loadedSrc === currentSrc && objectUrl) {
            return;
        }

        const previousUrl = objectUrl;
        objectUrl = null;
        void loadImage(src);

        return () => {
            if (previousUrl) {
                URL.revokeObjectURL(previousUrl);
            }
        };
    });
</script>

{#if objectUrl}
    <img src={objectUrl} {alt} class={className} />
{:else}
    <div class={className}></div>
{/if}
