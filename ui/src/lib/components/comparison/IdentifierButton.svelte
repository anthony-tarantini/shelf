<script lang="ts">
    import { t } from '$lib/i18n';

    let { value, typeKey, onUse } = $props<{
        value: string;
        typeKey: 'ISBN10' | 'ISBN13' | 'ASIN';
        onUse: (val: string) => void;
    }>();

    const buttonClass = "px-3 py-1 bg-card hover:bg-primary/20 text-primary border border-border hover:border-primary rounded-full text-xs transition-all";
</script>

<button
        type="button"
        onclick={() => onUse(value)}
        class={buttonClass}
>
    {value} ({$t(`metadata.identifiers.${typeKey}`)})
</button>