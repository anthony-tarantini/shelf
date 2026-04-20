import { api } from '$lib/api/client';
import type { BookPage } from '$lib/types/models';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const result = await api.get<BookPage>('/books/page', fetch);

	return {
		bookPage: result.right || { items: [], totalCount: 0, page: 0, size: 20 }
	};
};
