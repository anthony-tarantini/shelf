import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vitest/config';
import tailwindcss from '@tailwindcss/vite';
import { SvelteKitPWA } from '@vite-pwa/sveltekit';

const BACKEND_URL = process.env.BACKEND_URL || 'http://localhost:8080';

export default defineConfig({
	plugins: [
		tailwindcss(), 
		sveltekit(),
		SvelteKitPWA({
			registerType: 'autoUpdate',
			workbox: {
				globPatterns: ['client/**/*.{js,css,ico,png,svg,webp,webmanifest}'],
				modifyURLPrefix: {}
			},
			manifest: {
				id: '/',
				name: 'Shelf',
				short_name: 'Shelf',
				description: 'Personal Digital Library Manager',
				theme_color: '#ea76cb',
				background_color: '#111827',
				display: 'standalone',
				start_url: '/',
				scope: '/',
				categories: ['books', 'productivity', 'utilities'],
				icons: [
					{
						src: 'pwa-maskable.svg',
						sizes: '512x512',
						type: 'image/svg+xml'
					},
					{
						src: 'pwa-192x192.png',
						sizes: '192x192',
						type: 'image/png'
					},
					{
						src: 'pwa-512x512.png',
						sizes: '512x512',
						type: 'image/png'
					},
					{
						src: 'pwa-maskable.svg',
						sizes: '512x512',
						type: 'image/svg+xml',
						purpose: 'maskable'
					}
				]
			}
		})
	],
	server: {
		watch: {
			usePolling: true,
		},
		proxy: {
			'/api': {
				target: BACKEND_URL,
				changeOrigin: true,
			}
		}
	},
	test: {
		include: ['src/**/*.{test,spec}.{js,ts}'],
		environment: 'jsdom',
		setupFiles: ['./vitest-setup.ts'],
		alias: {
			'$app': '/src/lib/mocks/app',
		}
	},
	resolve: {
		conditions: ['browser', 'development']
	}
});
