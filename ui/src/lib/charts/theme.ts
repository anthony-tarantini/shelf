export const chartTokens = {
	primary: 'var(--primary)',
	primarySoft: 'color-mix(in srgb, var(--primary) 30%, transparent)',
	accent: 'var(--accent-foreground)',
	muted: 'var(--muted-foreground)',
	border: 'var(--border)',
	foreground: 'var(--foreground)',
} as const;

export function formatMinutes(totalSeconds: number): string {
	const minutes = Math.round(totalSeconds / 60);
	if (minutes < 60) return `${minutes}m`;
	const hours = Math.floor(minutes / 60);
	const rem = minutes % 60;
	return rem === 0 ? `${hours}h` : `${hours}h ${rem}m`;
}

export function formatDate(iso: string | Date): string {
	const d = typeof iso === 'string' ? new Date(iso) : iso;
	return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
}

export function formatTime(iso: string | Date): string {
	const d = typeof iso === 'string' ? new Date(iso) : iso;
	return d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
}
