import { test, expect } from './fixtures';
import { createHash } from 'crypto';

const BACKEND = process.env.BACKEND_URL || 'http://localhost:8081';
const USERNAME = 'admin';

function md5Hex(input: string): string {
	return createHash('md5').update(input).digest('hex');
}

async function provisionKoreaderCreds(apiHelper: import('./fixtures').ApiHelper): Promise<string> {
	const tokenResp = await apiHelper.createApiToken(`e2e-koreader-${Date.now()}`);
	const raw = tokenResp.data?.token as string;
	expect(raw, 'API token endpoint should return raw token').toBeTruthy();
	const key = md5Hex(raw);

	const registerRes = await fetch(`${BACKEND}/koreader/sync/users/create`, {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ username: USERNAME, password: key }),
	});
	expect([200, 201]).toContain(registerRes.status);
	return key;
}

function koreaderHeaders(key: string): Record<string, string> {
	return { 'x-auth-user': USERNAME, 'x-auth-key': key };
}

test.describe('KOReader Sync Protocol', () => {
	test('register + auth check succeed with token MD5 credentials', async ({ apiHelper }) => {
		const key = await provisionKoreaderCreds(apiHelper);

		const authRes = await fetch(`${BACKEND}/koreader/sync/users/auth`, {
			headers: koreaderHeaders(key),
		});
		expect(authRes.status).toBe(200);
	});

	test('auth fails with wrong key', async () => {
		const badRes = await fetch(`${BACKEND}/koreader/sync/users/auth`, {
			headers: koreaderHeaders(md5Hex('not-a-real-token')),
		});
		expect(badRes.status).toBe(401);
	});

	test('auth fails with missing headers', async () => {
		const res = await fetch(`${BACKEND}/koreader/sync/users/auth`);
		expect(res.status).toBe(401);
	});

	test('progress update then read returns stored payload', async ({ apiHelper }) => {
		const key = await provisionKoreaderCreds(apiHelper);

		// Need a real edition fileHash; progress update requires a matching edition or returns 404.
		await apiHelper.seedBook();
		const adminHeaders = await apiHelper.getAdminAuthHeaders();
		const bookListRes = await fetch(`${BACKEND}/api/books/page?title=Test%20Book`, { headers: adminHeaders });
		const bookListJson = await bookListRes.json();
		const document = bookListJson.data?.items?.[0]?.metadata?.editions?.[0]?.edition?.fileHash as string;
		expect(document, 'seeded book should expose fileHash').toBeTruthy();
		const payload = {
			document,
			percentage: 0.42,
			progress: '/body/DocFragment[3]/body/div/p[12]',
			device: 'e2e-kindle',
			device_id: 'e2e-device-1',
		};

		const putRes = await fetch(`${BACKEND}/koreader/sync/syncs/progress`, {
			method: 'PUT',
			headers: { 'Content-Type': 'application/json', ...koreaderHeaders(key) },
			body: JSON.stringify(payload),
		});
		expect(putRes.status).toBe(200);

		const getRes = await fetch(`${BACKEND}/koreader/sync/syncs/progress/${document}`, {
			headers: koreaderHeaders(key),
		});
		expect(getRes.status).toBe(200);
		const body = await getRes.json();
		expect(body.document).toBe(document);
		expect(body.percentage).toBeCloseTo(0.42, 5);
		expect(body.progress).toBe(payload.progress);
	});

	test('progress read returns 404 for unknown document', async ({ apiHelper }) => {
		const key = await provisionKoreaderCreds(apiHelper);
		const res = await fetch(`${BACKEND}/koreader/sync/syncs/progress/${'b'.repeat(32)}`, {
			headers: koreaderHeaders(key),
		});
		expect(res.status).toBe(404);
	});

	test('progress update with unknown document hash returns 404', async ({ apiHelper }) => {
		const key = await provisionKoreaderCreds(apiHelper);
		const res = await fetch(`${BACKEND}/koreader/sync/syncs/progress`, {
			method: 'PUT',
			headers: { 'Content-Type': 'application/json', ...koreaderHeaders(key) },
			body: JSON.stringify({ document: 'c'.repeat(32), percentage: 0.1, progress: 'x', device: 'd', device_id: 'i' }),
		});
		expect(res.status).toBe(404);
	});
});

test.describe('KOReader WebDAV (statistics)', () => {
	test('OPTIONS advertises DAV capabilities', async ({ apiHelper }) => {
		const key = await provisionKoreaderCreds(apiHelper);
		const res = await fetch(`${BACKEND}/koreader/webdav/`, {
			method: 'OPTIONS',
			headers: koreaderHeaders(key),
		});
		expect(res.status).toBe(200);
		expect(res.headers.get('dav')).toBeTruthy();
		expect(res.headers.get('allow')).toMatch(/PUT/);
	});

	test('PUT statistics.sqlite stores file and PROPFIND/GET return it', async ({ apiHelper }) => {
		const key = await provisionKoreaderCreds(apiHelper);
		// Minimal SQLite header bytes ("SQLite format 3\0" + 84 zero bytes = 100-byte header)
		const sqliteHeader = Buffer.concat([
			Buffer.from('SQLite format 3\0', 'binary'),
			Buffer.alloc(84),
		]);

		const putRes = await fetch(`${BACKEND}/koreader/webdav/statistics.sqlite`, {
			method: 'PUT',
			headers: {
				'Content-Type': 'application/octet-stream',
				...koreaderHeaders(key),
			},
			body: sqliteHeader,
		});
		expect([200, 201, 204]).toContain(putRes.status);

		const propfindRes = await fetch(`${BACKEND}/koreader/webdav/`, {
			method: 'PROPFIND',
			headers: { Depth: '1', ...koreaderHeaders(key) },
		});
		expect([200, 207]).toContain(propfindRes.status);
		const propfindBody = await propfindRes.text();
		expect(propfindBody).toMatch(/statistics\.sqlite/i);

		const getRes = await fetch(`${BACKEND}/koreader/webdav/statistics.sqlite`, {
			headers: koreaderHeaders(key),
		});
		expect(getRes.status).toBe(200);
	});

	test('PUT without auth headers returns 401', async () => {
		const res = await fetch(`${BACKEND}/koreader/webdav/statistics.sqlite`, {
			method: 'PUT',
			headers: { 'Content-Type': 'application/octet-stream' },
			body: Buffer.from('nope'),
		});
		expect(res.status).toBe(401);
	});
});

test.describe('KOReader Stats API', () => {
	test('books, unmatched, and daily endpoints reachable with admin JWT', async ({ apiHelper }) => {
		const headers = await apiHelper.getAdminAuthHeaders();

		const booksRes = await fetch(`${BACKEND}/api/koreader/stats/books`, { headers });
		expect(booksRes.status).toBe(200);

		const unmatchedRes = await fetch(`${BACKEND}/api/koreader/stats/unmatched`, { headers });
		expect(unmatchedRes.status).toBe(200);

		const from = new Date(Date.now() - 365 * 24 * 60 * 60 * 1000).toISOString();
		const to = new Date().toISOString();
		const dailyRes = await fetch(
			`${BACKEND}/api/koreader/stats/daily?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
			{ headers },
		);
		expect(dailyRes.status).toBe(200);
	});

	test('stats endpoints reject unauthenticated requests', async () => {
		const res = await fetch(`${BACKEND}/api/koreader/stats/books`);
		expect(res.status).toBe(401);
	});
});
