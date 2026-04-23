import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import PodcastInfoSidebar from './PodcastInfoSidebar.svelte';
import { CredentialStatus } from '$lib/types/models';

const mockAggregate: any = {
	podcast: {
		id: 'pod-1',
		feedUrl: 'https://example.com/rss',
		autoFetch: true,
		autoSanitize: true,
		fetchIntervalMinutes: 60,
		lastFetchedAt: '2026-04-22T10:00:00Z'
	},
	seriesTitle: 'The Great Podcast',
	episodes: [],
	credential: CredentialStatus.NO_CREDENTIAL
};

describe('PodcastInfoSidebar', () => {
	it('should render podcast info', () => {
		render(PodcastInfoSidebar, { props: { aggregate: mockAggregate } });

		expect(screen.getByText('The Great Podcast')).toBeInTheDocument();
		expect(screen.getByText('https://example.com/rss')).toBeInTheDocument();
		expect(screen.getByText(/Auto-fetch enabled/)).toBeInTheDocument();
		expect(screen.getByText(/Auto-sanitize enabled/)).toBeInTheDocument();
	});

	it('should show fetch interval', () => {
		render(PodcastInfoSidebar, { props: { aggregate: mockAggregate } });
		expect(screen.getByText(/Check every 60 minutes/)).toBeInTheDocument();
	});
});
