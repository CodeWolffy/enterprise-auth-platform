import { computed, reactive, ref } from 'vue';

/**
 * 分页结果类型
 */
export interface PageResult<T> {
  total: number;
  records: T[];
}

/**
 * CRUD 列表选项
 */
export interface CrudListOptions<T, Q extends Record<string, unknown>> {
  /** 默认查询条件 */
  defaultQuery: Q;
  /** 获取分页数据的方法 */
  fetchPage: (query: Q) => Promise<PageResult<T>>;
  /** 初始页码 */
  initialPage?: number;
  /** 初始分页大小 */
  initialPageSize?: number;
}

/**
 * CRUD 列表通用逻辑封装
 * 
 * 提供标准化的列表管理功能：
 * - 分页数据加载
 * - 搜索和重置
 * - 删除后自动重载
 * - 加载状态管理
 * 
 * @example
 * ```ts
 * const userList = useCrudList({
 *   defaultQuery: { keyword: '', status: null },
 *   fetchPage: async (query) => {
 *     const res = await getUserList(query);
 *     return { total: res.total, records: res.list };
 *   },
 * });
 * 
 * // 加载数据
 * await userList.load();
 * 
 * // 搜索
 * userList.query.keyword = 'test';
 * await userList.search();
 * 
 * // 删除并重载
 * await userList.removeWithReload(() => deleteUser(id));
 * ```
 */
export function useCrudList<T, Q extends Record<string, unknown>>(
  options: CrudListOptions<T, Q>,
) {
  const records = ref<T[]>([]);
  const total = ref(0);
  const loading = ref(false);
  const deleting = ref(false);
  const query = reactive({ ...options.defaultQuery }) as Q;
  const pagination = reactive({
    page: options.initialPage ?? 1,
    size: options.initialPageSize ?? 10,
  });

  /**
   * 完整的分页查询参数（包含查询条件和分页参数）
   */
  const pageQuery = computed(
    () =>
      ({
        ...query,
        page: pagination.page,
        size: pagination.size,
      }) as Q,
  );

  /**
   * 加载数据
   */
  async function load() {
    loading.value = true;
    try {
      const page = await options.fetchPage(pageQuery.value);
      records.value = page.records ?? [];
      total.value = page.total ?? 0;
    } finally {
      loading.value = false;
    }
  }

  /**
   * 搜索（重置到第一页并加载）
   */
  async function search() {
    pagination.page = 1;
    await load();
  }

  /**
   * 重置查询条件并加载第一页
   */
  async function reset() {
    Object.assign(query, options.defaultQuery);
    pagination.page = 1;
    await load();
  }

  /**
   * 删除操作并重新加载
   * 如果删除的是当前页最后一条记录且不是第一页，自动回退到上一页
   * 
   * @param removeAction 删除操作函数
   */
  async function removeWithReload(removeAction: () => Promise<void>) {
    deleting.value = true;
    try {
      await removeAction();
      // 如果删除的是当前页最后一条记录且不是第一页，回退到上一页
      if (records.value.length === 1 && pagination.page > 1) {
        pagination.page -= 1;
      }
      await load();
    } finally {
      deleting.value = false;
    }
  }

  /**
   * 设置页码并加载
   */
  function setPage(page: number) {
    pagination.page = page;
    return load();
  }

  /**
   * 设置分页大小并重置到第一页加载
   */
  function setPageSize(size: number) {
    pagination.size = size;
    pagination.page = 1;
    return load();
  }

  return {
    /** 数据列表 */
    records,
    /** 总记录数 */
    total,
    /** 加载状态 */
    loading,
    /** 删除中状态 */
    deleting,
    /** 查询条件（响应式） */
    query,
    /** 分页参数（响应式） */
    pagination,
    /** 完整查询参数（包含分页） */
    pageQuery,
    /** 加载数据 */
    load,
    /** 搜索 */
    search,
    /** 重置 */
    reset,
    /** 删除并重载 */
    removeWithReload,
    /** 设置页码 */
    setPage,
    /** 设置分页大小 */
    setPageSize,
  };
}