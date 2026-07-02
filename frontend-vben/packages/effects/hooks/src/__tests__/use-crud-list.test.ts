import { describe, expect, it, vi } from 'vitest';

import { nextTick } from 'vue';

import { useCrudList } from './use-crud-list';

describe('useCrudList', () => {
  it('loads records with pagination query', async () => {
    const fetchPage = vi.fn().mockResolvedValue({
      records: [{ id: 1 }, { id: 2 }],
      total: 2,
    });
    const list = useCrudList({
      defaultQuery: { keyword: '' },
      fetchPage,
      initialPageSize: 20,
    });

    await list.load();

    expect(fetchPage).toHaveBeenCalledWith({ keyword: '', page: 1, size: 20 });
    expect(list.total.value).toBe(2);
    expect(list.records.value).toEqual([{ id: 1 }, { id: 2 }]);
    expect(list.loading.value).toBe(false);
  });

  it('resets query and reloads from first page', async () => {
    const fetchPage = vi.fn().mockResolvedValue({ records: [], total: 0 });
    const list = useCrudList({
      defaultQuery: { keyword: '' },
      fetchPage,
    });

    list.query.keyword = 'alice';
    list.pagination.page = 3;
    await list.reset();

    expect(list.query.keyword).toBe('');
    expect(list.pagination.page).toBe(1);
    expect(fetchPage).toHaveBeenLastCalledWith({
      keyword: '',
      page: 1,
      size: 10,
    });
  });

  it('moves back one page when deleting the last row on a non-first page', async () => {
    const fetchPage = vi.fn().mockResolvedValue({ records: [{ id: 1 }], total: 1 });
    const removeAction = vi.fn().mockResolvedValue(undefined);
    const list = useCrudList({
      defaultQuery: { keyword: '' },
      fetchPage,
    });

    await list.load();
    list.pagination.page = 2;
    await nextTick();
    await list.removeWithReload(removeAction);

    expect(removeAction).toHaveBeenCalledOnce();
    expect(list.pagination.page).toBe(1);
    expect(list.deleting.value).toBe(false);
  });

  it('handles search correctly', async () => {
    const fetchPage = vi.fn().mockResolvedValue({ records: [], total: 0 });
    const list = useCrudList({
      defaultQuery: { keyword: '' },
      fetchPage,
    });

    list.pagination.page = 3;
    list.query.keyword = 'test';
    await list.search();

    expect(list.pagination.page).toBe(1);
    expect(fetchPage).toHaveBeenLastCalledWith({
      keyword: 'test',
      page: 1,
      size: 10,
    });
  });

  it('sets page size correctly', async () => {
    const fetchPage = vi.fn().mockResolvedValue({ records: [], total: 0 });
    const list = useCrudList({
      defaultQuery: { keyword: '' },
      fetchPage,
    });

    list.pagination.page = 3;
    await list.setPageSize(50);

    expect(list.pagination.size).toBe(50);
    expect(list.pagination.page).toBe(1);
    expect(fetchPage).toHaveBeenLastCalledWith({
      keyword: '',
      page: 1,
      size: 50,
    });
  });
});