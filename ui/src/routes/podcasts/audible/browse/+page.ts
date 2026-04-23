import { api } from '$lib/api/client';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const result = await api.get<any[]>('/podcasts/audible/library', fetch);

	if (result.left) {
		return {
			items: [],
			loadError: result.left.message
		};
	}

	return {
		items: result.right
	};
};
