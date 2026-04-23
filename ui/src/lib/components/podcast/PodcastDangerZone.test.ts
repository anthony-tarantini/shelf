import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/svelte';
import PodcastDangerZone from './PodcastDangerZone.svelte';
import { api } from '$lib/api/client';

vi.mock('$lib/api/client', () => ({
	api: {
		delete: vi.fn()
	}
}));

vi.mock('$app/navigation', () => ({
	goto: vi.fn()
}));

describe('PodcastDangerZone', () => {
	beforeEach(() => {
		vi.resetAllMocks();
	});

	it('should show confirmation dialog when clicking unsubscribe', async () => {
		render(PodcastDangerZone, { props: { podcastId: 'pod-1' } });
		
		const unsubscribeBtn = screen.getByRole('button', { name: 'Unsubscribe' });
		await fireEvent.click(unsubscribeBtn);
		
		// The dialog title is also "Unsubscribe", but we look for the message
		expect(screen.getAllByText(/Are you sure you want to unsubscribe/)[0]).toBeInTheDocument();
	});

	it('should call delete API on confirm', async () => {
		vi.mocked(api.delete).mockResolvedValueOnce({ right: {} });

		render(PodcastDangerZone, { props: { podcastId: 'pod-1' } });
		
		// Open dialog
		await fireEvent.click(screen.getByRole('button', { name: 'Unsubscribe' }));
		
		// Find confirm button in dialog - it has classes like "bg-destructive"
		// or we can use the fact that it's a button inside the fixed overlay
		const confirmBtn = screen.getAllByRole('button', { name: 'Unsubscribe' }).find(el => el.closest('.fixed'))!;
		await fireEvent.click(confirmBtn);

		await waitFor(() => {
			expect(api.delete).toHaveBeenCalledWith('/podcasts/pod-1');
		});
	});
});
