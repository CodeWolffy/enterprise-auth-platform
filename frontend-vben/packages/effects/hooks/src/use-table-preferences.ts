import { reactive, watch } from 'vue';

interface TablePreferenceColumn {
  key: string;
  label: string;
  width?: number;
}

type TableDensity = 'comfortable' | 'compact' | 'default';

interface TablePreferenceState {
  density: TableDensity;
  visibleColumnMap: Record<string, boolean>;
  widthMap: Record<string, number>;
}

const defaultState = (
  columns: TablePreferenceColumn[],
): TablePreferenceState => ({
  density: 'default',
  visibleColumnMap: Object.fromEntries(columns.map((item) => [item.key, true])),
  widthMap: Object.fromEntries(
    columns
      .filter((item) => typeof item.width === 'number')
      .map((item) => [item.key, item.width as number]),
  ),
});

const readState = (
  storageKey: string,
  columns: TablePreferenceColumn[],
): TablePreferenceState => {
  const fallback = defaultState(columns);

  if (typeof localStorage === 'undefined') {
    return fallback;
  }

  try {
    const parsed = JSON.parse(
      localStorage.getItem(storageKey) || '{}',
    ) as Partial<TablePreferenceState>;

    return {
      density: parsed.density ?? fallback.density,
      visibleColumnMap: {
        ...fallback.visibleColumnMap,
        ...parsed.visibleColumnMap,
      },
      widthMap: {
        ...fallback.widthMap,
        ...parsed.widthMap,
      },
    };
  } catch {
    return fallback;
  }
};

export function useTablePreferences(
  key: string,
  columns: TablePreferenceColumn[],
) {
  const storageKey = `vben:table-preferences:${key}`;
  const state = reactive<TablePreferenceState>(readState(storageKey, columns));

  watch(
    state,
    (value) => {
      if (typeof localStorage === 'undefined') {
        return;
      }
      localStorage.setItem(storageKey, JSON.stringify(value));
    },
    { deep: true },
  );

  const reset = () => {
    Object.assign(state, defaultState(columns));
  };

  const setColumnVisible = (columnKey: string, visible: boolean) => {
    state.visibleColumnMap[columnKey] = visible;
  };

  const setColumnWidth = (columnKey: string, width: number) => {
    state.widthMap[columnKey] = width;
  };

  const getColumnWidth = (columnKey: string) => state.widthMap[columnKey];

  return {
    columns,
    get density() {
      return state.density;
    },
    set density(value: TableDensity) {
      state.density = value;
    },
    getColumnWidth,
    reset,
    setColumnVisible,
    setColumnWidth,
    visibleColumnMap: state.visibleColumnMap,
  };
}
