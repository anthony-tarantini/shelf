import { describe, it, expect, vi, afterEach } from 'vitest';
import { formatDuration, getSearchShortcutHint } from './utils';

describe('utils', () => {
    describe('formatDuration', () => {
        it('should format seconds into M:SS', () => {
            expect(formatDuration(65)).toBe('1:05');
        });
        
        it('should format seconds into H:MM:SS', () => {
            expect(formatDuration(3665)).toBe('1:01:05');
        });

        it('should handle undefined', () => {
            expect(formatDuration(undefined)).toBe('');
        });
    });

    describe('getSearchShortcutHint', () => {
        afterEach(() => {
            vi.restoreAllMocks();
        });

        it('should return ⌘K for Mac', () => {
            vi.stubGlobal('navigator', { userAgent: 'Macintosh' });
            expect(getSearchShortcutHint()).toBe('⌘K');
        });

        it('should return Ctrl+K for Windows', () => {
            vi.stubGlobal('navigator', { userAgent: 'Windows NT 10.0' });
            expect(getSearchShortcutHint()).toBe('Ctrl+K');
        });
    });
});
