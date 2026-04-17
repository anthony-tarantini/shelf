<script lang="ts">
    import { t } from "$lib/i18n";
    import LoadingState from '$lib/components/ui/LoadingState/LoadingState.svelte';
</script>

<LoadingState title={$t('metadata.loading_title')} message={$t('metadata.loading')} compact={true} />
