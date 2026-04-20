import { describe, it, expect, vi, beforeEach } from 'vitest';
import { toast } from './toast.svelte';
import { get } from 'svelte/store';

describe('toast store', () => {
	beforeEach(() => {
		// Reset store before each test
		const current = get(toast);
		current.forEach((item) => toast.dismiss(item.id));
		vi.useFakeTimers();
	});

	it('should push a success toast', () => {
		const message = 'Success message';
		toast.success(message);

		const items = get(toast);
		expect(items).toHaveLength(1);
		expect(items[0].kind).toBe('success');
		expect(items[0].message).toBe(message);
		expect(items[0].id).toBeDefined();
	});

	it('should push an error toast', () => {
		const message = 'Error message';
		toast.error(message);

		const items = get(toast);
		expect(items).toHaveLength(1);
		expect(items[0].kind).toBe('error');
		expect(items[0].message).toBe(message);
	});

	it('should push an info toast', () => {
		const message = 'Info message';
		toast.info(message);

		const items = get(toast);
		expect(items).toHaveLength(1);
		expect(items[0].kind).toBe('info');
	});

	it('should dismiss a toast by id', () => {
		const id = toast.success('Test');
		expect(get(toast)).toHaveLength(1);

		toast.dismiss(id);
		expect(get(toast)).toHaveLength(0);
	});

	it('should auto-dismiss after duration', () => {
		toast.success('Auto dismiss', 1000);
		expect(get(toast)).toHaveLength(1);

		vi.advanceTimersByTime(1000);
		expect(get(toast)).toHaveLength(0);
	});

	it('should not auto-dismiss if duration is 0', () => {
		toast.success('No dismiss', 0);
		expect(get(toast)).toHaveLength(1);

		vi.advanceTimersByTime(10000);
		expect(get(toast)).toHaveLength(1);
	});
});
