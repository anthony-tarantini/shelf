<script lang="ts">
	import { t } from '$lib/i18n';
	import { api } from '$lib/api/client';
	import type { StagedBook } from '$lib/types/models';
	import BookMetadataForm, { type BookMetadataFormState } from './editor/BookMetadataForm.svelte';

	interface Props {
		book: StagedBook;
		onCancel: () => void;
		onSaveSuccess: () => void;
		onError: (msg: string) => void;
	}

	let { book, onCancel, onSaveSuccess, onError }: Props = $props();

	let processing = $state(false);

	let initialData = $derived<BookMetadataFormState>({
		id: book.id,
		title: book.title,
		authors: [...book.authors],
		description: book.description || '',
		publisher: book.publisher || '',
		publishYear: book.publishYear,
		genres: [...book.genres],
		series: book.series ? JSON.parse(JSON.stringify(book.series)) : [],
		ebookMetadata: book.ebookMetadata ? { ...book.ebookMetadata } : {},
		audiobookMetadata: book.audiobookMetadata ? { ...book.audiobookMetadata } : {},
		authorSuggestions: book.authorSuggestions,
		selectedAuthorIds: { ...book.selectedAuthorIds }
	});

	async function handleSave(formData: BookMetadataFormState) {
		processing = true;
		try {
			const cleanAuthorIds: Record<string, string | null> = {};
			for (const author of formData.authors) {
				cleanAuthorIds[author] = formData.selectedAuthorIds?.[author] ?? null;
			}

			const result = await api.patch<void>(`/books/staged/${book.id}/update`, {
				title: formData.title,
				authors: formData.authors,
				selectedAuthorIds: cleanAuthorIds,
				description: formData.description,
				publisher: formData.publisher,
				publishYear: formData.publishYear,
				genres: formData.genres,
				moods: book.moods,
				series: formData.series,
				ebookMetadata: Object.keys(formData.ebookMetadata).length > 0 ? formData.ebookMetadata : null,
				audiobookMetadata: Object.keys(formData.audiobookMetadata).length > 0 ? formData.audiobookMetadata : null,
			});
			if (result.left) {
				onError(result.left.message);
			} else {
				onSaveSuccess();
			}
		} catch (e) {
			onError(e instanceof Error ? e.message : $t('common.unknown_error'));
		} finally {
			processing = false;
		}
	}
</script>

{#key book.id}
<BookMetadataForm 
	{initialData}
	{processing}
	onSave={handleSave}
	{onCancel}
/>
{/key}