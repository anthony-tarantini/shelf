import { statsApi, pastYearRange } from '$lib/api/stats';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ params, fetch }) => {
	const range = pastYearRange();
	const [totals, sessions] = await Promise.all([
		statsApi.totalsForEdition(params.editionId, fetch),
		statsApi.sessionsForEdition(params.editionId, range, fetch),
	]);

	return {
		editionId: params.editionId,
		totals: totals.right ?? null,
		sessions: sessions.right ?? [],
		range: { from: range.from.toISOString(), to: range.to.toISOString() },
		unavailable: !!totals.left,
	};
};
