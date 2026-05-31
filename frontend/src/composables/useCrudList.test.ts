import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { useCrudList } from './useCrudList'

describe('useCrudList', () => {
  it('loads records with pagination query', async () => {
    const fetchPage = vi.fn().mockResolvedValue({
      total: 2,
      records: [{ id: 1 }, { id: 2 }],
    })
    const list = useCrudList({
      defaultQuery: { keyword: '' },
      fetchPage,
      initialPageSize: 20,
    })

    await list.load()

    expect(fetchPage).toHaveBeenCalledWith({ keyword: '', page: 1, size: 20 })
    expect(list.total.value).toBe(2)
    expect(list.records.value).toEqual([{ id: 1 }, { id: 2 }])
    expect(list.loading.value).toBe(false)
  })

  it('resets query and reloads from first page', async () => {
    const fetchPage = vi.fn().mockResolvedValue({ total: 0, records: [] })
    const list = useCrudList({
      defaultQuery: { keyword: '' },
      fetchPage,
    })

    list.query.keyword = 'alice'
    list.pagination.page = 3
    await list.reset()

    expect(list.query.keyword).toBe('')
    expect(list.pagination.page).toBe(1)
    expect(fetchPage).toHaveBeenLastCalledWith({ keyword: '', page: 1, size: 10 })
  })

  it('moves back one page when deleting the last row on a non-first page', async () => {
    const fetchPage = vi.fn().mockResolvedValue({ total: 1, records: [{ id: 1 }] })
    const removeAction = vi.fn().mockResolvedValue(undefined)
    const list = useCrudList({
      defaultQuery: { keyword: '' },
      fetchPage,
    })

    await list.load()
    list.pagination.page = 2
    await nextTick()
    await list.removeWithReload(removeAction)

    expect(removeAction).toHaveBeenCalledOnce()
    expect(list.pagination.page).toBe(1)
    expect(list.deleting.value).toBe(false)
  })
})