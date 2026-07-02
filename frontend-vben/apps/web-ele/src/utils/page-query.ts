export function normalizePageQuery(query: any = {}) {
  const { dateRange, endTime, fromEpochMs, startTime, toEpochMs, ...rest } = query;
  const [rangeStart, rangeEnd] = Array.isArray(dateRange) ? dateRange : [];

  return {
    ...rest,
    page: query.page ?? 1,
    size: query.size ?? 10,
    fromEpochMs:
      fromEpochMs ?? startTime ?? (rangeStart ? new Date(rangeStart).getTime() : undefined),
    toEpochMs:
      toEpochMs ?? endTime ?? (rangeEnd ? new Date(rangeEnd).getTime() : undefined),
  };
}
