import { describe, it, expect } from 'vitest';
import { getExternalAuthorNames } from './externalMetadata';

describe('externalMetadata utils', () => {
	it('should extract author names from contributors', () => {
		const metadata = {
			id: '1',
			title: 'Test',
			contributors: [
				{ name: 'Author One', type: 'AUTHOR' },
				{ name: 'Narrator One', type: 'NARRATOR' }
			]
		} as any;

		const authors = getExternalAuthorNames(metadata);
		expect(authors).toEqual(['Author One']);
	});

	it('should include contributors from ebook and audiobook defaults', () => {
		const metadata = {
			id: '1',
			title: 'Test',
			contributors: [{ name: 'Author Root' }],
			defaultEbook: {
				contributors: [{ name: 'Author Ebook' }]
			},
			defaultAudiobook: {
				contributors: [{ name: 'Author Audio' }]
			}
		} as any;

		const authors = getExternalAuthorNames(metadata);
		expect(authors).toContain('Author Root');
		expect(authors).toContain('Author Ebook');
		expect(authors).toContain('Author Audio');
	});

	it('should filter out narrators and translators by default', () => {
		const metadata = {
			id: '1',
			title: 'Test',
			contributors: [
				{ name: 'John Doe', type: 'AUTHOR' },
				{ name: 'Jane Smith', type: 'NARRATOR' },
				{ name: 'Bob Wilson', type: 'TRANSLATOR' }
			]
		} as any;

		const authors = getExternalAuthorNames(metadata);
		expect(authors).toEqual(['John Doe']);
	});

	it('should treat empty type as author', () => {
		const metadata = {
			id: '1',
			title: 'Test',
			contributors: [{ name: 'Anonymous' }]
		} as any;

		const authors = getExternalAuthorNames(metadata);
		expect(authors).toEqual(['Anonymous']);
	});

	it('should return unique names', () => {
		const metadata = {
			id: '1',
			title: 'Test',
			contributors: [
				{ name: 'Duplicate', type: 'AUTHOR' },
				{ name: 'Duplicate', type: 'AUTHOR' }
			]
		} as any;

		const authors = getExternalAuthorNames(metadata);
		expect(authors).toEqual(['Duplicate']);
	});
});
