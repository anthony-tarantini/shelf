<script lang="ts">
	import { t } from '$lib/i18n';
	import { api } from '../api/client.ts';
	import type { StagedBook } from '../types/models.ts';
	import { invalidateAll } from '$app/navigation';
	import StagedBookDisplay from './StagedBookDisplay.svelte';
	import StagedBookEditor from './StagedBookEditor.svelte';
	import MetadataManager from './metadata/MetadataManager.svelte';
	import MergeDialog from './ui/MergeDialog.svelte';
	import AuthenticatedImage from './ui/AuthenticatedImage.svelte';
	import ConfirmDialog from './ui/ConfirmDialog.svelte';

	let { book, onActionSuccess } = $props<{
		book: StagedBook;
		onActionSuccess?: () => void;
	}>();

	let viewMode = $state<'display' | 'edit' | 'metadata'>('display');
	let showMergeDialog = $state(false);
	let processing = $state(false);
	let isFetchingMetadata = $state(false);
	let error = $state<string | null>(null);
	let showDeleteConfirm = $state(false);

	function handleError(msg: string) {
		error = msg;
	}

	function clearError() {
		error = null;
	}

	async function promote() {
		clearError();
		processing = true;
		const result = await api.post<unknown>(`/books/staged/${book.id}/promote`);
		if (result.left) {
			handleError(result.left.message);
		} else {
			onActionSuccess?.();
			await invalidateAll();
		}
		processing = false;
	}

	async function merge(targetBookId: string) {
		clearError();
		processing = true;
		const result = await api.post<unknown>(`/books/staged/${book.id}/merge`, { targetBookId });
		if (result.left) {
			handleError(result.left.message);
		} else {
			showMergeDialog = false;
			onActionSuccess?.();
			await invalidateAll();
		}
		processing = false;
	}

	async function remove() {
		clearError();
		processing = true;
		const result = await api.delete<unknown>(`/books/staged/${book.id}`);
		if (result.left) {
			handleError(result.left.message);
		} else {
			onActionSuccess?.();
			await invalidateAll();
		}
		processing = false;
		showDeleteConfirm = false;
	}

	function handleMetadataFetchToggle() {
		clearError();
		if (viewMode === 'metadata') {
			viewMode = 'display';
		} else {
			viewMode = 'metadata';
		}
	}
</script>

<div class="bg-card/80 border border-border rounded-[1.5rem] p-6 shadow-xl flex gap-6">
	{#if book.coverPath}
		<AuthenticatedImage
			src={`/api/books/staged/${book.id}/cover`}
			alt={book.title}
			class="w-32 h-48 object-cover rounded-xl shadow-lg bg-background shrink-0"
		/>
	{:else}
		<div
			class="w-32 h-48 bg-background rounded-xl shadow-lg flex items-center justify-center text-gray-700 shrink-0"
		>
			{$t('metadata.no_cover')}
		</div>
	{/if}

	<div class="grow min-w-0">
		{#if error}
			<div class="bg-destructive/20 border border-destructive text-destructive-foreground px-4 py-3 rounded-md text-sm mb-4 flex justify-between items-center">
				<span>{error}</span>
				<button onclick={clearError} class="text-destructive-foreground hover:text-foreground">&times;</button>
			</div>
		{/if}

		{#if viewMode === 'edit'}
			<StagedBookEditor 
				{book} 
				onCancel={() => { clearError(); viewMode = 'display'; }} 
				onSaveSuccess={async () => { viewMode = 'display'; onActionSuccess?.(); await invalidateAll(); }}
				onError={handleError}
			/>
		{:else}
			<StagedBookDisplay 
				{book} 
				{processing}
				{isFetchingMetadata}
				onEdit={() => { clearError(); viewMode = 'edit'; }}
				onFetchMetadata={handleMetadataFetchToggle}
				onPromote={promote}
				onMerge={() => { clearError(); showMergeDialog = true; }}
				onDelete={() => { clearError(); showDeleteConfirm = true; }}
			>
				{#if viewMode === 'metadata'}
					<MetadataManager 
						{book} 
						bind:isFetching={isFetchingMetadata}
						onCancel={() => viewMode = 'display'}
						onApplySuccess={async () => { viewMode = 'display'; onActionSuccess?.(); await invalidateAll(); }}
						onError={handleError}
					/>
				{/if}
			</StagedBookDisplay>
		{/if}
	</div>
</div>

{#if showMergeDialog}
	<MergeDialog
		{book}
		{processing}
		onClose={() => showMergeDialog = false}
		onMerge={merge}
	/>
{/if}

<ConfirmDialog
	open={showDeleteConfirm}
	title={$t('import.confirmations.delete_staged')}
	message={$t('import.confirmations.delete_staged_message')}
	confirmLabel={$t('common.actions.delete')}
	variant="destructive"
	processing={processing}
	onCancel={() => (showDeleteConfirm = false)}
	onConfirm={() => void remove()}
/>
