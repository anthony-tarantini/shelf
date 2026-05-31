import type { Handle } from '@sveltejs/kit';

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';
const DEFAULT_PROXY_TIMEOUT_MS = 15000;

function resolveProxyTimeoutMs(): number {
	const raw = process.env.API_PROXY_TIMEOUT_MS;
	const parsed = raw ? Number.parseInt(raw, 10) : NaN;
	return Number.isFinite(parsed) && parsed > 0 ? parsed : DEFAULT_PROXY_TIMEOUT_MS;
}

export const handle: Handle = async ({ event, resolve }) => {
	if (event.url.pathname.startsWith('/api')) {
		const targetUrl = new URL(BACKEND_URL);
		const backendRequestUrl = new URL(event.url.pathname + event.url.search, targetUrl);
		const timeoutMs = resolveProxyTimeoutMs();

		const headers = new Headers(event.request.headers);

		// Support SSR auth: sanitize the Cookie header and conditionally promote it to Authorization.
		// Always remove shelf_token from the forwarded Cookie header to avoid redundant/unintended token forwarding.
		const cookies = headers.get('cookie');
		if (cookies) {
			const sanitized = cookies
				.split(';')
				.map(c => c.trim())
				.filter(c => !c.startsWith('shelf_token='))
				.join('; ');
			if (sanitized) {
				headers.set('cookie', sanitized);
			} else {
				headers.delete('cookie');
			}
		}

		// Only promote cookie to Authorization if ENABLE_E2E_AUTH is set,
		// to maintain the bearer-token model in production.
		if (process.env.ENABLE_E2E_AUTH === 'true') {
			const token = event.cookies.get('shelf_token');
			if (token && !headers.has('Authorization')) {
				headers.set('Authorization', `Bearer ${token}`);
			}
		}

		// Forward essential headers for proxying
		headers.set('host', targetUrl.host);
		headers.set('X-Forwarded-Host', event.url.host);
		headers.set('X-Forwarded-For', event.getClientAddress());
		headers.set('X-Forwarded-Proto', event.url.protocol.replace(':', ''));

		const controller = new AbortController();
		const timeout = setTimeout(() => controller.abort(), timeoutMs);
		try {
			const response = await fetch(backendRequestUrl.toString(), {
				method: event.request.method,
				headers,
				body:
					event.request.method !== 'GET' && event.request.method !== 'HEAD'
						? event.request.body
						: undefined,
				signal: controller.signal,
				// @ts-expect-error - duplex is needed for streaming bodies in some fetch implementations
				duplex: 'half'
			});

			return response;
		} catch (error) {
			console.error('Proxy error:', error);
			const isAbort = error instanceof DOMException && error.name === 'AbortError';
			if (isAbort) {
				return new Response('Upstream request timed out', { status: 504 });
			}
			return new Response('Proxy error', { status: 502 });
		} finally {
			clearTimeout(timeout);
		}
	}

	return resolve(event);
};
