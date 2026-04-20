import type { Handle } from '@sveltejs/kit';

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';

export const handle: Handle = async ({ event, resolve }) => {
    if (event.url.pathname.startsWith('/api')) {
        const targetUrl = new URL(BACKEND_URL);
        const backendRequestUrl = new URL(event.url.pathname + event.url.search, targetUrl);
        
        const headers = new Headers(event.request.headers);
        
        // Forward essential headers for proxying
        headers.set('host', targetUrl.host);
        headers.set('X-Forwarded-Host', event.url.host);
        headers.set('X-Forwarded-For', event.getClientAddress());
        headers.set('X-Forwarded-Proto', event.url.protocol.replace(':', ''));
        
        try {
            const response = await fetch(backendRequestUrl.toString(), {
                method: event.request.method,
                headers,
                body: event.request.method !== 'GET' && event.request.method !== 'HEAD' 
                    ? event.request.body 
                    : undefined,
                // @ts-expect-error - duplex is needed for streaming bodies in some fetch implementations
                duplex: 'half'
            });

            return response;
        } catch (error) {
            console.error('Proxy error:', error);
            return new Response('Proxy error', { status: 502 });
        }
    }

    return resolve(event);
};
