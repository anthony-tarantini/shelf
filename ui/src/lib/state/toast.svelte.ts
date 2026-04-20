import { writable } from 'svelte/store';

export type ToastKind = 'success' | 'error' | 'info';

export interface ToastItem {
	id: string;
	kind: ToastKind;
	message: string;
}

function createToastStore() {
	const { subscribe, update } = writable<ToastItem[]>([]);

	function push(kind: ToastKind, message: string, duration = 4000) {
		const id = crypto.randomUUID();
		update((items) => [...items, { id, kind, message }]);
		if (duration > 0 && typeof window !== 'undefined') {
			window.setTimeout(() => dismiss(id), duration);
		}
		return id;
	}

	function dismiss(id: string) {
		update((items) => items.filter((item) => item.id !== id));
	}

	return {
		subscribe,
		dismiss,
		push,
		success: (message: string, duration?: number) => push('success', message, duration),
		error: (message: string, duration?: number) => push('error', message, duration),
		info: (message: string, duration?: number) => push('info', message, duration)
	};
}

export const toast = createToastStore();
