import { setCurrentTimezone } from '@vben/utils';

import { afterEach, describe, expect, it } from 'vitest';

import { formatDateTime, getClientTimeZone, toInstantIso } from '../datetime';

describe('datetime timezone alignment', () => {
  afterEach(() => {
    setCurrentTimezone('Asia/Shanghai');
  });

  it('uses the Vben configured timezone for display', () => {
    setCurrentTimezone('Asia/Shanghai');
    expect(formatDateTime('2026-01-01T00:00:00Z')).toBe('2026-01-01 08:00:00');

    setCurrentTimezone('America/New_York');
    expect(formatDateTime('2026-01-01T00:00:00Z')).toBe('2025-12-31 19:00:00');
  });

  it('exposes the Vben configured timezone for API headers', () => {
    setCurrentTimezone('America/New_York');

    expect(getClientTimeZone()).toBe('America/New_York');
  });

  it('converts timezone-less calendar input with the configured timezone', () => {
    setCurrentTimezone('Asia/Shanghai');
    expect(toInstantIso('2026-01-01 00:00:00')).toBe(
      '2025-12-31T16:00:00.000Z',
    );

    setCurrentTimezone('America/New_York');
    expect(toInstantIso('2026-01-01 00:00:00')).toBe(
      '2026-01-01T05:00:00.000Z',
    );
  });

  it('treats date picker Date values as calendar input in the configured timezone', () => {
    setCurrentTimezone('America/New_York');

    expect(toInstantIso(new Date(2026, 0, 1, 0, 0, 0))).toBe(
      '2026-01-01T05:00:00.000Z',
    );
  });
});
