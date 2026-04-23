import { writable } from 'svelte/store';

export const page = {
    subscribe: writable({
        url: new URL('http://localhost'),
        params: {},
        route: { id: null },
        status: 200,
        error: null,
        data: {},
        form: null
    }).subscribe
};

export const navigating = {
    subscribe: writable(null).subscribe
};

export const updated = {
    subscribe: writable(false).subscribe
};
