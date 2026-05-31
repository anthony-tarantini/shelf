import { test as base, type Page } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

export type Fixtures = {
	authenticatedPage: Page;
	adminPage: Page;
	apiHelper: ApiHelper;
};

type LocalStorageItem = { name: string; value: string };
type StorageState = {
	origins?: {
		origin: string;
		localStorage?: LocalStorageItem[];
	}[];
};

type PodcastResponse = { id: string; feedUrl: string };
type SeriesResponse = { id: string; name: string };

function getAuthTokenFromState(): string | null {
	const authFile = path.join(process.cwd(), 'tests', '.auth', 'admin.json');
	if (fs.existsSync(authFile)) {
		const data = JSON.parse(fs.readFileSync(authFile, 'utf-8')) as StorageState;
		const tokenItem = data.origins?.[0]?.localStorage?.find((item) => item.name === 'shelf_token');
		if (tokenItem) return tokenItem.value;
	}
	return null;
}

export class ApiHelper {
	private readonly backendUrl: string;

	constructor() {
		this.backendUrl = process.env.BACKEND_URL || 'http://localhost:8081';
	}

	async getAdminToken(): Promise<string> {
		const token = getAuthTokenFromState();
		if (token) return token;
		throw new Error('Admin token not found');
	}

	async getAdminAuthHeaders(): Promise<Record<string, string>> {
		return {
			'Authorization': `Bearer ${await this.getAdminToken()}`,
			'Content-Type': 'application/json'
		};
	}

	async seedBook() {
		const headers = await this.getAdminAuthHeaders();
		
		// First check if 'Test Book' is already in the library
		const libraryRes = await fetch(`${this.backendUrl}/api/books/page?title=Test%20Book`, { headers });
		if (libraryRes.ok) {
			const libJson = await libraryRes.json();
			if (libJson.data && libJson.data.items && libJson.data.items.length > 0) {
				const match = libJson.data.items.find((i: { book: { title: string } }) => i.book.title === 'Test Book');
				if (match) return { data: match };
			}
		}

		// Start scan
		await fetch(`${this.backendUrl}/api/books/import/scan`, {
			method: 'POST',
			headers,
			body: JSON.stringify({ data: { path: '/import-fixtures/books' } })
		});

		// Poll staged books until we see 'Test Book'
		let stagedId = null;
		for (let i = 0; i < 20; i++) {
			await new Promise(r => setTimeout(r, 500));
			const res = await fetch(`${this.backendUrl}/api/books/staged`, { headers });
			if (res.ok) {
				const json = await res.json();
				if (json.data && json.data.items && json.data.items.length > 0) {
					const match = json.data.items.find((item: { title: string }) => item.title === 'Test Book');
					if (match) {
						stagedId = match.id;
						break;
					}
				}
			}
		}

		if (!stagedId) {
			throw new Error('Failed to find staged Test Book after scan');
		}

		// Promote
		const promoteRes = await fetch(`${this.backendUrl}/api/books/staged/${stagedId}/promote`, {
			method: 'POST',
			headers,
			body: JSON.stringify({ data: {} })
		});

		if (!promoteRes.ok) {
			throw new Error(`Failed to promote book: ${await promoteRes.text()}`);
		}

		return promoteRes.json();
	}

	async seedPodcast() {
		const headers = await this.getAdminAuthHeaders();
		
		// 1. Check if podcast already exists
		const podcastsRes = await fetch(`${this.backendUrl}/api/podcasts`, { headers });
		if (podcastsRes.ok) {
			const podcastsJson = await podcastsRes.json();
			const existing = (podcastsJson.data?.podcasts as PodcastResponse[] | undefined)?.find((p) => 
				p.feedUrl.includes('test-podcast.xml')
			);
			if (existing) return existing;
		}

		// 2. Reuse or create series
		let seriesId = null;
		const seriesRes = await fetch(`${this.backendUrl}/api/series`, { headers });
		if (seriesRes.ok) {
			const seriesJson = await seriesRes.json();
			const existingSeries = (seriesJson.data as SeriesResponse[] | undefined)?.find((s) => s.name === 'Test Podcast');
			if (existingSeries) {
				seriesId = existingSeries.id;
			}
		}

		if (!seriesId) {
			const createSeriesRes = await fetch(`${this.backendUrl}/api/series`, {
				method: 'POST',
				headers,
				body: JSON.stringify({ data: { title: 'Test Podcast' } })
			});
			if (!createSeriesRes.ok) {
				throw new Error(`Failed to create series: ${await createSeriesRes.text()}`);
			}
			const newSeriesJson = await createSeriesRes.json();
			seriesId = newSeriesJson.data.id;
		}

		// 3. Create podcast
		const response = await fetch(`${this.backendUrl}/api/podcasts`, {
			method: 'POST',
			headers,
			body: JSON.stringify({
				data: {
					seriesId,
					feedUrl: 'http://fixtures:80/test-podcast.xml'
				}
			})
		});
		return response.json();
	}

	async createApiToken(name: string) {
		const headers = await this.getAdminAuthHeaders();
		const response = await fetch(`${this.backendUrl}/api/tokens`, {
			method: 'POST',
			headers,
			body: JSON.stringify({ data: { description: name } })
		});
		return response.json();
	}

	async uploadKoreaderStats(statsData: ArrayBuffer) {
		const headers = await this.getAdminAuthHeaders();
		const response = await fetch(`${this.backendUrl}/koreader/users/admin/statistics.sqlite`, {
			method: 'PUT',
			headers: {
				...headers,
				'Content-Type': 'application/octet-stream'
			},
			body: statsData
		});
		return response;
	}

	async opdsRequest(path: string, options?: RequestInit) {
		const headers = {
			'Authorization': `Basic ${Buffer.from(`admin@example.com:adminpassword`).toString('base64')}`,
			...(options?.headers || {})
		};
		return fetch(`${this.backendUrl}${path}`, {
			...options,
			headers
		});
	}
}

export const test = base.extend<Fixtures>({
	apiHelper: async ({}, use) => {
		await use(new ApiHelper());
	},
	adminPage: async ({ page }, use) => {
		const token = getAuthTokenFromState();
		if (token) {
			// Set localStorage for client-side API calls
			await page.addInitScript((t) => {
				window.localStorage.setItem('shelf_token', t);
			}, token);
		}
		await use(page);
	},
	authenticatedPage: async ({ adminPage }, use) => {
		// Currently aliased to adminPage since we only create one user
		await use(adminPage);
	}
});

export { expect } from '@playwright/test';
