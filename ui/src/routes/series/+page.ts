import { api } from '$lib/api/client';
import type { SeriesPage } from '$lib/types/models';
import { error } from '@sveltejs/kit';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const result = await api.get<SeriesPage>('/series/page', fetch);

	if (result.left) {
		throw error(result.left.status || 500, result.left.message);
	}

	return {
		seriesPage: result.right || { items: [], totalCount: 0, page: 0, size: 20 }
	};
};
