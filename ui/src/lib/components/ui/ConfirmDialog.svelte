<script lang="ts">
	let {
		open = $bindable(false),
		title,
		message,
		confirmLabel = 'Confirm',
		cancelLabel = 'Cancel',
		variant = 'default',
		processing = false,
		onConfirm,
		onCancel
	} = $props<{
		open: boolean;
		title: string;
		message: string;
		confirmLabel?: string;
		cancelLabel?: string;
		variant?: 'default' | 'destructive';
		processing?: boolean;
		onConfirm: () => void;
		onCancel: () => void;
	}>();
</script>

{#if open}
	<div class="fixed inset-0 z-50 flex items-center justify-center bg-background/80 p-4 backdrop-blur-sm">
		<div class="w-full max-w-md overflow-hidden rounded-[1.75rem] border border-border bg-card shadow-2xl shadow-black/10">
			<div class="border-b border-border bg-muted/20 px-6 py-5">
				<p class="text-xs font-bold uppercase tracking-[0.28em] {variant === 'destructive' ? 'text-destructive' : 'text-primary'}">
					{variant === 'destructive' ? 'Confirm destructive action' : 'Confirm action'}
				</p>
				<h3 class="mt-2 text-xl font-semibold text-foreground">{title}</h3>
				<p class="mt-3 text-sm leading-6 text-muted-foreground">{message}</p>
			</div>
			<div class="flex justify-end gap-3 bg-card/80 px-6 py-4">
				<button
					onclick={onCancel}
					disabled={processing}
					class="rounded-xl border border-border bg-accent px-4 py-2 text-sm font-medium text-foreground transition-colors hover:bg-accent/80 disabled:opacity-50"
				>
					{cancelLabel}
				</button>
				<button
					onclick={onConfirm}
					disabled={processing}
					class="rounded-xl px-4 py-2 text-sm font-bold transition-colors disabled:opacity-50 {variant === 'destructive' ? 'bg-destructive text-destructive-foreground hover:bg-destructive/90' : 'bg-primary text-primary-foreground hover:bg-primary/90'}"
				>
					{#if processing}
						<span class="mr-2 inline-block h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white"></span>
					{/if}
					{confirmLabel}
				</button>
			</div>
		</div>
	</div>
{/if}
