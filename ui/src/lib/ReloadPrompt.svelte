<script lang="ts">
    import {pwaInfo} from 'virtual:pwa-info';
    import Button from '$lib/components/ui/Button/Button.svelte';
    import type {Writable} from "svelte/store";

    let needRefresh = $state(false);
    let updateServiceWorker = $state<((reloadPage?: boolean) => Promise<void>) | undefined>();
    let needRefreshStore: Writable<boolean>;

    $effect(() => {
        if (pwaInfo) {
            import('virtual:pwa-register/svelte').then(({useRegisterSW}) => {
                const sw = useRegisterSW({
                    onNeedRefresh() {
                        console.log('New content available, please refresh.');
                    },
                    onOfflineReady() {
                        console.log('App ready to work offline');
                    },
                });
                needRefreshStore = sw.needRefresh;
                updateServiceWorker = sw.updateServiceWorker;
                sw.needRefresh.subscribe((val: boolean) => {
                    needRefresh = val;
                });
            });
        }
    });

    function close() {
        if (needRefreshStore) needRefreshStore.set(false);
    }
</script>

{#if needRefresh}
    <div class="fixed right-0 bottom-0 m-4 p-4 border rounded-lg bg-card border-primary shadow-lg z-50 animate-in fade-in slide-in-from-bottom-4">
        <div class="mb-2 text-sm font-medium">
            New version available!
        </div>
        <div class="flex gap-2">
            <Button
                    onclick={() => updateServiceWorker?.(true)}
                    variant="primary" size="sm"
            >
                Reload
            </Button>
            <Button
                    onclick={close}
                    variant="ghost" size="sm"
            >
                Close
            </Button>
        </div>
    </div>
{/if}
