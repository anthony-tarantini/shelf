import { api } from '$lib/api/client';
import { error } from '@sveltejs/kit';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const result = await api.get<any[]>('/podcasts/audible/library', fetch);

	if (result.left) {
		// If 401/403, we might want to redirect to connect, but let's handle in svelte
		return {
			items: [],
			loadError: result.left.message
		};
	}

	return {
		items: result.right
	};
};
