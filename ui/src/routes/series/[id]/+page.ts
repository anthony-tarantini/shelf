import { api } from '$lib/api/client';
import type { AuthorRoot, BookSummary, SeriesAggregate, SeriesRoot } from '$lib/types/models';
import { error } from '@sveltejs/kit';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, params }) => {
	const [seriesResult, booksResult, authorsResult] = await Promise.all([
		api.get<SeriesRoot>(`/series/${params.id}`, fetch),
		api.get<BookSummary[]>(`/series/${params.id}/books`, fetch),
		api.get<AuthorRoot[]>(`/series/${params.id}/authors`, fetch)
	]);

	if (seriesResult.left) {
		throw error(seriesResult.left.status || 500, seriesResult.left.message);
	}
	
	if (booksResult.left) {
		throw error(booksResult.left.status || 500, booksResult.left.message);
	}

	if (authorsResult.left) {
		throw error(authorsResult.left.status || 500, authorsResult.left.message);
	}

	const rawSeries = seriesResult.right;
	const series =
		rawSeries && 'series' in rawSeries ? (rawSeries.series as SeriesRoot) : (rawSeries as SeriesRoot);

	const details: SeriesAggregate = {
		series,
		books: booksResult.right || [],
		authors: authorsResult.right || []
	};

	return {
		details
	};
};
