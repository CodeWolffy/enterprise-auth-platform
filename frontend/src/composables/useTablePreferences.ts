import { computed, reactive, ref, watch } from 'vue'

export type TableDensity = 'compact' | 'default' | 'comfortable'

export interface TableColumnPreference {
  key: string
  label: string
  visible?: boolean
  width?: number
}

interface PersistedTablePreferences {
  density: TableDensity
  visibleColumns: string[]
  columnWidths: Record<string, number>
}

function safeParse(raw: string | null): PersistedTablePreferences | null {
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as PersistedTablePreferences
  } catch {
    return null
  }
}

export function useTablePreferences(storageKey: string, columns: TableColumnPreference[]) {
  const defaultVisible = columns.filter((item) => item.visible !== false).map((item) => item.key)
  const defaultWidths: Record<string, number> = {}
  columns.forEach((item) => {
    if (item.width) {
      defaultWidths[item.key] = item.width
    }
  })

  const persisted = typeof window === 'undefined' ? null : safeParse(localStorage.getItem(storageKey))
  const density = ref<TableDensity>(persisted?.density || 'default')
  const visibleColumns = ref<string[]>(persisted?.visibleColumns?.length ? persisted.visibleColumns : defaultVisible)
  const columnWidths = ref<Record<string, number>>({ ...defaultWidths, ...(persisted?.columnWidths || {}) })

  const visibleColumnMap = computed(() => {
    const visibleSet = new Set(visibleColumns.value)
    return columns.reduce<Record<string, boolean>>((acc, item) => {
      acc[item.key] = visibleSet.has(item.key)
      return acc
    }, {})
  })

  function setColumnVisible(key: string, visible: boolean) {
    const current = new Set(visibleColumns.value)
    if (visible) {
      current.add(key)
    } else {
      current.delete(key)
    }
    if (current.size === 0) {
      current.add(key)
    }
    visibleColumns.value = Array.from(current)
  }

  function setColumnWidth(key: string, width: number) {
    if (!Number.isFinite(width) || width <= 0) {
      return
    }
    columnWidths.value = {
      ...columnWidths.value,
      [key]: Math.round(width),
    }
  }

  function getColumnWidth(key: string) {
    return columnWidths.value[key]
  }

  function reset() {
    density.value = 'default'
    visibleColumns.value = [...defaultVisible]
    columnWidths.value = { ...defaultWidths }
  }

  watch(
    [density, visibleColumns, columnWidths],
    () => {
      if (typeof window === 'undefined') {
        return
      }
      const payload: PersistedTablePreferences = {
        density: density.value,
        visibleColumns: visibleColumns.value,
        columnWidths: columnWidths.value,
      }
      localStorage.setItem(storageKey, JSON.stringify(payload))
    },
    { deep: true },
  )

  return reactive({
    columns,
    density,
    visibleColumns,
    visibleColumnMap,
    columnWidths,
    setColumnVisible,
    setColumnWidth,
    getColumnWidth,
    reset,
  })
}
