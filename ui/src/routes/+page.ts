import { api } from '$lib/api/client';
import type { BookPage } from '$lib/types/models';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, url }) => {
	const title = url.searchParams.get('title')?.trim() ?? '';
	const author = url.searchParams.get('author')?.trim() ?? '';
	const series = url.searchParams.get('series')?.trim() ?? '';
	const status = url.searchParams.get('status')?.trim() ?? '';
	const format = url.searchParams.get('format')?.trim() ?? '';

	const params = new URLSearchParams();
	if (title) params.set('title', title);
	if (author) params.set('author', author);
	if (series) params.set('series', series);
	if (status) params.set('status', status);
	if (format) params.set('format', format);

	const query = params.toString();
	const result = await api.get<BookPage>(query ? `/books/page?${query}` : '/books/page', fetch);

	return {
		bookPage: result.right || { items: [], totalCount: 0, page: 0, size: 20 },
		initialFilters: {
			title,
			author,
			series,
			status,
			format
		}
	};
};
