import { computed, reactive, ref, watch } from 'vue';

/**
 * 表格密度类型
 */
export type TableDensity = 'compact' | 'comfortable' | 'default';

/**
 * 表格列偏好设置
 */
export interface TableColumnPreference {
  /** 列的唯一标识 */
  key: string;
  /** 列标签 */
  label: string;
  /** 是否默认可见 */
  visible?: boolean;
  /** 默认宽度 */
  width?: number;
}

/**
 * 持久化的表格偏好设置
 */
interface PersistedTablePreferences {
  density: TableDensity;
  visibleColumns: string[];
  columnWidths: Record<string, number>;
}

/**
 * 安全解析 JSON
 */
function safeParse(raw: string | null): PersistedTablePreferences | null {
  if (!raw) {
    return null;
  }
  try {
    return JSON.parse(raw) as PersistedTablePreferences;
  } catch {
    return null;
  }
}

/**
 * 表格偏好设置管理
 * 
 * 提供表格列显示、宽度、密度等偏好设置的持久化管理：
 * - 列可见性控制
 * - 列宽度调整
 * - 表格密度切换
 * - 自动持久化到 localStorage
 * 
 * @param storageKey - localStorage 存储键
 * @param columns - 列配置
 * 
 * @example
 * ```ts
 * const tablePrefs = useTablePreferences('user-table-prefs', [
 *   { key: 'name', label: '姓名', visible: true, width: 120 },
 *   { key: 'email', label: '邮箱', visible: true, width: 200 },
 *   { key: 'status', label: '状态', visible: false },
 * ]);
 * 
 * // 控制列可见性
 * tablePrefs.setColumnVisible('status', true);
 * 
 * // 调整列宽度
 * tablePrefs.setColumnWidth('name', 150);
 * 
 * // 切换密度
 * tablePrefs.density = 'compact';
 * 
 * // 重置为默认值
 * tablePrefs.reset();
 * ```
 */
export function useTablePreferences(
  storageKey: string,
  columns: TableColumnPreference[],
) {
  // 计算默认可见列
  const defaultVisible = columns
    .filter((item) => item.visible !== false)
    .map((item) => item.key);

  // 计算默认列宽
  const defaultWidths: Record<string, number> = {};
  for (const item of columns) {
    if (item.width) {
      defaultWidths[item.key] = item.width;
    }
  }

  // 从 localStorage 恢复偏好设置
  const persisted =
    typeof window === 'undefined'
      ? null
      : safeParse(localStorage.getItem(storageKey));

  const density = ref<TableDensity>(persisted?.density || 'default');
  const visibleColumns = ref<string[]>(
    persisted?.visibleColumns?.length
      ? persisted.visibleColumns
      : defaultVisible,
  );
  const columnWidths = ref<Record<string, number>>({
    ...defaultWidths,
    ...(persisted?.columnWidths || {}),
  });

  /**
   * 可见列映射表（用于快速查询）
   */
  const visibleColumnMap = computed(() => {
    const visibleSet = new Set(visibleColumns.value);
    return columns.reduce<Record<string, boolean>>((acc, item) => {
      acc[item.key] = visibleSet.has(item.key);
      return acc;
    }, {});
  });

  /**
   * 设置列可见性
   * @param key - 列标识
   * @param visible - 是否可见
   */
  function setColumnVisible(key: string, visible: boolean) {
    const current = new Set(visibleColumns.value);
    if (visible) {
      current.add(key);
    } else {
      current.delete(key);
    }
    // 确保至少有一列可见
    if (current.size === 0) {
      current.add(key);
    }
    visibleColumns.value = [...current];
  }

  /**
   * 设置列宽度
   * @param key - 列标识
   * @param width - 宽度值
   */
  function setColumnWidth(key: string, width: number) {
    if (!Number.isFinite(width) || width <= 0) {
      return;
    }
    columnWidths.value = {
      ...columnWidths.value,
      [key]: Math.round(width),
    };
  }

  /**
   * 获取列宽度
   * @param key - 列标识
   */
  function getColumnWidth(key: string) {
    return columnWidths.value[key];
  }

  /**
   * 重置为默认值
   */
  function reset() {
    density.value = 'default';
    visibleColumns.value = [...defaultVisible];
    columnWidths.value = { ...defaultWidths };
  }

  // 监听变化并持久化
  watch(
    [density, visibleColumns, columnWidths],
    () => {
      if (typeof window === 'undefined') {
        return;
      }
      const payload: PersistedTablePreferences = {
        columnWidths: columnWidths.value,
        density: density.value,
        visibleColumns: visibleColumns.value,
      };
      localStorage.setItem(storageKey, JSON.stringify(payload));
    },
    { deep: true },
  );

  return reactive({
    /** 列配置 */
    columns,
    /** 表格密度 */
    density,
    /** 可见列列表 */
    visibleColumns,
    /** 可见列映射表 */
    visibleColumnMap,
    /** 列宽度映射 */
    columnWidths,
    /** 设置列可见性 */
    setColumnVisible,
    /** 设置列宽度 */
    setColumnWidth,
    /** 获取列宽度 */
    getColumnWidth,
    /** 重置 */
    reset,
  });
}