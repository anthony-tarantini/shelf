<script lang="ts">
  import { t } from '$lib/i18n';

  let {
    message = t.get('common.loading'),
    title = '',
    compact = false
  } = $props<{
    message?: string;
    title?: string;
    compact?: boolean;
  }>();
</script>

<div class="flex flex-col items-center justify-center {compact ? 'py-8' : 'py-20'}">
  <div class="mb-4 h-12 w-12 rounded-full border-4 border-primary/20 border-t-primary animate-spin"></div>
  {#if title}
    <p class="text-xs font-bold uppercase tracking-[0.28em] text-primary">{title}</p>
  {/if}
  <p class="mt-2 font-medium text-muted-foreground">{message}</p>
</div>
