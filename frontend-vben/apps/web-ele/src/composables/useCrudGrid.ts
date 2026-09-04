import type { VbenFormProps } from '#/adapter/form';
import type { VxeTableGridOptions } from '#/adapter/vxe-table';

import { ElMessage, ElMessageBox } from 'element-plus';

import { useVbenVxeGrid } from '#/adapter/vxe-table';

export type CrudGridSortDirection = 'asc' | 'desc';

export interface CrudGridPaginationParams {
  page: number;
  size: number;
  sortBy?: string;
  sortDirection?: CrudGridSortDirection;
}

export type CrudGridQueryParams<
  TQuery extends object = Record<string, unknown>,
> = CrudGridPaginationParams & Omit<TQuery, keyof CrudGridPaginationParams>;

export interface CrudGridPageResponse<TRow extends object> {
  list?: TRow[];
  records?: TRow[];
  total?: number;
}

export interface CrudGridOptions<
  TRow extends object = Record<string, unknown>,
  TQuery extends object = Record<string, unknown>,
  TId extends number | string = string,
> {
  /** 表单配置项，如搜索表单 schema */
  formOptions?: Partial<VbenFormProps>;
  /** 列配置或生成函数 */
  columns?:
    | (() => VxeTableGridOptions<TRow>['columns'])
    | VxeTableGridOptions<TRow>['columns'];
  /** 查询分页数据接口：入参为过滤参数，返回结构为 { records: T[], total: number } 或标准 PageResult */
  fetchPage: (
    params: CrudGridQueryParams<TQuery>,
  ) => Promise<CrudGridPageResponse<TRow>>;
  /** 单行删除接口（可选） */
  deleteApi?: (id: TId) => Promise<unknown>;
  /** 主键字段，默认 'id' */
  rowKey?: keyof TRow & string;
  /** 默认排序字段 */
  defaultSortBy?: string;
  /** 默认排序方向 */
  defaultSortDirection?: CrudGridSortDirection;
  /** 每页默认数量，默认 10 */
  pageSize?: number;
  /** 删除成功后的提示消息 */
  deleteSuccessMessage?: string;
  /** 删除前确认提示消息，默认 '此操作将删除该项，是否继续?' */
  deleteConfirmMessage?: string;
  /** 扩展 gridOptions */
  gridOptions?: Partial<VxeTableGridOptions<TRow>>;
}

interface CrudGridQueryContext {
  page: {
    currentPage: number;
    pageSize: number;
  };
}

type CrudGridRow<TRow extends object> = Record<string, unknown> & TRow;

/**
 * 通用企业级 CRUD Grid 组合式函数。
 * <p>
 * 归一化封装 vxe-grid 分页代理、搜索表单、排序以及通用删除逻辑。
 * </p>
 */
export function useCrudGrid<
  TRow extends object = Record<string, unknown>,
  TQuery extends object = Record<string, unknown>,
  TId extends number | string = string,
>(options: CrudGridOptions<TRow, TQuery, TId>) {
  const {
    formOptions,
    columns,
    fetchPage,
    deleteApi,
    rowKey = 'id',
    defaultSortBy = 'createdAt',
    defaultSortDirection = 'desc',
    pageSize = 10,
    deleteConfirmMessage = '此操作将删除该项，是否继续?',
    deleteSuccessMessage = '删除成功',
    gridOptions = {},
  } = options;

  const [Grid, gridApi] = useVbenVxeGrid<CrudGridRow<TRow>>({
    formOptions: {
      submitOnChange: false,
      ...formOptions,
    },
    gridOptions: {
      columns: typeof columns === 'function' ? columns() : columns,
      height: 'auto',
      keepSource: true,
      pagerConfig: { enabled: true, pageSize },
      proxyConfig: {
        ajax: {
          query: async (
            { page }: CrudGridQueryContext,
            formValues?: TQuery,
          ) => {
            const queryParams = {
              ...formValues,
              page: page.currentPage,
              size: page.pageSize,
            } as CrudGridQueryParams<TQuery>;
            if (defaultSortBy) {
              queryParams.sortBy = defaultSortBy;
              queryParams.sortDirection = defaultSortDirection;
            }
            const response = await fetchPage(queryParams);
            return {
              list: response?.records ?? response?.list ?? [],
              total: response?.total ?? 0,
            };
          },
        },
      },
      rowConfig: { keyField: rowKey },
      toolbarConfig: {
        refresh: true,
        refreshOptions: { code: 'query' },
        search: true,
        zoom: false,
      },
      ...gridOptions,
    },
  } as Parameters<typeof useVbenVxeGrid<CrudGridRow<TRow>>>[0]);

  function onRefresh() {
    gridApi.query();
  }

  async function onDelete(row: TId | TRow, customConfirmMessage?: string) {
    if (!deleteApi) {
      console.warn('deleteApi is not configured for useCrudGrid');
      return;
    }
    const id =
      typeof row === 'object' && row !== null
        ? ((Reflect.get(row, rowKey) ?? row) as TId)
        : row;
    try {
      await ElMessageBox.confirm(
        customConfirmMessage || deleteConfirmMessage,
        '提示',
        {
          cancelButtonText: '取消',
          confirmButtonText: '确认',
          type: 'warning',
        },
      );
    } catch {
      // 用户取消确认，或关闭了对话框。
      return;
    }

    try {
      await deleteApi(id);
    } catch {
      // 请求客户端负责展示接口错误；删除失败时不提示成功或刷新列表。
      return;
    }

    ElMessage.success(deleteSuccessMessage);
    onRefresh();
  }

  return {
    Grid,
    gridApi,
    onRefresh,
    onDelete,
  };
}
