import { statsApi, type KoreaderBookTotals, type KoreaderStatBook } from '$lib/api/stats';
import type { PageLoad } from './$types';

export type BookRow = KoreaderStatBook & { totals: KoreaderBookTotals | null };

export const load: PageLoad = async ({ fetch }) => {
	const [booksResult, unmatchedResult] = await Promise.all([
		statsApi.listBooks(fetch),
		statsApi.listUnmatched(fetch),
	]);

	const books = booksResult.right ?? [];
	const unavailable = !!booksResult.left;

	const totalsResults = unavailable
		? []
		: await Promise.all(
				books
					.filter((b) => b.editionId !== null)
					.map((b) =>
						statsApi.totalsForEdition(b.editionId!, fetch).then((r) => [b.id, r] as const),
					),
			);
	const totalsById = new Map<string, KoreaderBookTotals>();
	for (const [id, res] of totalsResults) {
		if (res.right) totalsById.set(id, res.right);
	}

	const rows: BookRow[] = books.map((b) => ({ ...b, totals: totalsById.get(b.id) ?? null }));
	rows.sort((a, b) => (b.totals?.totalDurationSeconds ?? 0) - (a.totals?.totalDurationSeconds ?? 0));

	return {
		rows,
		unmatched: unmatchedResult.right ?? [],
		unavailable,
	};
};
