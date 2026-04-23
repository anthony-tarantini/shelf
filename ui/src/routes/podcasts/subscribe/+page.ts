import { api } from '$lib/api/client';
import type { SeriesRoot } from '$lib/types/models';
import { error } from '@sveltejs/kit';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const result = await api.get<SeriesRoot[]>('/series', fetch);

	if (result.left) {
		throw error(result.left.status || 500, result.left.message);
	}

	return {
		series: result.right || []
	};
};
