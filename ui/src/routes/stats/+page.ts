import { statsApi, pastYearRange } from '$lib/api/stats';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch }) => {
	const range = pastYearRange();
	const result = await statsApi.daily(range, fetch);
	return {
		daily: result.right ?? [],
		range: { from: range.from.toISOString(), to: range.to.toISOString() },
		unavailable: !!result.left,
	};
};
