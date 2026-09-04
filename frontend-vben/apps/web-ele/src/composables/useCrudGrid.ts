import type { VxeTableGridOptions } from '#/adapter/vxe-table';
import type { VbenFormProps } from '#/adapter/form';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { ElMessage, ElMessageBox } from 'element-plus';

export interface CrudGridOptions {
  /** 表单配置项，如搜索表单 schema */
  formOptions?: Partial<VbenFormProps>;
  /** 列配置或生成函数 */
  columns?: VxeTableGridOptions['columns'] | (() => VxeTableGridOptions['columns']);
  /** 查询分页数据接口：入参为过滤参数，返回结构为 { records: T[], total: number } 或标准 PageResult */
  fetchPage: (params: any) => Promise<any>;
  /** 单行删除接口（可选） */
  deleteApi?: (id: any) => Promise<any>;
  /** 主键字段，默认 'id' */
  rowKey?: string;
  /** 默认排序字段 */
  defaultSortBy?: string;
  /** 默认排序方向 */
  defaultSortDirection?: 'asc' | 'desc';
  /** 每页默认数量，默认 10 */
  pageSize?: number;
  /** 删除前确认提示消息，默认 '此操作将永久删除该项，是否继续?' */
  deleteConfirmMessage?: string;
  /** 扩展 gridOptions */
  gridOptions?: Partial<VxeTableGridOptions>;
}

/**
 * 通用企业级 CRUD Grid 组合式函数。
 * <p>
 * 归一化封装 vxe-grid 分页代理、搜索表单、排序以及通用删除逻辑。
 * </p>
 */
export function useCrudGrid(options: CrudGridOptions) {
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
    gridOptions = {},
  } = options;

  const [Grid, gridApi] = useVbenVxeGrid({
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
          query: async ({ page }, formValues) => {
            const queryParams: any = {
              ...formValues,
              page: page.currentPage,
              size: page.pageSize,
            };
            if (defaultSortBy) {
              queryParams.sortBy = defaultSortBy;
              queryParams.sortDirection = defaultSortDirection;
            }
            const response: any = await fetchPage(queryParams);
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
    } as VxeTableGridOptions,
  });

  function onRefresh() {
    gridApi.query();
  }

  async function onDelete(row: any, customConfirmMessage?: string) {
    if (!deleteApi) {
      console.warn('deleteApi is not configured for useCrudGrid');
      return;
    }
    const id = row?.[rowKey] ?? row;
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
      await deleteApi(id);
      ElMessage.success('删除成功');
      onRefresh();
    } catch {
      // 用户取消
    }
  }

  return {
    Grid,
    gridApi,
    onRefresh,
    onDelete,
  };
}
