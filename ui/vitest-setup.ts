import '@testing-library/jest-dom/vitest';
import { vi, afterEach, beforeAll } from 'vitest';
import { cleanup } from '@testing-library/svelte';
import { loadTranslations, locale } from '$lib/i18n';

afterEach(() => {
    cleanup();
});

beforeAll(async () => {
    await loadTranslations('en', '/');
    locale.set('en');
});

const localStorageMock = (() => {
    const store = new Map<string, string>();
    return {
        getItem: vi.fn((key: string) => store.get(key) ?? null),
        setItem: vi.fn((key: string, value: string) => {
            store.set(key, value);
        }),
        removeItem: vi.fn((key: string) => {
            store.delete(key);
        }),
        clear: vi.fn(() => {
            store.clear();
        })
    };
})();

Object.defineProperty(globalThis, 'localStorage', {
    value: localStorageMock,
    configurable: true
});

// Mock SvelteKit environment
vi.mock('$app/environment', () => ({
    browser: true,
    dev: true,
    building: false,
    version: 'test'
}));
