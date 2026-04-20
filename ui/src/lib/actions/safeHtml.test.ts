import { describe, it, expect, vi } from 'vitest';
import { safeHtml } from './safeHtml';
import DOMPurify from 'dompurify';

vi.mock('dompurify', () => ({
	default: {
		sanitize: vi.fn((val) => val) // Default implementation returns value
	}
}));

describe('safeHtml action', () => {
	it('should sanitize and set innerHTML on initialization', () => {
		const node = document.createElement('div');
		const html = '<script>alert("xss")</script><p>Safe</p>';
		
		safeHtml(node, html);
		
		expect(DOMPurify.sanitize).toHaveBeenCalledWith(html);
		expect(node.innerHTML).toBe(html); // because our mock returns same value
	});

	it('should update innerHTML when value changes', () => {
		const node = document.createElement('div');
		const action = safeHtml(node, 'Initial');
		
		action.update('Updated');
		expect(DOMPurify.sanitize).toHaveBeenCalledWith('Updated');
		expect(node.innerHTML).toBe('Updated');
	});

	it('should clear innerHTML on destroy', () => {
		const node = document.createElement('div');
		const action = safeHtml(node, 'Content');
		expect(node.innerHTML).toBe('Content');
		
		action.destroy();
		expect(node.innerHTML).toBe('');
	});
});
