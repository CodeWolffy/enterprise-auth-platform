import type { BuiltinThemeType, TimezoneOption } from '@vben-core/typings';

interface BuiltinThemePreset {
  color: string;
  darkPrimaryColor?: string;
  primaryColor?: string;
  type: BuiltinThemeType;
}

const BUILT_IN_THEME_PRESETS: BuiltinThemePreset[] = [
  {
    color: 'hsl(212 100% 45%)',
    type: 'default',
  },
  {
    color: 'hsl(245 82% 67%)',
    type: 'violet',
  },
  {
    color: 'hsl(347 77% 60%)',
    type: 'pink',
  },
  {
    color: 'hsl(42 84% 61%)',
    type: 'yellow',
  },
  {
    color: 'hsl(231 98% 65%)',
    type: 'sky-blue',
  },
  {
    color: 'hsl(161 90% 43%)',
    type: 'green',
  },
  {
    color: 'hsl(240 5% 26%)',
    darkPrimaryColor: 'hsl(0 0% 98%)',
    primaryColor: 'hsl(240 5.9% 10%)',
    type: 'zinc',
  },
  {
    color: 'hsl(181 84% 32%)',
    type: 'deep-green',
  },
  {
    color: 'hsl(211 91% 39%)',
    type: 'deep-blue',
  },
  {
    color: 'hsl(18 89% 40%)',
    type: 'orange',
  },
  {
    color: 'hsl(0 75% 42%)',
    type: 'rose',
  },
  {
    color: 'hsl(0 0% 25%)',
    darkPrimaryColor: 'hsl(0 0% 98%)',
    primaryColor: 'hsl(240 5.9% 10%)',
    type: 'neutral',
  },
  {
    color: 'hsl(215 25% 27%)',
    darkPrimaryColor: 'hsl(0 0% 98%)',
    primaryColor: 'hsl(240 5.9% 10%)',
    type: 'slate',
  },
  {
    color: 'hsl(217 19% 27%)',
    darkPrimaryColor: 'hsl(0 0% 98%)',
    primaryColor: 'hsl(240 5.9% 10%)',
    type: 'gray',
  },
  {
    color: '',
    type: 'custom',
  },
];

/**
 * 时区选项
 */
const TIME_ZONE_DEFINITIONS = [
  {
    offset: 0,
    timezone: 'UTC',
  },
  {
    offset: -8,
    timezone: 'America/Los_Angeles',
  },
  {
    offset: -6,
    timezone: 'America/Chicago',
  },
  {
    offset: -5,
    timezone: 'America/New_York',
  },
  {
    offset: -3,
    timezone: 'America/Sao_Paulo',
  },
  {
    offset: 0,
    timezone: 'Europe/London',
  },
  {
    offset: 1,
    timezone: 'Europe/Berlin',
  },
  {
    offset: 1,
    timezone: 'Europe/Paris',
  },
  {
    offset: 4,
    timezone: 'Asia/Dubai',
  },
  {
    offset: 5.5,
    timezone: 'Asia/Kolkata',
  },
  {
    offset: 7,
    timezone: 'Asia/Bangkok',
  },
  {
    offset: 8,
    timezone: 'Asia/Singapore',
  },
  {
    offset: 8,
    timezone: 'Asia/Shanghai',
  },
  {
    offset: 8,
    timezone: 'Asia/Hong_Kong',
  },
  {
    offset: 9,
    timezone: 'Asia/Tokyo',
  },
  {
    offset: 9,
    timezone: 'Asia/Seoul',
  },
  {
    offset: 10,
    timezone: 'Australia/Sydney',
  },
];

function resolveCurrentOffsetLabel(timezone: string) {
  try {
    const offsetPart = new Intl.DateTimeFormat('en-US', {
      timeZone: timezone,
      timeZoneName: 'shortOffset',
    })
      .formatToParts(new Date())
      .find((part) => part.type === 'timeZoneName')?.value;

    return offsetPart?.replace('GMT', 'UTC') || 'UTC';
  } catch {
    return 'UTC';
  }
}

const DEFAULT_TIME_ZONE_OPTIONS: TimezoneOption[] = TIME_ZONE_DEFINITIONS.map(
  (item) => ({
    ...item,
    label: `${item.timezone} (${resolveCurrentOffsetLabel(item.timezone)})`,
  }),
);

export const COLOR_PRESETS = [...BUILT_IN_THEME_PRESETS].slice(0, 7);

export { BUILT_IN_THEME_PRESETS, DEFAULT_TIME_ZONE_OPTIONS };

export type { BuiltinThemePreset };
