import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/svelte';
import PwaInstallPrompt from './PwaInstallPrompt.svelte';

const { installStateMock } = vi.hoisted(() => ({
	installStateMock: {
		platform: 'chromium' as 'chromium' | 'ios-safari' | 'unsupported',
		shouldShowInstallUi: true,
		promptInstall: vi.fn(async () => {}),
		dismiss: vi.fn()
	}
}));

vi.mock('$lib/state/install.svelte', () => ({
	installState: installStateMock
}));

describe('PwaInstallPrompt', () => {
	beforeEach(() => {
		installStateMock.platform = 'chromium';
		installStateMock.shouldShowInstallUi = true;
		installStateMock.promptInstall.mockClear();
		installStateMock.dismiss.mockClear();
	});

	it('renders Chromium install actions', async () => {
		render(PwaInstallPrompt);

		expect(screen.getByText('Keep Shelf close at hand')).toBeInTheDocument();
		expect(screen.getByRole('button', { name: 'Install app' })).toBeInTheDocument();

		await fireEvent.click(screen.getByRole('button', { name: 'Install app' }));
		expect(installStateMock.promptInstall).toHaveBeenCalledTimes(1);
	});

	it('renders iOS instructions without install action', () => {
		installStateMock.platform = 'ios-safari';

		render(PwaInstallPrompt);

		expect(screen.getByText('Use Share, then choose Add to Home Screen.')).toBeInTheDocument();
		expect(screen.queryByRole('button', { name: 'Install app' })).not.toBeInTheDocument();
	});

	it('hides itself when install UI should not show', () => {
		installStateMock.shouldShowInstallUi = false;

		render(PwaInstallPrompt);

		expect(screen.queryByText('Keep Shelf close at hand')).not.toBeInTheDocument();
	});

	it('dismisses when close button is clicked', async () => {
		render(PwaInstallPrompt);

		await fireEvent.click(screen.getByLabelText('Dismiss notification'));
		expect(installStateMock.dismiss).toHaveBeenCalledTimes(1);
	});
});
