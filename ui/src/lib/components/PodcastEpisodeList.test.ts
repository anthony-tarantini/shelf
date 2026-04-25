import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import PodcastEpisodeList from './PodcastEpisodeList.svelte';

const mockEpisodes: any[] = [
	{
		id: 'episode-1',
		title: 'Episode One',
		season: 1,
		episode: 1,
		totalTime: 3600,
		publishedAt: '2026-04-20T12:00:00Z',
		coverPath: 'path/1'
	},
	{
		id: 'episode-2',
		title: 'Episode Two',
		season: 1,
		episode: 2,
		totalTime: 1800,
		publishedAt: '2026-04-21T12:00:00Z',
		coverPath: null
	}
];

describe('PodcastEpisodeList', () => {
	it('should render episode titles and numbers', () => {
		render(PodcastEpisodeList, { props: { podcastId: 'podcast-1', episodes: mockEpisodes } });

		expect(screen.getByText('Episode One')).toBeInTheDocument();
		expect(screen.getByText('Episode Two')).toBeInTheDocument();
		expect(screen.getAllByText('1').length).toBeGreaterThan(0);
		expect(screen.getByText('2')).toBeInTheDocument();
	});

	it('should format durations correctly', () => {
		render(PodcastEpisodeList, { props: { podcastId: 'podcast-1', episodes: mockEpisodes } });

		// 3600s = 1:00:00 or 60:00 depending on implementation
		expect(screen.getByText('1:00:00')).toBeInTheDocument();
		// 1800s = 30:00
		expect(screen.getByText('30:00')).toBeInTheDocument();
	});

	it('should render cover placeholders when available', () => {
		render(PodcastEpisodeList, { props: { podcastId: 'podcast-1', episodes: mockEpisodes } });

		// Since AuthenticatedImage starts with a placeholder div
		const links = screen.getAllByRole('link');
		expect(links.length).toBeGreaterThan(0);
	});
});
