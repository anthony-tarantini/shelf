<script lang="ts">
    import '../../app.css';
    import {auth} from '$lib/auth.svelte';
    import {goto} from '$app/navigation';
    import {resolve} from "$app/paths";

    let {children} = $props();

    // Redirect if not logged in
    $effect(() => {
        if (auth.isInitialized && !auth.currentUser) {
            goto(resolve('/login'));
        }
    });
</script>

<div class="h-screen w-screen bg-background text-foreground overflow-hidden">
    {@render children()}
</div>