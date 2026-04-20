<script lang="ts">
    import { t } from '$lib/i18n';
    import type { ReadStatus } from '$lib/types/models';

    let { status } = $props<{ status: ReadStatus }>();

    const tone = $derived.by(() => {
        switch (status) {
            case 'READING':
                return 'border-primary/30 bg-primary/10 text-primary';
            case 'FINISHED':
                return 'border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400';
            case 'ABANDONED':
                return 'border-amber-500/30 bg-amber-500/10 text-amber-700 dark:text-amber-300';
            case 'QUEUED':
                return 'border-sky-500/30 bg-sky-500/10 text-sky-700 dark:text-sky-300';
            default:
                return 'border-border bg-accent/60 text-muted-foreground';
        }
    });

    const label = $derived(t.get(`books.read_status.options.${status.toLowerCase()}`));
</script>

<span class={`inline-flex items-center rounded-full border px-2.5 py-1 text-[10px] font-bold uppercase tracking-[0.18em] ${tone}`}>
    {label}
</span>
