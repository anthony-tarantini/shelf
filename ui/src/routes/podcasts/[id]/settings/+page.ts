import { api } from '$lib/api/client';
import type { PodcastAggregate } from '$lib/types/models';
import { error } from '@sveltejs/kit';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ params, fetch }) => {
	const result = await api.get<PodcastAggregate>(`/podcasts/${params.id}`, fetch);

	if (result.left) {
		throw error(result.left.status || 500, result.left.message);
	}

	return {
		aggregate: result.right
	};
};
