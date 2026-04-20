<script lang="ts">
	import { fly, fade } from 'svelte/transition';
	import { toast } from '$lib/state/toast.svelte';
	import { t } from '$lib/i18n';
</script>

<div class="pointer-events-none fixed right-4 top-4 z-[60] flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-3">
	{#each $toast as item (item.id)}
		<div
			in:fly={{ y: -12, duration: 180 }}
			out:fade={{ duration: 140 }}
			class="pointer-events-auto overflow-hidden rounded-2xl border shadow-2xl backdrop-blur-md {item.kind === 'error' ? 'border-destructive/20 bg-destructive/10 text-destructive' : item.kind === 'success' ? 'border-primary/20 bg-primary/10 text-foreground' : 'border-border bg-card/95 text-foreground'}"
		>
			<div class="flex items-start gap-3 px-4 py-3">
				<div class="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full {item.kind === 'error' ? 'bg-destructive/15 text-destructive' : item.kind === 'success' ? 'bg-primary/15 text-primary' : 'bg-accent text-foreground'}">
					{#if item.kind === 'error'}
						<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M12 9v4"/><path d="M12 17h.01"/><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.72 3h16.92a2 2 0 0 0 1.72-3L13.71 3.86a2 2 0 0 0-3.42 0Z"/></svg>
					{:else if item.kind === 'success'}
						<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>
					{:else}
						<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>
					{/if}
				</div>
				<div class="min-w-0 flex-1">
					<p class="text-xs font-bold uppercase tracking-[0.28em] {item.kind === 'error' ? 'text-destructive' : item.kind === 'success' ? 'text-primary' : 'text-muted-foreground'}">
						{item.kind === 'error'
							? $t('common.feedback.action_failed')
							: item.kind === 'success'
								? $t('common.feedback.action_complete')
								: $t('common.feedback.notice')}
					</p>
					<p class="mt-1 text-sm leading-6">{item.message}</p>
				</div>
				<button
					onclick={() => toast.dismiss(item.id)}
					class="rounded-full p-1 text-current/70 transition-colors hover:bg-background/50 hover:text-current"
					aria-label={$t('common.actions.dismiss')}
				>
					×
				</button>
			</div>
		</div>
	{/each}
</div>
