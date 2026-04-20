import { api } from '$lib/api/client';
import { t } from '$lib/i18n';
import type { BookAggregate } from '$lib/types/models';
import type { PageLoad } from './$types';
import { error } from '@sveltejs/kit';

export const load: PageLoad = async ({ params, fetch }) => {
	const result = await api.get<BookAggregate>(`/books/${params.id}/details`, fetch);
	
	if (result.left) {
		throw error(404, result.left.message);
	}
	
	if (!result.right) {
		throw error(404, t.get('books.not_found'));
	}
	
	return {
		details: result.right
	};
};
