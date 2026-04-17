/// <reference types="vite-plugin-pwa/info" />
/// <reference types="vite-plugin-pwa/svelte" />

// See https://svelte.dev/docs/kit/types#app.d.ts
// for information about these interfaces
declare global {
	namespace App {
		// interface Error {}
		// interface Locals {}
		// interface PageData {}
		// interface PageState {}
		// interface Platform {}
	}

	interface BeforeInstallPromptEvent extends Event {
		prompt: () => Promise<void>;
		userChoice: Promise<{
			outcome: 'accepted' | 'dismissed';
			platform: string;
		}>;
	}
}

declare module 'virtual:pwa-info' {
	export const pwaInfo: import('vite-plugin-pwa').PwaInfo | undefined;
}

declare module 'virtual:pwa-register/svelte' {
	import type { Writable } from 'svelte/store';
	import type { RegisterSWOptions } from 'vite-plugin-pwa/types';

	export type { RegisterSWOptions };

	export function useRegisterSW(options?: RegisterSWOptions): {
		needRefresh: Writable<boolean>;
		offlineReady: Writable<boolean>;
		updateServiceWorker: (reloadPage?: boolean) => Promise<void>;
	};
}

export {};
