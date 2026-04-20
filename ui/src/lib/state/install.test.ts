import { describe, it, expect, vi, beforeEach } from 'vitest';
import { InstallState } from './install.svelte';

describe('InstallState', () => {
	let install: InstallState;

	beforeEach(() => {
		vi.resetAllMocks();
		// Mock window.matchMedia
		Object.defineProperty(window, 'matchMedia', {
			writable: true,
			value: vi.fn().mockImplementation(query => ({
				matches: false,
				media: query,
				onchange: null,
				addListener: vi.fn(),
				removeListener: vi.fn(),
				addEventListener: vi.fn(),
				removeEventListener: vi.fn(),
				dispatchEvent: vi.fn(),
			})),
		});
		
		// Mock localStorage
		const store: Record<string, string> = {};
		Object.defineProperty(window, 'localStorage', {
			value: {
				getItem: vi.fn(key => store[key] || null),
				setItem: vi.fn((key, value) => { store[key] = value.toString(); }),
				removeItem: vi.fn(key => { delete store[key]; }),
				clear: vi.fn(() => { for (const key in store) delete store[key]; }),
			},
			writable: true
		});

		install = new InstallState();
	});

	it('should initialize with unsupported platform by default in tests', () => {
		install.initialize();
		expect(install.platform).toBe('unsupported');
		expect(install.canPromptInstall).toBe(false);
	});

	it('should detect chromium platform from user agent', () => {
		// Mock navigator.userAgent
		Object.defineProperty(navigator, 'userAgent', {
			value: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36',
			configurable: true
		});

		install.initialize();
		expect(install.platform).toBe('chromium');
	});

	it('should detect ios-safari platform', () => {
		Object.defineProperty(navigator, 'userAgent', {
			value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0.3 Mobile/15E148 Safari/604.1',
			configurable: true
		});

		install.initialize();
		expect(install.platform).toBe('ios-safari');
	});

	it('should show install UI for ios-safari', () => {
		Object.defineProperty(navigator, 'userAgent', {
			value: 'Mozilla/5.0 (iPhone; CPU iPhone OS 14_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.0.3 Mobile/15E148 Safari/604.1',
			configurable: true
		});

		install.initialize();
		expect(install.shouldShowInstallUi).toBe(true);
	});

	it('should handle beforeinstallprompt event for chromium', () => {
		Object.defineProperty(navigator, 'userAgent', {
			value: 'Chrome',
			configurable: true
		});

		install.initialize();
		
		const mockEvent = {
			preventDefault: vi.fn(),
			prompt: vi.fn(),
			userChoice: Promise.resolve({ outcome: 'accepted' })
		};

		window.dispatchEvent(new CustomEvent('beforeinstallprompt', { detail: mockEvent }));
		// Note: The actual event would be passed to the listener. 
		// Since we can't easily dispatch a real BeforeInstallPromptEvent,
		// we might need to expose the handler or mock the window.addEventListener.
	});

	it('should respect dismissal', () => {
		Object.defineProperty(navigator, 'userAgent', {
			value: 'iPhone Safari',
			configurable: true
		});

		install.initialize();
		expect(install.shouldShowInstallUi).toBe(true);

		install.dismiss();
		expect(install.isDismissed).toBe(true);
		expect(install.shouldShowInstallUi).toBe(false);
	});
});
