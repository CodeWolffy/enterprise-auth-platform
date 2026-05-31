import { computed, reactive, ref } from 'vue'

type PageResult<T> = {
  total: number
  records: T[]
}

type CrudListOptions<T, Q extends Record<string, unknown>> = {
  defaultQuery: Q
  fetchPage: (query: Q) => Promise<PageResult<T>>
  initialPage?: number
  initialPageSize?: number
}

export function useCrudList<T, Q extends Record<string, unknown>>(options: CrudListOptions<T, Q>) {
  const records = ref<T[]>([])
  const total = ref(0)
  const loading = ref(false)
  const deleting = ref(false)
  const query = reactive({ ...options.defaultQuery }) as Q
  const pagination = reactive({
    page: options.initialPage ?? 1,
    size: options.initialPageSize ?? 10,
  })

  const pageQuery = computed(() => ({
    ...query,
    page: pagination.page,
    size: pagination.size,
  }) as Q)

  async function load() {
    loading.value = true
    try {
      const page = await options.fetchPage(pageQuery.value)
      records.value = page.records ?? []
      total.value = page.total ?? 0
    } finally {
      loading.value = false
    }
  }

  async function search() {
    pagination.page = 1
    await load()
  }

  async function reset() {
    Object.assign(query, options.defaultQuery)
    pagination.page = 1
    await load()
  }

  async function removeWithReload(removeAction: () => Promise<void>) {
    deleting.value = true
    try {
      await removeAction()
      if (records.value.length === 1 && pagination.page > 1) {
        pagination.page -= 1
      }
      await load()
    } finally {
      deleting.value = false
    }
  }

  function setPage(page: number) {
    pagination.page = page
    return load()
  }

  function setPageSize(size: number) {
    pagination.size = size
    pagination.page = 1
    return load()
  }

  return {
    records,
    total,
    loading,
    deleting,
    query,
    pagination,
    pageQuery,
    load,
    search,
    reset,
    removeWithReload,
    setPage,
    setPageSize,
  }
}