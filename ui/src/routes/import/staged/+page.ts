import { api } from '$lib/api/client';
import type { StagedBookPage } from '$lib/types/models';
import { error } from '@sveltejs/kit';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, url }) => {
	const stagedResult = await api.get<StagedBookPage>(`/books/staged${url.search}`, fetch);

	if (stagedResult.left) {
		throw error(stagedResult.left.status || 500, stagedResult.left.message);
	}

	return {
		stagedPage: stagedResult.right || { items: [], totalCount: 0, page: 0, size: 20 }
	};
};
