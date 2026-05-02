import { api } from './client';
import type { Either, AppError } from './client';

export type KoreaderStatBook = {
	id: string;
	editionId: string | null;
	md5: string | null;
	title: string;
	authors: string | null;
	series: string | null;
	language: string | null;
	pages: number | null;
	matched: boolean;
	firstSeenAt: string;
	lastIngestedAt: string;
};

export type KoreaderSession = {
	id: string;
	startedAt: string;
	endedAt: string;
	durationSeconds: number;
	pagesRead: number;
	firstPage: number;
	lastPage: number;
};

export type KoreaderBookTotals = {
	bookSurrogateId: string;
	editionId: string | null;
	sessionCount: number;
	totalDurationSeconds: number;
	totalPagesRead: number;
	firstSessionAt: string | null;
	lastSessionAt: string | null;
};

export type KoreaderDailyAggregate = {
	day: string;
	sessionCount: number;
	bookCount: number;
	totalDurationSeconds: number;
	totalPagesRead: number;
};

export type DateRange = { from: Date; to: Date };

function rangeQuery(range: DateRange): string {
	return `?from=${encodeURIComponent(range.from.toISOString())}&to=${encodeURIComponent(range.to.toISOString())}`;
}

export const statsApi = {
	listBooks(fetchFn: typeof fetch = fetch): Promise<Either<AppError, KoreaderStatBook[]>> {
		return api.get<KoreaderStatBook[]>('/koreader/stats/books', fetchFn);
	},
	listUnmatched(fetchFn: typeof fetch = fetch): Promise<Either<AppError, KoreaderStatBook[]>> {
		return api.get<KoreaderStatBook[]>('/koreader/stats/unmatched', fetchFn);
	},
	sessionsForEdition(
		editionId: string,
		range: DateRange,
		fetchFn: typeof fetch = fetch,
	): Promise<Either<AppError, KoreaderSession[]>> {
		return api.get<KoreaderSession[]>(
			`/koreader/stats/books/${editionId}/sessions${rangeQuery(range)}`,
			fetchFn,
		);
	},
	totalsForEdition(
		editionId: string,
		fetchFn: typeof fetch = fetch,
	): Promise<Either<AppError, KoreaderBookTotals>> {
		return api.get<KoreaderBookTotals>(`/koreader/stats/books/${editionId}/totals`, fetchFn);
	},
	daily(
		range: DateRange,
		fetchFn: typeof fetch = fetch,
	): Promise<Either<AppError, KoreaderDailyAggregate[]>> {
		return api.get<KoreaderDailyAggregate[]>(`/koreader/stats/daily${rangeQuery(range)}`, fetchFn);
	},
};

export function pastYearRange(now: Date = new Date()): DateRange {
	const to = now;
	const from = new Date(now);
	from.setFullYear(from.getFullYear() - 1);
	return { from, to };
}
