import type { CrudGridPageResponse, CrudGridQueryParams } from './useCrudGrid';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { useCrudGrid } from './useCrudGrid';

const mocks = vi.hoisted(() => ({
  confirm: vi.fn(),
  gridQuery: vi.fn(),
  messageSuccess: vi.fn(),
  useVbenVxeGrid: vi.fn(),
}));

vi.mock('#/adapter/vxe-table', () => ({
  useVbenVxeGrid: mocks.useVbenVxeGrid,
}));

vi.mock('element-plus', () => ({
  ElMessage: {
    success: mocks.messageSuccess,
  },
  ElMessageBox: {
    confirm: mocks.confirm,
  },
}));

interface TestRow {
  id: number;
  name: string;
}

interface TestFilter {
  enabled?: boolean;
  keyword?: string;
}

type TestFetchPage = (
  params: CrudGridQueryParams<TestFilter>,
) => Promise<CrudGridPageResponse<TestRow>>;

type CapturedQuery = (
  context: {
    page: {
      currentPage: number;
      pageSize: number;
    };
  },
  formValues?: TestFilter,
) => Promise<{ list: TestRow[]; total: number }>;

interface CapturedGridOptions {
  gridOptions?: {
    proxyConfig?: {
      ajax?: {
        query?: CapturedQuery;
      };
    };
  };
}

function getCapturedQuery(): CapturedQuery {
  const options = mocks.useVbenVxeGrid.mock
    .calls[0]?.[0] as CapturedGridOptions;
  const query = options.gridOptions?.proxyConfig?.ajax?.query;
  if (!query) {
    throw new Error('Expected useCrudGrid to configure a query callback');
  }
  return query;
}

function setupGrid(
  fetchPage: TestFetchPage,
  deleteApi?: (id: number) => Promise<void>,
) {
  return useCrudGrid<TestRow, TestFilter, number>({
    columns: [],
    deleteApi,
    fetchPage,
  });
}

describe('useCrudGrid', () => {
  beforeEach(() => {
    mocks.confirm.mockResolvedValue(undefined);
    mocks.gridQuery.mockReset();
    mocks.messageSuccess.mockReset();
    mocks.useVbenVxeGrid.mockReset();
    mocks.useVbenVxeGrid.mockReturnValue([
      {},
      {
        query: mocks.gridQuery,
      },
    ]);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('passes filters, pagination, and default sorting to fetchPage', async () => {
    const fetchPage = vi
      .fn<TestFetchPage>()
      .mockResolvedValue({ records: [], total: 0 });
    setupGrid(fetchPage);

    await getCapturedQuery()(
      { page: { currentPage: 3, pageSize: 25 } },
      { enabled: true, keyword: 'alice' },
    );

    expect(fetchPage).toHaveBeenCalledWith({
      enabled: true,
      keyword: 'alice',
      page: 3,
      size: 25,
      sortBy: 'createdAt',
      sortDirection: 'desc',
    });
  });

  it('normalizes records and list responses for the grid', async () => {
    const rows = [{ id: 7, name: 'Alice' }];
    const fetchPage = vi
      .fn<TestFetchPage>()
      .mockResolvedValueOnce({ records: rows, total: 4 })
      .mockResolvedValueOnce({ list: rows, total: 1 });
    setupGrid(fetchPage);
    const query = getCapturedQuery();
    const context = { page: { currentPage: 1, pageSize: 10 } };

    await expect(query(context)).resolves.toEqual({ list: rows, total: 4 });
    await expect(query(context)).resolves.toEqual({ list: rows, total: 1 });
  });

  it('deletes the row and refreshes after confirmation succeeds', async () => {
    const fetchPage = vi
      .fn<TestFetchPage>()
      .mockResolvedValue({ records: [], total: 0 });
    const deleteApi = vi
      .fn<(id: number) => Promise<void>>()
      .mockResolvedValue();
    const { onDelete } = setupGrid(fetchPage, deleteApi);

    await onDelete({ id: 42, name: 'Alice' });

    expect(deleteApi).toHaveBeenCalledWith(42);
    expect(mocks.messageSuccess).toHaveBeenCalledWith('删除成功');
    expect(mocks.gridQuery).toHaveBeenCalledTimes(1);
  });

  it('does not report success or refresh when deletion fails', async () => {
    const fetchPage = vi
      .fn<TestFetchPage>()
      .mockResolvedValue({ records: [], total: 0 });
    const deleteApi = vi
      .fn<(id: number) => Promise<void>>()
      .mockRejectedValue(new Error('delete failed'));
    const { onDelete } = setupGrid(fetchPage, deleteApi);

    await onDelete({ id: 42, name: 'Alice' });

    expect(deleteApi).toHaveBeenCalledWith(42);
    expect(mocks.messageSuccess).not.toHaveBeenCalled();
    expect(mocks.gridQuery).not.toHaveBeenCalled();
  });

  it('does not delete or refresh when confirmation is cancelled', async () => {
    mocks.confirm.mockRejectedValue(new Error('cancelled'));
    const fetchPage = vi
      .fn<TestFetchPage>()
      .mockResolvedValue({ records: [], total: 0 });
    const deleteApi = vi
      .fn<(id: number) => Promise<void>>()
      .mockResolvedValue();
    const { onDelete } = setupGrid(fetchPage, deleteApi);

    await onDelete({ id: 42, name: 'Alice' });

    expect(deleteApi).not.toHaveBeenCalled();
    expect(mocks.messageSuccess).not.toHaveBeenCalled();
    expect(mocks.gridQuery).not.toHaveBeenCalled();
  });
});
