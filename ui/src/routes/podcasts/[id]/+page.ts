import { api } from '$lib/api/client';
import type { EpisodePage, PodcastAggregate } from '$lib/types/models';
import { error } from '@sveltejs/kit';
import type { PageLoad } from './$types';

const DEFAULT_PAGE_SIZE = 50;

export const load: PageLoad = async ({ params, fetch, url }) => {
	const page = Number.parseInt(url.searchParams.get('page') ?? '0', 10);
	const size = Number.parseInt(url.searchParams.get('size') ?? `${DEFAULT_PAGE_SIZE}`, 10);
	const sortDir = url.searchParams.get('sortDir') ?? 'DESC';

	const [aggregateResult, episodesResult] = await Promise.all([
		api.get<PodcastAggregate>(`/podcasts/${params.id}`, fetch),
		api.get<EpisodePage>(
			`/podcasts/${params.id}/episodes?page=${page}&size=${size}&sortDir=${sortDir}`,
			fetch
		)
	]);

	if (aggregateResult.left) {
		throw error(aggregateResult.left.status || 500, aggregateResult.left.message);
	}
	if (episodesResult.left) {
		throw error(episodesResult.left.status || 500, episodesResult.left.message);
	}

	return {
		aggregate: aggregateResult.right,
		episodePage: episodesResult.right,
		sortDir
	};
};
