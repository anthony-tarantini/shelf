import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/svelte';
import PodcastRssSettings from './PodcastRssSettings.svelte';
import { api } from '$lib/api/client';

vi.mock('$lib/api/client', () => ({
	api: {
		post: vi.fn()
	}
}));

// Mock navigator.clipboard
Object.defineProperty(navigator, 'clipboard', {
	value: {
		writeText: vi.fn().mockImplementation(() => Promise.resolve())
	}
});

const mockPodcast: any = {
	id: 'pod-1',
	feedToken: 'token-123'
};

describe('PodcastRssSettings', () => {
	beforeEach(() => {
		vi.resetAllMocks();
	});

	it('should render RSS URL', () => {
		render(PodcastRssSettings, { props: { podcast: mockPodcast, onUpdate: vi.fn() } });
		expect(screen.getByText(/token-123/)).toBeInTheDocument();
	});

	it('should copy RSS URL to clipboard', async () => {
		render(PodcastRssSettings, { props: { podcast: mockPodcast, onUpdate: vi.fn() } });
		const copyBtn = screen.getByText(/Copy RSS URL/);
		await fireEvent.click(copyBtn);
		expect(navigator.clipboard.writeText).toHaveBeenCalledWith(expect.stringContaining('token-123'));
		expect(screen.getByText(/RSS URL copied/)).toBeInTheDocument();
	});

	it('should call rotate token API', async () => {
		const onUpdate = vi.fn();
		const updatedPodcast = { ...mockPodcast, feedToken: 'new-token' };
		vi.mocked(api.post).mockResolvedValueOnce({ right: updatedPodcast });

		render(PodcastRssSettings, { props: { podcast: mockPodcast, onUpdate } });
		const rotateBtn = screen.getByRole('button', { name: 'Rotate Token' });
		await fireEvent.click(rotateBtn);

		await waitFor(() => {
			expect(api.post).toHaveBeenCalledWith('/podcasts/pod-1/rotate-token', {});
			expect(onUpdate).toHaveBeenCalledWith(updatedPodcast);
		});
	});
});
