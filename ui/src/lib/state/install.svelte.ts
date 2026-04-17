const INSTALL_DISMISS_KEY = 'shelf_install_prompt_dismissed_until';
const INSTALL_DISMISS_MS = 1000 * 60 * 60 * 24 * 7;

export type InstallPlatform = 'ios-safari' | 'chromium' | 'unsupported';

class InstallState {
	private platformState = $state<InstallPlatform>('unsupported');
	private canPromptState = $state(false);
	private installedState = $state(false);
	private dismissedState = $state(false);
	private initializedState = $state(false);
	private deferredPrompt: BeforeInstallPromptEvent | null = null;
	private mediaQuery?: MediaQueryList;
	private boundBeforeInstallPrompt = (event: Event) => {
		const promptEvent = event as BeforeInstallPromptEvent;
		promptEvent.preventDefault();
		this.deferredPrompt = promptEvent;
		this.sync();
	};
	private boundAppInstalled = () => {
		this.installedState = true;
		this.deferredPrompt = null;
		this.clearDismissal();
		this.sync();
	};
	private boundDisplayModeChange = () => {
		this.installedState = this.detectStandalone();
		this.sync();
	};

	constructor() {
		if (typeof window !== 'undefined') {
			this.initialize();
		}
	}

	get platform() {
		return this.platformState;
	}

	get canPromptInstall() {
		return this.canPromptState;
	}

	get isInstalled() {
		return this.installedState;
	}

	get isDismissed() {
		return this.dismissedState;
	}

	get isReady() {
		return this.initializedState;
	}

	get shouldShowInstallUi() {
		if (!this.initializedState || this.installedState || this.dismissedState) {
			return false;
		}

		return this.platformState === 'ios-safari' || this.canPromptState;
	}

	initialize() {
		this.platformState = this.detectPlatform();
		this.installedState = this.detectStandalone();
		this.dismissedState = this.readDismissal();
		this.mediaQuery = window.matchMedia('(display-mode: standalone)');
		this.mediaQuery.addEventListener('change', this.boundDisplayModeChange);
		window.addEventListener('beforeinstallprompt', this.boundBeforeInstallPrompt);
		window.addEventListener('appinstalled', this.boundAppInstalled);
		this.initializedState = true;
		this.sync();
	}

	async promptInstall() {
		if (!this.deferredPrompt) return;

		await this.deferredPrompt.prompt();
		const choice = await this.deferredPrompt.userChoice;
		this.deferredPrompt = null;
		if (choice.outcome === 'accepted') {
			this.installedState = true;
			this.clearDismissal();
		} else {
			this.dismiss();
		}
		this.sync();
	}

	dismiss() {
		if (typeof window !== 'undefined') {
			window.localStorage.setItem(INSTALL_DISMISS_KEY, String(Date.now() + INSTALL_DISMISS_MS));
		}
		this.dismissedState = true;
		this.sync();
	}

	resetDismissal() {
		this.clearDismissal();
		this.dismissedState = false;
		this.sync();
	}

	private sync() {
		this.platformState = this.detectPlatform();
		this.installedState = this.detectStandalone();
		this.dismissedState = this.readDismissal();
		this.canPromptState = this.platformState === 'chromium' && this.deferredPrompt !== null && !this.installedState && !this.dismissedState;
	}

	private detectStandalone() {
		if (typeof window === 'undefined') return false;

		const navigatorStandalone = typeof navigator !== 'undefined' && 'standalone' in navigator
			? (navigator as Navigator & { standalone?: boolean }).standalone === true
			: false;
		return window.matchMedia('(display-mode: standalone)').matches || navigatorStandalone;
	}

	private detectPlatform(): InstallPlatform {
		if (typeof navigator === 'undefined') return 'unsupported';

		const userAgent = navigator.userAgent;
		const isIos = /iPad|iPhone|iPod/.test(userAgent);
		const isSafari = /Safari/.test(userAgent) && !/CriOS|FxiOS|EdgiOS|OPiOS/.test(userAgent);
		if (isIos && isSafari) {
			return 'ios-safari';
		}

		if (/Chrome|Chromium|Edg|OPR/.test(userAgent) && !isIos) {
			return 'chromium';
		}

		return 'unsupported';
	}

	private readDismissal() {
		if (typeof window === 'undefined') return false;

		const rawValue = window.localStorage.getItem(INSTALL_DISMISS_KEY);
		if (!rawValue) return false;

		const dismissedUntil = Number(rawValue);
		if (!Number.isFinite(dismissedUntil) || dismissedUntil <= Date.now()) {
			this.clearDismissal();
			return false;
		}

		return true;
	}

	private clearDismissal() {
		if (typeof window !== 'undefined') {
			window.localStorage.removeItem(INSTALL_DISMISS_KEY);
		}
	}
}

export const installState = new InstallState();
