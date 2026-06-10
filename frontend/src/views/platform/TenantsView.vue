<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">总租户</span>
        <strong>{{ total }}</strong>
        <span>当前筛选条件下的租户总数</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">启用租户</span>
        <strong>{{ enabledTenantCount }}</strong>
        <span>当前页已启用租户数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">平台级</span>
        <strong>{{ platformTenantCount }}</strong>
        <span>当前页平台级租户数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">能力覆盖</span>
        <strong>{{ overrideChangedCount }}</strong>
        <span>当前已编辑的覆盖策略条目</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div><span class="eyebrow">租户</span><h3>租户管理</h3></div>
        <el-button v-permission="'tenant:write'" type="primary" @click="void openTenant()">新增租户</el-button>
      </div>

      <AdvancedSearch @search="handleSearch" @reset="resetSearch">
        <el-form-item label="关键字"><el-input v-model="keyword" placeholder="搜索租户编码或名称" clearable /></el-form-item>
        <el-form-item label="租户级别">
          <el-select v-model="platformFilter" clearable style="width: 160px">
            <el-option label="全部" value="" />
            <el-option label="平台级租户" value="platform" />
            <el-option label="业务租户" value="business" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" clearable style="width: 140px">
            <el-option label="全部" value="" />
            <el-option label="启用" value="1" />
            <el-option label="禁用" value="0" />
          </el-select>
        </el-form-item>
      </AdvancedSearch>

      <div class="table-tools">
        <div class="table-tools__left">
          <el-radio-group v-model="tenantTablePrefs.density" size="small">
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
              v-for="item in tenantTablePrefs.columns"
              :key="item.key"
              :model-value="tenantTablePrefs.visibleColumnMap[item.key]"
              @change="(value: boolean) => tenantTablePrefs.setColumnVisible(item.key, value)"
            >
              {{ item.label }}
            </el-checkbox>
          </div>
        </el-popover>
        <el-button size="small" @click="tenantTablePrefs.reset()">恢复默认</el-button>
        </div>
        <div class="table-tools__right">
          <span class="toolbar-tip">{{ tenantRefreshTip }}</span>
          <el-button size="small" :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="tenants"
        stripe
        :class="`table-density-${tenantTablePrefs.density}`"
        @header-dragend="onTenantHeaderDragEnd"
      >
        <el-table-column
          v-if="tenantTablePrefs.visibleColumnMap.tenantId"
          column-key="tenantId"
          prop="tenantId"
          label="租户编码"
          min-width="140"
          :width="tenantTablePrefs.getColumnWidth('tenantId')"
        />
        <el-table-column
          v-if="tenantTablePrefs.visibleColumnMap.name"
          column-key="name"
          prop="name"
          label="租户名称"
          min-width="160"
          :width="tenantTablePrefs.getColumnWidth('name')"
        />
        <el-table-column
          v-if="tenantTablePrefs.visibleColumnMap.level"
          column-key="level"
          label="级别"
          min-width="100"
          :width="tenantTablePrefs.getColumnWidth('level')"
        >
          <template #default="{ row }">
            <el-tag :type="row.platformLevel ? 'danger' : 'success'">{{ row.platformLevel ? '平台级' : '业务级' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="tenantTablePrefs.visibleColumnMap.status"
          column-key="status"
          label="状态"
          min-width="90"
          :width="tenantTablePrefs.getColumnWidth('status')"
        >
          <template #default="{ row }">
            <el-tag :type="row.tenantStatus === 1 ? 'success' : 'info'">{{ row.tenantStatus === 1 ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          v-if="tenantTablePrefs.visibleColumnMap.package"
          column-key="package"
          label="套餐"
          min-width="180"
          :width="tenantTablePrefs.getColumnWidth('package')"
        >
          <template #default="{ row }">{{ row.packageName || row.packageCode || '-' }}</template>
        </el-table-column>
        <el-table-column
          v-if="tenantTablePrefs.visibleColumnMap.authWindow"
          column-key="authWindow"
          label="授权期限"
          min-width="220"
          :width="tenantTablePrefs.getColumnWidth('authWindow')"
        >
          <template #default="{ row }">
            <div class="auth-window">
              <span>{{ formatDateTime(row.authBeginAt) || '立即生效' }}</span>
              <small>至 {{ formatDateTime(row.expireAt) || '长期有效' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          v-if="tenantTablePrefs.visibleColumnMap.contact"
          column-key="contact"
          label="联系人"
          min-width="180"
          :width="tenantTablePrefs.getColumnWidth('contact')"
        >
          <template #default="{ row }">
            <div class="auth-window">
              <span>{{ row.contactName || '-' }}</span>
              <small>{{ row.contactPhone || row.contactEmail || '未填写联系方式' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          v-if="tenantTablePrefs.visibleColumnMap.actions"
          column-key="actions"
          fixed="right"
          label="操作"
          width="340"
          :min-width="220"
        >
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openHistory(row)">变更历史</el-button>
            <el-button v-permission="'tenant:write'" link type="primary" @click="openOverrides(row)">能力覆盖</el-button>
            <el-button v-permission="'tenant:write'" link type="primary" @click="void openTenant(row)">编辑</el-button>
            <el-button v-permission="'tenant:write'" link type="danger" @click="removeTenant(row.tenantId)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无租户数据" />
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
    </section>

    <el-drawer v-model="detailVisible" title="租户详情" size="760px">
      <el-descriptions v-if="detailTenant" :column="2" border class="drawer-section drawer-section--overview">
        <el-descriptions-item label="租户编码">{{ detailTenant.tenantId }}</el-descriptions-item>
        <el-descriptions-item label="租户名称">{{ detailTenant.name }}</el-descriptions-item>
        <el-descriptions-item label="套餐">{{ detailTenant.packageName || detailTenant.packageCode || '未设置' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detailTenant.tenantStatus === 1 ? '启用' : '禁用' }}</el-descriptions-item>
        <el-descriptions-item label="授权开始">{{ formatDateTime(detailTenant.authBeginAt) || '立即生效' }}</el-descriptions-item>
        <el-descriptions-item label="授权结束">{{ formatDateTime(detailTenant.expireAt) || '长期有效' }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detailTenant.contactName || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detailTenant.contactPhone || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="联系邮箱">{{ detailTenant.contactEmail || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="官网">{{ detailTenant.website || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="地址" :span="2">{{ detailTenant.address || '未填写' }}</el-descriptions-item>
        <el-descriptions-item label="运营备注" :span="2">{{ detailTenant.lifecycleNote || '未填写' }}</el-descriptions-item>
      </el-descriptions>

      <div v-loading="detailLoading" class="detail-capability-panel">
        <div class="detail-metrics">
          <article class="insight-card"><strong>{{ detailCapabilitySummary?.packageCapabilityCount ?? 0 }}</strong><span>套餐默认能力</span></article>
          <article class="insight-card"><strong>{{ detailCapabilitySummary?.effectiveCapabilityCount ?? 0 }}</strong><span>当前生效能力</span></article>
          <article class="insight-card"><strong>{{ detailCapabilitySummary?.visibleMenuCount ?? 0 }}</strong><span>实际可见菜单</span></article>
          <article class="insight-card"><strong>{{ detailCapabilitySummary?.grantableMenuCount ?? 0 }}</strong><span>实际可授权菜单</span></article>
        </div>

        <el-alert :title="detailCapabilityDiffText" type="info" show-icon :closable="false" class="form-alert" />
        <el-alert :title="detailResourceSummaryText" type="success" show-icon :closable="false" class="form-alert" />

        <section class="detail-block">
          <div class="detail-block__head"><strong>套餐能力</strong><span>{{ detailCapabilitySummary?.packageName || detailCapabilitySummary?.packageCode || '未设置套餐' }}</span></div>
          <div class="tag-row">
            <el-tag v-for="code in detailCapabilitySummary?.packageCapabilityCodes || []" :key="`package-${code}`" effect="plain">{{ capabilityLabel(code) }}</el-tag>
            <el-empty v-if="!detailCapabilitySummary?.packageCapabilityCodes?.length" description="暂无套餐能力" :image-size="80" />
          </div>
        </section>

        <section class="detail-block">
          <div class="detail-block__head"><strong>租户覆盖后的生效能力</strong><span>覆盖策略不会修改套餐定义</span></div>
          <div class="tag-row">
            <el-tag v-for="code in detailCapabilitySummary?.effectiveCapabilityCodes || []" :key="`effective-${code}`" type="success" effect="plain">{{ capabilityLabel(code) }}</el-tag>
            <el-empty v-if="!detailCapabilitySummary?.effectiveCapabilityCodes?.length" description="暂无生效能力" :image-size="80" />
          </div>
        </section>

        <section class="detail-block">
          <div class="detail-block__head"><strong>实际可见菜单范围</strong><span>来自当前生效能力的资源范围</span></div>
          <el-table :data="detailCapabilitySummary?.visibleMenus || []" size="small" stripe>
            <el-table-column prop="menuName" label="菜单" min-width="150" />
            <el-table-column prop="resourceKey" label="资源键" min-width="180" />
            <el-table-column prop="path" label="路径" min-width="160" />
          </el-table>
        </section>

        <section class="detail-block">
          <div class="detail-block__head"><strong>实际可授权菜单范围</strong><span>用于角色菜单授权边界</span></div>
          <el-table :data="detailCapabilitySummary?.grantableMenus || []" size="small" stripe>
            <el-table-column prop="menuName" label="菜单" min-width="150" />
            <el-table-column prop="grantKey" label="授权键" min-width="180" />
            <el-table-column prop="resourceKey" label="资源键" min-width="180" />
          </el-table>
        </section>
      </div>
    </el-drawer>

    <el-drawer v-model="historyVisible" title="租户变更历史" size="860px">
      <AdvancedSearch @search="applyHistorySearch" @reset="resetHistorySearch">
        <el-form-item label="类型">
          <el-select v-model="historyQuery.changeType" clearable style="width: 160px">
            <el-option label="全部" value="" />
            <el-option label="创建" value="CREATED" />
            <el-option label="状态" value="STATUS" />
            <el-option label="套餐" value="PACKAGE" />
            <el-option label="能力" value="CAPABILITY" />
            <el-option label="资料" value="PROFILE" />
          </el-select>
        </el-form-item>
        <el-form-item label="字段"><el-input v-model="historyQuery.fieldKey" placeholder="按字段键筛选" clearable /></el-form-item>
        <el-form-item label="操作人"><el-input v-model="historyQuery.operator" placeholder="按操作人筛选" clearable /></el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="historyDateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            clearable
          />
        </el-form-item>
      </AdvancedSearch>

      <div class="history-insight">
        <article class="insight-card"><strong>{{ historySummary.totalChanges }}</strong><span>命中变更总数</span></article>
        <article class="insight-card"><strong>{{ historySummary.packageChanges }}</strong><span>套餐相关变更</span></article>
        <article class="insight-card"><strong>{{ historySummary.capabilityChanges }}</strong><span>能力相关变更</span></article>
        <article class="insight-card"><strong>{{ historySummary.statusChanges }}</strong><span>状态相关变更</span></article>
      </div>

      <div v-if="Object.keys(historySummary.affectedFieldCounts).length" class="tag-row">
        <el-tag v-for="(count, field) in historySummary.affectedFieldCounts" :key="field" type="info" effect="plain">{{ field }} × {{ count }}</el-tag>
      </div>

      <el-timeline v-if="historySummary.recentTimeline.length">
        <el-timeline-item v-for="item in historySummary.recentTimeline" :key="item.id" :timestamp="formatDateTime(item.occurredAt)" placement="top">
          <div class="timeline-item">
            <strong>{{ item.summary }}</strong>
            <span>{{ item.impactSummary || '该变更会影响当前租户的套餐、能力或运营说明。' }}</span>
          </div>
        </el-timeline-item>
      </el-timeline>

      <el-table :data="historyRecords" stripe>
        <el-table-column prop="changeType" label="类型" min-width="120" />
        <el-table-column prop="fieldKey" label="字段" min-width="140" />
        <el-table-column prop="summary" label="摘要" min-width="220" />
        <el-table-column prop="impactSummary" label="影响说明" min-width="280" />
        <el-table-column prop="operator" label="操作人" min-width="120" />
        <el-table-column label="时间" min-width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.occurredAt) }}
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="historyPage"
          v-model:page-size="historySize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="historyTotal"
          @size-change="handleHistorySizeChange"
          @current-change="handleHistoryPageChange"
        />
      </div>
    </el-drawer>

    <el-drawer v-model="overrideVisible" title="租户能力覆盖" size="960px">
      <template v-if="overrideBundle">
        <div class="tag-row">
          <el-tag type="info">套餐默认 {{ overrideBundle.packageCapabilityCodes.length }}</el-tag>
          <el-tag type="success">当前生效 {{ overrideBundle.effectiveCapabilityCodes.length }}</el-tag>
          <el-tag type="warning">策略覆盖 {{ overrideChangedCount }}</el-tag>
        </div>
        <div class="override-bar">
          <span class="toolbar-tip">差异摘要：{{ overrideSummaryText }}</span>
          <div>
            <el-button v-permission="'tenant:write'" :disabled="overrideChangedCount === 0 && descriptionChangedCount === 0" @click="restoreAllOverrides">恢复全部默认</el-button>
            <el-button v-permission="'tenant:write'" type="primary" :loading="overrideSaving" @click="submitOverrides">保存覆盖</el-button>
          </div>
        </div>
        <el-table :data="overrideForm" stripe>
          <el-table-column prop="capabilityCode" label="能力编码" min-width="150" />
          <el-table-column prop="capabilityName" label="能力名称" min-width="160" />
          <el-table-column label="套餐默认" min-width="100">
            <template #default="{ row }">
              <el-tag :type="row.packageEnabled ? 'success' : 'info'" effect="plain">{{ row.packageEnabled ? '启用' : '关闭' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="覆盖策略" min-width="160">
            <template #default="{ row }">
              <el-select v-model="row.overrideMode" style="width: 120px">
                <el-option label="继承" value="inherit" />
                <el-option label="启用" value="enable" />
                <el-option label="禁用" value="disable" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="说明覆盖" min-width="260">
            <template #default="{ row }">
              <el-input v-model="row.capabilityDescOverride" type="textarea" :rows="2" maxlength="200" show-word-limit placeholder="留空则沿用平台能力说明" />
            </template>
          </el-table-column>
          <el-table-column fixed="right" label="操作" width="120">
            <template #default="{ $index }">
              <el-button v-permission="'tenant:write'" link type="primary" @click="restoreRowOverride($index)">恢复默认</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <el-dialog v-model="visible" :title="editingTenantId ? '编辑租户' : '新增租户'" width="720px">
      <el-form ref="formRef" label-position="top" :model="form" :rules="tenantRules">
        <el-form-item label="租户编码" prop="tenantId"><el-input v-model="form.tenantId" :disabled="Boolean(editingTenantId)" /></el-form-item>
        <el-form-item label="租户名称" prop="tenantName"><el-input v-model="form.tenantName" /></el-form-item>
        <el-form-item label="租户级别"><el-switch v-model="form.platformLevel" inline-prompt active-text="平台级" inactive-text="业务级" /></el-form-item>
        <el-form-item label="租户状态" prop="tenantStatus"><el-select v-model="form.tenantStatus" style="width: 100%"><el-option label="启用" :value="1" /><el-option label="禁用" :value="0" /></el-select></el-form-item>
        <el-form-item label="授权开始时间"><el-date-picker v-model="form.authBeginAt" type="datetime" style="width: 100%" placeholder="留空表示立即生效" /></el-form-item>
        <el-form-item label="授权结束时间"><el-date-picker v-model="form.expireAt" type="datetime" style="width: 100%" placeholder="留空表示长期有效" /></el-form-item>
        <el-form-item label="Logo 地址"><el-input v-model="form.logoUrl" placeholder="https://..." /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="联系邮箱"><el-input v-model="form.contactEmail" /></el-form-item>
        <el-form-item label="官网地址"><el-input v-model="form.website" placeholder="https://..." /></el-form-item>
        <el-form-item label="联系地址"><el-input v-model="form.address" /></el-form-item>
        <el-form-item label="套餐">
          <el-select v-model="form.packageCode" filterable clearable style="width: 100%" @change="syncPackage"><el-option v-for="item in packageOptions" :key="item.packageCode" :label="`${item.packageName} (${item.packageCode})`" :value="item.packageCode" /></el-select>
        </el-form-item>
        <div v-if="selectedPackage" class="package-preview">
          <div class="package-preview__head">
            <strong>{{ selectedPackage.packageName }}</strong>
            <span>{{ selectedPackage.subtitle || selectedPackage.packageDesc || '该租户将继承此套餐的默认能力与配额。' }}</span>
          </div>
          <div class="tag-row">
            <el-tag type="info">默认能力 {{ selectedPackage.capabilityCodes.length }}</el-tag>
            <el-tag type="success">可见资源 {{ selectedPackage.visibleResourceCount ?? 0 }}</el-tag>
            <el-tag type="warning">授权资源 {{ selectedPackage.grantResourceCount ?? 0 }}</el-tag>
          </div>
          <div class="tag-row">
            <el-tag v-for="code in selectedPackage.capabilityCodes" :key="code" effect="plain">{{ capabilityLabel(code) }}</el-tag>
          </div>
        </div>
        <el-alert
          v-if="packageChangeTip"
          :title="packageChangeTip"
          type="warning"
          show-icon
          :closable="false"
          class="form-alert"
        />
        <el-form-item label="套餐名称">
          <el-input v-model="form.packageName" disabled />
        </el-form-item>
        <el-form-item label="用户配额">
          <el-input-number v-model="form.userQuota" :min="0" style="width: 100%" disabled />
        </el-form-item>
        <el-form-item label="存储配额(GB)">
          <el-input-number v-model="form.storageQuotaGb" :min="0" style="width: 100%" disabled />
        </el-form-item>
        <el-form-item label="生效能力集合">
          <el-select v-model="form.capabilityCodes" multiple filterable style="width: 100%">
            <el-option v-for="capability in capabilityOptions" :key="capability.capabilityCode" :label="`${capability.capabilityName} (${capability.capabilityCode})`" :value="capability.capabilityCode" />
          </el-select>
          <div class="form-tip">编辑能力集合只保存当前租户的覆盖策略，不会修改平台套餐定义。</div>
        </el-form-item>
        <div v-if="capabilityDiffText" class="form-tip form-tip--strong">{{ capabilityDiffText }}</div>
        <el-form-item label="运营备注"><el-input v-model="form.lifecycleNote" type="textarea" :rows="3" maxlength="200" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="visible = false">取消</el-button><el-button v-permission="'tenant:write'" type="primary" @click="submit">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdvancedSearch from '@/components/common/AdvancedSearch.vue'
import type { FormInstance, FormRules } from 'element-plus'
import {
  createTenant,
  deleteTenant,
  queryTenantCapabilityOverrides,
  queryTenantCapabilitySummary,
  queryTenantHistory,
  queryTenantHistorySummary,
  queryTenants,
  updateTenant,
  updateTenantCapabilityOverrides,
} from '@/api/modules'
import { queryTenantCapabilities, queryTenantPackages } from '@/api/modules'
import { useTablePreferences } from '@/composables/useTablePreferences'
import type {
  TenantCapabilityOverrideItemView,
  TenantCapabilityOverrideView,
  TenantCapabilitySummaryView,
  TenantCapabilityView,
  TenantChangeView,
  TenantHistorySummaryView,
  TenantPackageView,
  TenantView,
} from '@/types/tenant'
import { formatDateTime, toDate, toEpochMs } from '@/utils/datetime'

type OverrideRow = TenantCapabilityOverrideItemView & { overrideMode: 'inherit' | 'enable' | 'disable' }

const tenants = ref<TenantView[]>([])
const packageOptions = ref<TenantPackageView[]>([])
const capabilityOptions = ref<TenantCapabilityView[]>([])
const loading = ref(false)
const visible = ref(false)
const detailVisible = ref(false)
const historyVisible = ref(false)
const overrideVisible = ref(false)
const overrideSaving = ref(false)
const editingTenantId = ref<string | null>(null)
const detailTenant = ref<TenantView | null>(null)
const detailCapabilitySummary = ref<TenantCapabilitySummaryView | null>(null)
const detailLoading = ref(false)
const historyTenantId = ref<string | null>(null)
const overrideTenantId = ref<string | null>(null)
const originalPackageCode = ref<string>('')
const overrideBundle = ref<TenantCapabilityOverrideView | null>(null)
const overrideForm = ref<OverrideRow[]>([])
const historyRecords = ref<TenantChangeView[]>([])
const historySummary = ref<TenantHistorySummaryView>({
  tenantId: '',
  totalChanges: 0,
  packageChanges: 0,
  capabilityChanges: 0,
  statusChanges: 0,
  profileChanges: 0,
  affectedFieldCounts: {},
  recentTimeline: [],
})
const historyPage = ref(1)
const historySize = ref(10)
const historyTotal = ref(0)
const historyQuery = reactive({ changeType: '', fieldKey: '', operator: '' })
const historyDateRange = ref<[Date, Date] | null>(null)
const keyword = ref('')
const platformFilter = ref('')
const statusFilter = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const tenantLoadedAt = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  tenantId: '',
  tenantName: '',
  platformLevel: false,
  tenantStatus: 1,
  authBeginAt: null as Date | null,
  expireAt: null as Date | null,
  packageCode: '',
  packageName: '',
  userQuota: undefined as number | undefined,
  storageQuotaGb: undefined as number | undefined,
  capabilityCodes: [] as string[],
  logoUrl: '',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  website: '',
  address: '',
  lifecycleNote: '',
})
const tenantRules = reactive<FormRules>({
  tenantId: [
    { required: true, message: '请输入租户编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9:_-]{2,64}$/, message: '租户编码仅支持字母、数字、:、_、-', trigger: 'blur' },
  ],
  tenantName: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  tenantStatus: [{ required: true, message: '请选择租户状态', trigger: 'change' }],
})

const tenantTablePrefs = useTablePreferences('table:tenants', [
  { key: 'tenantId', label: '租户编码', width: 160 },
  { key: 'name', label: '租户名称', width: 180 },
  { key: 'level', label: '级别', width: 120 },
  { key: 'status', label: '状态', width: 100 },
  { key: 'package', label: '套餐', width: 220 },
  { key: 'authWindow', label: '授权期限', width: 240 },
  { key: 'contact', label: '联系人', width: 200 },
  { key: 'actions', label: '操作', width: 340 },
])

const overrideChangedCount = computed(() => overrideForm.value.filter((item) => item.overrideMode !== 'inherit').length)
const descriptionChangedCount = computed(() => overrideForm.value.filter((item) => Boolean(item.capabilityDescOverride?.trim())).length)
const overrideSummaryText = computed(() => {
  const parts: string[] = []
  if (overrideChangedCount.value) {
    parts.push(`${overrideChangedCount.value} 项策略覆盖`)
  }
  if (descriptionChangedCount.value) {
    parts.push(`${descriptionChangedCount.value} 项说明改写`)
  }
  return parts.length ? parts.join('；') : '当前完全继承套餐默认能力。'
})
const enabledTenantCount = computed(() => tenants.value.filter((item) => item.tenantStatus === 1).length)
const platformTenantCount = computed(() => tenants.value.filter((item) => item.platformLevel).length)
const tenantRefreshTip = computed(() => tenantLoadedAt.value ? `上次刷新：${formatDateTime(tenantLoadedAt.value)}` : '尚未刷新')
const selectedPackage = computed(() => packageOptions.value.find((item) => item.packageCode === form.packageCode) ?? null)
const originalPackage = computed(() => packageOptions.value.find((item) => item.packageCode === originalPackageCode.value) ?? null)
const packageChangeTip = computed(() => {
  if (!editingTenantId.value || originalPackageCode.value === form.packageCode) {
    return ''
  }
  const fromName = originalPackage.value?.packageName || originalPackageCode.value || '未设置套餐'
  const toName = selectedPackage.value?.packageName || form.packageCode || '未设置套餐'
  return `套餐将从「${fromName}」切换为「${toName}」，配额与默认能力会按新套餐重新计算。`
})
const capabilityDiffText = computed(() => {
  const packageCodes = new Set(selectedPackage.value?.capabilityCodes ?? [])
  const currentCodes = new Set(form.capabilityCodes)
  const added = [...currentCodes].filter((code) => !packageCodes.has(code))
  const disabled = [...packageCodes].filter((code) => !currentCodes.has(code))
  const parts: string[] = []
  if (added.length) {
    parts.push(`相对套餐新增 ${added.length} 项：${added.map(capabilityLabel).join('、')}`)
  }
  if (disabled.length) {
    parts.push(`相对套餐禁用 ${disabled.length} 项：${disabled.map(capabilityLabel).join('、')}`)
  }
  return parts.join('；')
})
const detailCapabilityDiffText = computed(() => {
  const summary = detailCapabilitySummary.value
  if (!summary) {
    return '正在读取租户能力范围。'
  }
  const parts: string[] = []
  if (summary.addedCapabilities.length) {
    parts.push(`相对套餐新增 ${summary.addedCapabilityCount} 项：${summary.addedCapabilities.map(capabilityLabel).join('、')}`)
  }
  if (summary.disabledCapabilities.length) {
    parts.push(`相对套餐禁用 ${summary.disabledCapabilityCount} 项：${summary.disabledCapabilities.map(capabilityLabel).join('、')}`)
  }
  return parts.length ? parts.join('；') : '当前完全继承套餐默认能力。'
})
const detailResourceSummaryText = computed(() => {
  const summary = detailCapabilitySummary.value
  if (!summary) {
    return '菜单范围随当前生效能力实时计算。'
  }
  return `实际可见 ${summary.visibleMenuCount} 项，可授权 ${summary.grantableMenuCount} 项。`
})

void bootstrap()

async function bootstrap() {
  await Promise.all([load(), loadCatalog()])
}

async function loadCatalog() {
  const [packages, capabilities] = await Promise.all([queryTenantPackages(), queryTenantCapabilities()])
  packageOptions.value = packages
  capabilityOptions.value = capabilities
}

async function load() {
  loading.value = true
  try {
    const result = await queryTenants({
      keyword: keyword.value || undefined,
      platformLevel: platformFilter.value ? platformFilter.value === 'platform' : undefined,
      tenantStatus: statusFilter.value === '' ? undefined : Number(statusFilter.value),
      page: page.value,
      size: size.value,
    })
    tenants.value = result.records
    total.value = result.total
    tenantLoadedAt.value = Date.now()
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  void load()
}

function resetSearch() {
  keyword.value = ''
  platformFilter.value = ''
  statusFilter.value = ''
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

async function openTenant(row?: TenantView) {
  editingTenantId.value = row?.tenantId ?? null
  originalPackageCode.value = row?.packageCode ?? ''
  Object.assign(form, {
    tenantId: row?.tenantId ?? '',
    tenantName: row?.name ?? '',
    platformLevel: row?.platformLevel ?? false,
    tenantStatus: row?.tenantStatus ?? 1,
    authBeginAt: toDate(row?.authBeginAt),
    expireAt: toDate(row?.expireAt),
    packageCode: row?.packageCode ?? '',
    packageName: row?.packageName ?? '',
    userQuota: row?.userQuota ?? undefined,
    storageQuotaGb: row?.storageQuotaGb ?? undefined,
    capabilityCodes: [...(row?.capabilityCodes ?? [])],
    logoUrl: row?.logoUrl ?? '',
    contactName: row?.contactName ?? '',
    contactPhone: row?.contactPhone ?? '',
    contactEmail: row?.contactEmail ?? '',
    website: row?.website ?? '',
    address: row?.address ?? '',
    lifecycleNote: row?.lifecycleNote ?? '',
  })
  if (row?.tenantId) {
    const summary = await queryTenantCapabilitySummary(row.tenantId)
    form.packageCode = summary.packageCode ?? form.packageCode
    form.packageName = summary.packageName ?? form.packageName
    form.capabilityCodes = [...summary.effectiveCapabilityCodes]
  } else if (form.packageCode) {
    syncPackage(form.packageCode)
  }
  visible.value = true
}

function syncPackage(packageCode?: string) {
  const selected = packageOptions.value.find((item) => item.packageCode === packageCode)
  if (!selected) {
    form.packageName = ''
    form.userQuota = undefined
    form.storageQuotaGb = undefined
    form.capabilityCodes = []
    return
  }
  form.packageName = selected.packageName
  form.userQuota = selected.userQuota ?? undefined
  form.storageQuotaGb = selected.storageQuotaGb ?? undefined
  form.capabilityCodes = [...selected.capabilityCodes]
}

function capabilityLabel(code: string) {
  const capability = capabilityOptions.value.find((item) => item.capabilityCode === code)
  return capability ? `${capability.capabilityName} (${code})` : code
}

async function openDetail(row: TenantView) {
  detailTenant.value = row
  detailCapabilitySummary.value = null
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailCapabilitySummary.value = await queryTenantCapabilitySummary(row.tenantId)
  } finally {
    detailLoading.value = false
  }
}

async function openHistory(row: TenantView) {
  historyTenantId.value = row.tenantId
  historyPage.value = 1
  historyVisible.value = true
  await Promise.all([loadHistory(), loadHistorySummary()])
}

async function openOverrides(row: TenantView) {
  overrideTenantId.value = row.tenantId
  overrideVisible.value = true
  const bundle = await queryTenantCapabilityOverrides(row.tenantId)
  overrideBundle.value = bundle
  overrideForm.value = bundle.overrides.map((item) => ({
    ...item,
    overrideMode: item.overrideEnabled == null ? 'inherit' : item.overrideEnabled ? 'enable' : 'disable',
  }))
}

function restoreRowOverride(index: number) {
  const current = overrideForm.value[index]
  if (!current) {
    return
  }
  current.overrideMode = 'inherit'
  current.capabilityDescOverride = ''
}

function restoreAllOverrides() {
  overrideForm.value = overrideForm.value.map((item) => ({
    ...item,
    overrideMode: 'inherit',
    capabilityDescOverride: '',
  }))
}

async function submitOverrides() {
  if (!overrideTenantId.value) {
    return
  }
  overrideSaving.value = true
  try {
    const payload = {
      overrides: overrideForm.value
        .map((item) => ({
          capabilityCode: item.capabilityCode,
          enabled: item.overrideMode === 'inherit' ? null : item.overrideMode === 'enable',
          capabilityDescOverride: item.capabilityDescOverride?.trim() || null,
        }))
        .filter((item) => item.enabled !== null || item.capabilityDescOverride),
    }
    const bundle = await updateTenantCapabilityOverrides(overrideTenantId.value, payload)
    overrideBundle.value = bundle
    overrideForm.value = bundle.overrides.map((item) => ({
      ...item,
      overrideMode: item.overrideEnabled == null ? 'inherit' : item.overrideEnabled ? 'enable' : 'disable',
    }))
    ElMessage.success('租户能力覆盖已更新')
    await load()
  } finally {
    overrideSaving.value = false
  }
}

function historyParams() {
  return {
    changeType: historyQuery.changeType || undefined,
    fieldKey: historyQuery.fieldKey || undefined,
    operator: historyQuery.operator || undefined,
    fromEpochMs: toEpochMs(historyDateRange.value?.[0]) ?? undefined,
    toEpochMs: toEpochMs(historyDateRange.value?.[1]) ?? undefined,
  }
}

async function loadHistory() {
  if (!historyTenantId.value) {
    return
  }
  const result = await queryTenantHistory(historyTenantId.value, {
    ...historyParams(),
    page: historyPage.value,
    size: historySize.value,
  })
  historyRecords.value = result.records
  historyTotal.value = result.total
}

async function loadHistorySummary() {
  if (!historyTenantId.value) {
    return
  }
  historySummary.value = await queryTenantHistorySummary(historyTenantId.value, historyParams())
}

async function applyHistorySearch() {
  historyPage.value = 1
  await Promise.all([loadHistory(), loadHistorySummary()])
}

async function resetHistorySearch() {
  historyQuery.changeType = ''
  historyQuery.fieldKey = ''
  historyQuery.operator = ''
  historyDateRange.value = null
  historyPage.value = 1
  await Promise.all([loadHistory(), loadHistorySummary()])
}

async function handleHistoryPageChange(value: number) {
  historyPage.value = value
  await loadHistory()
}

async function handleHistorySizeChange(value: number) {
  historySize.value = value
  historyPage.value = 1
  await loadHistory()
}

async function submit() {
  if (!formRef.value) {
    return
  }
  await formRef.value.validate()
  const payload = {
    tenantId: form.tenantId,
    tenantName: form.tenantName,
    platformLevel: form.platformLevel,
    tenantStatus: form.tenantStatus,
    authBeginAt: toEpochMs(form.authBeginAt),
    expireAt: toEpochMs(form.expireAt),
    packageCode: form.packageCode || null,
    packageName: form.packageName || null,
    userQuota: form.userQuota ?? null,
    storageQuotaGb: form.storageQuotaGb ?? null,
    capabilityCodes: form.capabilityCodes,
    logoUrl: form.logoUrl || null,
    contactName: form.contactName || null,
    contactPhone: form.contactPhone || null,
    contactEmail: form.contactEmail || null,
    website: form.website || null,
    address: form.address || null,
    lifecycleNote: form.lifecycleNote || null,
  }
  if (editingTenantId.value) {
    await updateTenant(editingTenantId.value, payload)
    ElMessage.success('租户已更新')
  } else {
    await createTenant(payload)
    ElMessage.success('租户已创建')
  }
  visible.value = false
  await load()
}

async function removeTenant(tenantId: string) {
  await ElMessageBox.confirm('删除租户后，相关租户数据将无法继续访问，是否继续？', '删除确认', { type: 'warning' })
  await deleteTenant(tenantId)
  ElMessage.success('租户已删除')
  await load()
}

function onTenantHeaderDragEnd(newWidth: number, _oldWidth: number, column: { property?: string; columnKey?: string }) {
  const key = String(column.columnKey || column.property || '')
  if (!key) {
    return
  }
  tenantTablePrefs.setColumnWidth(key, newWidth)
}
</script>

<style scoped>
.auth-window {
  display: grid;
  gap: 3px;
}

.table-tools__left,
.table-tools__right {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.table-tools__right {
  margin-left: auto;
}

.toolbar-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.auth-window small {
  color: var(--el-text-color-secondary);
}

.package-preview {
  display: grid;
  gap: 10px;
  margin: -6px 0 18px;
  padding: 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-fill-color-lighter);
}

.package-preview__head {
  display: grid;
  gap: 4px;
}

.package-preview__head span,
.form-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.5;
}

.form-alert {
  margin: 0 0 18px;
}

.form-tip--strong {
  margin: -8px 0 18px;
  color: var(--el-color-warning);
}

.detail-capability-panel {
  display: grid;
  gap: 16px;
  margin-top: 16px;
  min-height: 240px;
}

.detail-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.detail-block {
  display: grid;
  gap: 10px;
  padding: 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  background: var(--el-bg-color);
}

.detail-block__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.detail-block__head span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
