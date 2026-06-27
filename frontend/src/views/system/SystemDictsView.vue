<template>
  <div class="panel-stack system-dicts-page">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">字典类型</span>
        <strong>{{ total }}</strong>
        <span>当前筛选条件下的类型数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">当前页字典值</span>
        <strong>{{ pageValueTotal }}</strong>
        <span>当前页类型下挂载的字典值</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">已选类型</span>
        <strong>{{ selectedDict?.dictType || '-' }}</strong>
        <span>{{ selectedDict ? `${currentValueTotal} 个字典值` : '请选择一个字典类型' }}</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">字典</span>
          <h3>字典管理</h3>
          <p class="panel-subtitle">左侧维护字典类型，右侧维护该类型下的字典值。</p>
        </div>
        <div class="panel-actions">
          <el-button v-permission="'upms:sysdict:edit'" @click="refreshCacheAction">刷新缓存</el-button>
          <el-button v-permission="'upms:sysdict:add'" type="primary" @click="openDict()">新增字典类型</el-button>
        </div>
      </div>

      <AdvancedSearch @search="handleSearch" @reset="resetSearch">
        <el-form-item label="字典类型">
          <el-input v-model="typeKeyword" placeholder="输入字典类型" clearable />
        </el-form-item>
        <el-form-item label="字典分类">
          <el-select v-model="category" placeholder="全部分类" clearable style="width: 180px">
            <el-option v-for="item in categoryOptions" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键字">
          <el-input v-model="keyword" placeholder="搜索类型、说明或备注" clearable />
        </el-form-item>
        <el-form-item label="排序字段">
          <el-select v-model="sortBy" style="width: 160px">
            <el-option label="创建时间" value="createdAt" />
            <el-option label="字典类型" value="dictType" />
            <el-option label="兼容编码" value="dictCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序方向">
          <el-select v-model="sortDirection" style="width: 120px">
            <el-option label="升序" value="asc" />
            <el-option label="降序" value="desc" />
          </el-select>
        </el-form-item>
      </AdvancedSearch>

      <div class="dict-layout">
        <aside class="dict-type-panel">
          <div class="section-head">
            <div>
              <span class="eyebrow">类型列表</span>
              <h4>字典类型</h4>
            </div>
            <span class="muted-text">{{ dicts.length }} / {{ total }}</span>
          </div>

          <div class="table-tools">
            <el-radio-group v-model="dictTablePrefs.density" size="small">
              <el-radio-button value="compact">紧凑</el-radio-button>
              <el-radio-button value="default">默认</el-radio-button>
              <el-radio-button value="comfortable">宽松</el-radio-button>
            </el-radio-group>
            <el-popover placement="bottom-end" width="240" trigger="click">
              <template #reference>
                <el-button size="small">列显示</el-button>
              </template>
              <div class="column-chooser">
                <el-checkbox
                  v-for="item in dictTablePrefs.columns"
                  :key="item.key"
                  :model-value="dictTablePrefs.visibleColumnMap[item.key]"
                  @change="(value: boolean) => dictTablePrefs.setColumnVisible(item.key, value)"
                >
                  {{ item.label }}
                </el-checkbox>
              </div>
            </el-popover>
            <el-button size="small" @click="dictTablePrefs.reset()">恢复默认</el-button>
          </div>

          <el-result v-if="loadError" icon="error" title="加载失败" :sub-title="loadError" class="panel-result">
            <template #extra>
              <el-button type="primary" @click="load">重试</el-button>
            </template>
          </el-result>

          <el-table
            v-else
            v-loading="loading"
            :data="dicts"
            stripe
            class="type-table"
            :class="`table-density-${dictTablePrefs.density}`"
            :row-class-name="dictRowClassName"
            @row-click="selectDict"
            @header-dragend="onDictHeaderDragEnd"
          >
            <el-table-column
              v-if="dictTablePrefs.visibleColumnMap.dictType"
              column-key="dictType"
              prop="dictType"
              label="字典类型"
              min-width="180"
              :width="dictTablePrefs.getColumnWidth('dictType')"
            >
              <template #default="{ row }">
                <div class="type-cell">
                  <strong>{{ row.dictType }}</strong>
                  <span>{{ row.description || row.dictValue || '暂无说明' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="dictTablePrefs.visibleColumnMap.category"
              column-key="category"
              prop="category"
              label="分类"
              min-width="100"
              :width="dictTablePrefs.getColumnWidth('category')"
            />
            <el-table-column
              v-if="dictTablePrefs.visibleColumnMap.valueCount"
              column-key="valueCount"
              prop="valueCount"
              label="值数量"
              width="88"
              :width="dictTablePrefs.getColumnWidth('valueCount')"
            />
            <el-table-column
              v-if="dictTablePrefs.visibleColumnMap.enabled"
              column-key="enabled"
              label="状态"
              width="88"
              :width="dictTablePrefs.getColumnWidth('enabled')"
            >
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              v-if="dictTablePrefs.visibleColumnMap.updatedAt"
              column-key="updatedAt"
              label="更新时间"
              min-width="160"
              :width="dictTablePrefs.getColumnWidth('updatedAt')"
            >
              <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column
              v-if="dictTablePrefs.visibleColumnMap.actions"
              column-key="actions"
              fixed="right"
              label="操作"
              :width="dictTablePrefs.getColumnWidth('actions') || 150"
            >
              <template #default="{ row }">
                <el-button link type="primary" @click.stop="selectDict(row)">查看值</el-button>
                <el-button v-permission="'upms:sysdict:edit'" link type="primary" @click.stop="openDict(row)">编辑</el-button>
                <el-button v-permission="'upms:sysdict:del'" link type="danger" @click.stop="removeDict(row.id)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无字典类型" />
            </template>
          </el-table>

          <div class="pagination-wrap">
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="size"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              :total="total"
              @size-change="handleSizeChange"
              @current-change="handleCurrentChange"
            />
          </div>
        </aside>

        <main class="dict-value-panel">
          <div class="section-head value-head">
            <div>
              <span class="eyebrow">字典值</span>
              <h4>{{ selectedDict?.dictType || '未选择字典类型' }}</h4>
              <p>{{ selectedDict?.description || selectedDict?.dictValue || '选择左侧字典类型后维护具体字典值。' }}</p>
            </div>
            <div class="panel-actions">
              <el-button :disabled="!selectedDict" @click="reloadSelectedDetail">刷新</el-button>
              <el-button v-permission="'upms:sysdict:edit'" :disabled="!selectedDict" @click="openSelectedDict">编辑类型</el-button>
              <el-button v-permission="'upms:sysdict:add'" :disabled="!selectedDict" type="primary" @click="openValue()">新增字典值</el-button>
            </div>
          </div>

          <div v-if="selectedDict" class="dict-summary">
            <span>分类：{{ selectedDict.category || '-' }}</span>
            <span>状态：{{ selectedDict.enabled ? '启用' : '停用' }}</span>
            <span>字典值：{{ currentValueTotal }}</span>
            <span>启用值：{{ enabledValueCount }}</span>
          </div>

          <el-empty v-if="!selectedDict" description="请先选择左侧字典类型" class="value-empty" />

          <el-result v-else-if="valueLoadError" icon="error" title="加载失败" :sub-title="valueLoadError" class="panel-result">
            <template #extra>
              <el-button type="primary" @click="reloadSelectedDetail">重试</el-button>
            </template>
          </el-result>

          <el-table v-else v-loading="valuesLoading" :data="dictValues" stripe class="value-table">
            <el-table-column prop="dictLabel" label="字典标签" min-width="160">
              <template #default="{ row }">
                <div class="type-cell">
                  <strong>{{ row.dictLabel }}</strong>
                  <span>{{ row.remarks || '暂无备注' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="dictValue" label="字典键值" min-width="140" />
            <el-table-column prop="sort" label="排序" width="80" />
            <el-table-column label="回显样式" min-width="120">
              <template #default="{ row }">
                <el-tag :type="resolveTagType(row.showClass)">{{ row.showClass || 'default' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="88">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" min-width="160">
              <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
            </el-table-column>
            <el-table-column fixed="right" label="操作" width="150">
              <template #default="{ row }">
                <el-button v-permission="'upms:sysdict:edit'" link type="primary" @click="openValue(row)">编辑</el-button>
                <el-button v-permission="'upms:sysdict:del'" link type="danger" @click="removeValue(row.id)">删除</el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无字典值" />
            </template>
          </el-table>
        </main>
      </div>
    </section>

    <el-dialog v-model="dictDialogVisible" :title="editingDictId !== null ? '编辑字典类型' : '新增字典类型'" width="520px">
      <el-form ref="dictFormRef" label-position="top" :model="dictForm" :rules="dictRules">
        <el-form-item label="字典类型" prop="dictType">
          <el-input v-model="dictForm.dictType" placeholder="例如：system.user.status" />
        </el-form-item>
        <el-form-item label="类型说明" prop="description">
          <el-input v-model="dictForm.description" placeholder="说明该类型的业务用途" />
        </el-form-item>
        <el-form-item label="启用状态" prop="enabled">
          <el-switch v-model="dictForm.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注" prop="remarks">
          <el-input v-model="dictForm.remarks" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictDialogVisible = false">取消</el-button>
        <el-button v-permission="['upms:sysdict:add', 'upms:sysdict:edit']" type="primary" @click="submitDict">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="valueDialogVisible" :title="editingValueId !== null ? '编辑字典值' : '新增字典值'" width="560px">
      <el-alert v-if="selectedDict" :title="`所属字典类型：${selectedDict.dictType}`" type="info" show-icon :closable="false" class="dialog-alert" />
      <el-form ref="valueFormRef" label-position="top" :model="valueForm" :rules="valueRules">
        <el-form-item label="字典标签" prop="dictLabel">
          <el-input v-model="valueForm.dictLabel" placeholder="页面展示名称" />
        </el-form-item>
        <el-form-item label="字典键值" prop="dictValue">
          <el-input v-model="valueForm.dictValue" placeholder="业务存储值" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="valueForm.sort" :min="0" :step="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="回显样式" prop="showClass">
          <el-select v-model="valueForm.showClass" placeholder="选择标签样式" clearable>
            <el-option label="default" value="" />
            <el-option label="primary" value="primary" />
            <el-option label="success" value="success" />
            <el-option label="warning" value="warning" />
            <el-option label="danger" value="danger" />
            <el-option label="info" value="info" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态" prop="enabled">
          <el-switch v-model="valueForm.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注" prop="remarks">
          <el-input v-model="valueForm.remarks" type="textarea" :rows="3" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="valueDialogVisible = false">取消</el-button>
        <el-button v-permission="['upms:sysdict:add', 'upms:sysdict:edit']" type="primary" :disabled="!selectedDict" @click="submitValue">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import AdvancedSearch from '@/components/common/AdvancedSearch.vue'
import {
  createDict,
  createDictValue,
  deleteDict,
  deleteDictValue,
  queryCategories,
  queryDictDetail,
  queryDicts,
  refreshDictCache,
  updateDict,
  updateDictValue,
} from '@/api/modules'
import { useTablePreferences } from '@/composables/useTablePreferences'
import type { CategoryOption, DictValueView, DictView } from '@/types/system'
import { formatDateTime } from '@/utils/datetime'

type SortBy = 'createdAt' | 'dictType' | 'dictCode'
type SortDirection = 'asc' | 'desc'
type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const dicts = ref<DictView[]>([])
const dictValues = ref<DictValueView[]>([])
const detailDict = ref<DictView | null>(null)
const selectedDictId = ref<number | null>(null)
const loading = ref(false)
const valuesLoading = ref(false)
const loadError = ref('')
const valueLoadError = ref('')
const dictDialogVisible = ref(false)
const valueDialogVisible = ref(false)
const editingDictId = ref<number | null>(null)
const editingValueId = ref<number | null>(null)
const keyword = ref('')
const typeKeyword = ref('')
const category = ref('')
const sortBy = ref<SortBy>('createdAt')
const sortDirection = ref<SortDirection>('asc')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const dictFormRef = ref<FormInstance>()
const valueFormRef = ref<FormInstance>()
const categoryOptions = ref<CategoryOption[]>([])

const dictTablePrefs = useTablePreferences('table:system-dicts', [
  { key: 'dictType', label: '字典类型', width: 220 },
  { key: 'category', label: '分类', width: 110 },
  { key: 'valueCount', label: '值数量', width: 88 },
  { key: 'enabled', label: '状态', width: 88 },
  { key: 'updatedAt', label: '更新时间', width: 160 },
  { key: 'actions', label: '操作', width: 180 },
])

const dictForm = reactive({
  dictType: '',
  description: '',
  enabled: true,
  remarks: '',
})

const valueForm = reactive({
  dictLabel: '',
  dictValue: '',
  sort: 0,
  showClass: '',
  enabled: true,
  remarks: '',
})

const dictRules = reactive<FormRules>({
  dictType: [{ required: true, message: '请输入字典类型', trigger: 'blur' }],
})

const valueRules = reactive<FormRules>({
  dictLabel: [{ required: true, message: '请输入字典标签', trigger: 'blur' }],
  dictValue: [{ required: true, message: '请输入字典键值', trigger: 'blur' }],
})

const selectedDict = computed(() => {
  if (detailDict.value && detailDict.value.id === selectedDictId.value) {
    return detailDict.value
  }
  return dicts.value.find((item) => item.id === selectedDictId.value) ?? null
})
const pageValueTotal = computed(() => dicts.value.reduce((sum, item) => sum + (item.valueCount || 0), 0))
const currentValueTotal = computed(() => selectedDict.value?.valueCount ?? dictValues.value.length)
const enabledValueCount = computed(() => dictValues.value.filter((item) => item.enabled).length)

void load()
void loadCategories()

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await queryDicts({
      dictType: typeKeyword.value || undefined,
      category: category.value || undefined,
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value,
      sortBy: sortBy.value,
      sortDirection: sortDirection.value,
    })
    dicts.value = result.records
    total.value = result.total

    const nextId = resolveNextSelectedId(result.records)
    selectedDictId.value = nextId
    if (nextId === null) {
      detailDict.value = null
      dictValues.value = []
      return
    }
    await loadDetail(nextId)
  } catch {
    dicts.value = []
    dictValues.value = []
    detailDict.value = null
    total.value = 0
    selectedDictId.value = null
    loadError.value = '字典类型加载失败，请稍后重试。'
  } finally {
    loading.value = false
  }
}

async function loadDetail(id: number) {
  valuesLoading.value = true
  valueLoadError.value = ''
  try {
    const detail = await queryDictDetail(id)
    detailDict.value = detail.dict
    dictValues.value = detail.values
    const index = dicts.value.findIndex((item) => item.id === detail.dict.id)
    if (index >= 0) {
      dicts.value[index] = detail.dict
    }
  } catch {
    dictValues.value = []
    valueLoadError.value = '字典值加载失败，请稍后重试。'
  } finally {
    valuesLoading.value = false
  }
}

async function loadCategories() {
  const result = await queryCategories()
  categoryOptions.value = result.dict || []
}

function resolveNextSelectedId(records: DictView[]) {
  if (records.some((item) => item.id === selectedDictId.value)) {
    return selectedDictId.value
  }
  return records[0]?.id ?? null
}

function handleSearch() {
  page.value = 1
  void load()
}

function resetSearch() {
  keyword.value = ''
  typeKeyword.value = ''
  category.value = ''
  sortBy.value = 'createdAt'
  sortDirection.value = 'asc'
  page.value = 1
  void load()
}

function handleSizeChange(value: number) {
  size.value = value
  page.value = 1
  void load()
}

function handleCurrentChange(value: number) {
  page.value = value
  void load()
}

function selectDict(row: DictView) {
  selectedDictId.value = row.id
  void loadDetail(row.id)
}

function reloadSelectedDetail() {
  if (selectedDictId.value === null) {
    return
  }
  void loadDetail(selectedDictId.value)
}

function openSelectedDict() {
  if (!selectedDict.value) {
    return
  }
  openDict(selectedDict.value)
}

function openDict(row?: DictView) {
  editingDictId.value = row?.id ?? null
  Object.assign(dictForm, {
    dictType: row?.dictType ?? '',
    description: row?.description ?? row?.dictValue ?? '',
    enabled: row?.enabled ?? true,
    remarks: row?.remarks ?? '',
  })
  dictDialogVisible.value = true
}

function openValue(row?: DictValueView) {
  if (!selectedDict.value) {
    return
  }
  editingValueId.value = row?.id ?? null
  Object.assign(valueForm, {
    dictLabel: row?.dictLabel ?? '',
    dictValue: row?.dictValue ?? '',
    sort: row?.sort ?? nextValueSort(),
    showClass: row?.showClass ?? '',
    enabled: row?.enabled ?? true,
    remarks: row?.remarks ?? '',
  })
  valueDialogVisible.value = true
}

async function submitDict() {
  if (!dictFormRef.value) {
    return
  }
  try {
    await dictFormRef.value.validate()
    const payload = {
      dictType: dictForm.dictType.trim(),
      description: blankToUndefined(dictForm.description),
      enabled: dictForm.enabled,
      remarks: blankToUndefined(dictForm.remarks),
    }
    if (editingDictId.value !== null) {
      const saved = await updateDict(editingDictId.value, payload)
      selectedDictId.value = saved.id
      ElMessage.success('字典类型已更新')
    } else {
      const saved = await createDict(payload)
      selectedDictId.value = saved.id
      ElMessage.success('字典类型已创建')
    }
    dictDialogVisible.value = false
    await load()
  } catch {
    // 统一请求拦截器和表单校验会处理错误提示。
  }
}

async function submitValue() {
  if (!valueFormRef.value || !selectedDict.value) {
    return
  }
  try {
    await valueFormRef.value.validate()
    const payload = {
      dictLabel: valueForm.dictLabel.trim(),
      dictValue: valueForm.dictValue.trim(),
      sort: valueForm.sort,
      showClass: blankToUndefined(valueForm.showClass),
      enabled: valueForm.enabled,
      remarks: blankToUndefined(valueForm.remarks),
    }
    if (editingValueId.value !== null) {
      await updateDictValue(editingValueId.value, payload)
      ElMessage.success('字典值已更新')
    } else {
      await createDictValue(selectedDict.value.id, payload)
      ElMessage.success('字典值已创建')
    }
    valueDialogVisible.value = false
    await load()
  } catch {
    // 统一请求拦截器和表单校验会处理错误提示。
  }
}

async function removeDict(id: number) {
  await ElMessageBox.confirm('删除字典类型会同步删除下属字典值，是否继续？', '删除确认', { type: 'warning' })
  await deleteDict(id)
  if (selectedDictId.value === id) {
    selectedDictId.value = null
  }
  ElMessage.success('字典类型已删除')
  await load()
}

async function removeValue(id: number) {
  await ElMessageBox.confirm('删除后不可恢复，是否继续？', '删除确认', { type: 'warning' })
  await deleteDictValue(id)
  ElMessage.success('字典值已删除')
  await load()
}

async function refreshCacheAction() {
  await refreshDictCache()
  ElMessage.success('字典缓存已刷新')
}

function nextValueSort() {
  return dictValues.value
    .map((item) => item.sort)
    .filter((value) => value !== null && value !== undefined)
    .reduce((max, value) => Math.max(max, value + 1), 0)
}

function formatTime(value?: number | null) {
  return formatDateTime(value)
}

function resolveTagType(showClass?: string | null): TagType {
  if (showClass === 'success' || showClass === 'warning' || showClass === 'danger' || showClass === 'primary') {
    return showClass
  }
  return 'info'
}

function blankToUndefined(value: string) {
  const normalized = value.trim()
  return normalized ? normalized : undefined
}

function dictRowClassName({ row }: { row: DictView }) {
  return row.id === selectedDictId.value ? 'is-active-row' : ''
}

function onDictHeaderDragEnd(newWidth: number, _oldWidth: number, column: { property?: string; columnKey?: string }) {
  const key = String(column.columnKey || column.property || '')
  if (!key) {
    return
  }
  dictTablePrefs.setColumnWidth(key, newWidth)
}
</script>

<style scoped>
.system-dicts-page {
  gap: 18px;
}

.panel-subtitle,
.value-head p,
.type-cell span,
.muted-text {
  color: #64748b;
}

.panel-subtitle,
.value-head p {
  margin: 6px 0 0;
}

.panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.dict-layout {
  display: grid;
  grid-template-columns: minmax(420px, 0.9fr) minmax(520px, 1.1fr);
  gap: 18px;
  margin-top: 16px;
}

.dict-type-panel,
.dict-value-panel {
  min-width: 0;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 16px;
  background: #fff;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.section-head h4 {
  margin: 4px 0 0;
  color: #0f172a;
  font-size: 18px;
}

.table-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.column-chooser {
  display: grid;
  gap: 8px;
}

.type-cell {
  display: grid;
  gap: 4px;
}

.type-cell strong {
  color: #0f172a;
}

.dict-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.dict-summary span {
  border-radius: 999px;
  background: #f1f5f9;
  color: #334155;
  padding: 5px 10px;
  font-size: 12px;
}

.value-empty {
  padding: 56px 0;
}

.dialog-alert {
  margin-bottom: 14px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.type-table :deep(.is-active-row > td) {
  background: #eff6ff !important;
}

.type-table :deep(.is-active-row .type-cell strong) {
  color: #2563eb;
}

@media (max-width: 1200px) {
  .dict-layout {
    grid-template-columns: 1fr;
  }
}
</style>
