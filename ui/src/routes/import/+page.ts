import { api } from '$lib/api/client';
import type { LibationScanStatus } from '$lib/types/models';
import { error } from '@sveltejs/kit';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const libationResult = await api.get<LibationScanStatus>('/import/libation/status', fetch);
	if (libationResult.left) {
		throw error(libationResult.left.status || 500, libationResult.left.message);
	}

	return { libation: libationResult.right };
};
