import { api } from '$lib/api/client';
import type { AuthorAggregate, AuthorRoot, BookSummary } from '$lib/types/models';
import { error } from '@sveltejs/kit';
import type { PageLoad } from './$types';

export const load: PageLoad = async ({ fetch, params }) => {
	const [authorResult, booksResult] = await Promise.all([
		api.get<AuthorRoot>(`/authors/${params.id}`, fetch),
		api.get<BookSummary[]>(`/authors/${params.id}/books`, fetch)
	]);

	if (authorResult.left) {
		throw error(authorResult.left.status || 500, authorResult.left.message);
	}
	
	if (booksResult.left) {
		throw error(booksResult.left.status || 500, booksResult.left.message);
	}

	const rawAuthor = authorResult.right;
	const author =
		rawAuthor && 'author' in rawAuthor ? (rawAuthor.author as AuthorRoot) : (rawAuthor as AuthorRoot);

	const details: AuthorAggregate = {
		author,
		books: booksResult.right || []
	};

	return {
		details
	};
};
