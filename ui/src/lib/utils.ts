export function formatDuration(seconds: number | undefined): string {
  if (seconds === undefined) return '';
  const hrs = Math.floor(seconds / 3600);
  const mins = Math.floor((seconds % 3600) / 60);
  const secs = Math.floor(seconds % 60);
  if (hrs > 0) {
    return `${hrs}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  }
  return `${mins}:${secs.toString().padStart(2, '0')}`;
}

export function getSearchShortcutHint(): string {
  if (typeof window === 'undefined') return '⌘K';
  const isMac = /Mac|iPod|iPhone|iPad/.test(navigator.userAgent);
  return isMac ? '⌘K' : 'Ctrl+K';
}

