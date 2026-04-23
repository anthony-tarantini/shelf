import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/svelte';
import PodcastConfigSettings from './PodcastConfigSettings.svelte';
import { api } from '$lib/api/client';

vi.mock('$lib/api/client', () => ({
	api: {
		put: vi.fn()
	}
}));

const mockPodcast: any = {
	id: 'pod-1',
	autoFetch: true,
	autoSanitize: false,
	fetchIntervalMinutes: 60
};

describe('PodcastConfigSettings', () => {
	beforeEach(() => {
		vi.resetAllMocks();
	});

	it('should render current settings', () => {
		render(PodcastConfigSettings, { props: { podcast: mockPodcast, onUpdate: vi.fn() } });
		
		const autoFetchCheckbox = screen.getByLabelText(/Auto-fetch episodes/) as HTMLInputElement;
		expect(autoFetchCheckbox.checked).toBe(true);
		
		const intervalInput = screen.getByLabelText(/Fetch interval/) as HTMLInputElement;
		expect(intervalInput.value).toBe('60');
	});

	it('should call update API on save', async () => {
		const onUpdate = vi.fn();
		const updatedPodcast = { ...mockPodcast, fetchIntervalMinutes: 120 };
		vi.mocked(api.put).mockResolvedValueOnce({ right: updatedPodcast });

		render(PodcastConfigSettings, { props: { podcast: mockPodcast, onUpdate } });
		
		const intervalInput = screen.getByLabelText(/Fetch interval/);
		await fireEvent.input(intervalInput, { target: { value: '120' } });
		
		const saveBtn = screen.getByText('Save Settings');
		await fireEvent.click(saveBtn);

		await waitFor(() => {
			expect(api.put).toHaveBeenCalledWith('/podcasts/pod-1', expect.objectContaining({
				fetchIntervalMinutes: 120
			}));
			expect(onUpdate).toHaveBeenCalledWith(updatedPodcast);
			expect(screen.getByText(/Settings saved successfully/)).toBeInTheDocument();
		});
	});
});
