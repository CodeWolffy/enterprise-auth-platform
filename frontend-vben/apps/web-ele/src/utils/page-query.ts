import { toInstantIso } from './datetime';

export function normalizePageQuery(query: any = {}) {
  const { dateRange, endTime, from, startTime, to, ...rest } = query;
  const [rangeStart, rangeEnd] = Array.isArray(dateRange) ? dateRange : [];

  return {
    ...rest,
    page: query.page ?? 1,
    size: query.size ?? 10,
    from: toInstantIso(from ?? startTime ?? rangeStart),
    to: toInstantIso(to ?? endTime ?? rangeEnd),
  };
}
