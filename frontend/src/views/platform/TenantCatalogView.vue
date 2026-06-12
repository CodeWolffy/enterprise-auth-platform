<template>
  <div class="panel-stack">
    <section class="dashboard-grid">
      <article class="stat-card">
        <span class="eyebrow">套餐</span>
        <strong>{{ packages.length }}</strong>
        <span>当前定义的租户套餐</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">能力</span>
        <strong>{{ capabilities.length }}</strong>
        <span>当前定义的租户能力</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">启用套餐</span>
        <strong>{{ enabledPackageCount }}</strong>
        <span>启用中的套餐数量</span>
      </article>
      <article class="stat-card">
        <span class="eyebrow">启用能力</span>
        <strong>{{ enabledCapabilityCount }}</strong>
        <span>启用中的能力数量</span>
      </article>
    </section>

    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">租户套餐</span>
          <h3>租户套餐与能力</h3>
        </div>
        <div class="panel-actions">
          <span class="toolbar-tip">{{ catalogRefreshTip }}</span>
          <el-button :loading="loadingPackages || loadingCapabilities" @click="refreshCatalog">刷新</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="套餐管理" name="packages">
          <div class="panel-head sub-head">
            <div>
<span class="eyebrow">套餐</span>
              <h4>套餐定义</h4>
            </div>
            <el-button v-permission="'upms:tenantcatalog:add'" type="primary" @click="openPackageDialog()">新增套餐</el-button>
          </div>
          <div class="table-tools">
            <el-radio-group v-model="packageTablePrefs.density" size="small">
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
                  v-for="item in packageTablePrefs.columns"
                  :key="item.key"
                  :model-value="packageTablePrefs.visibleColumnMap[item.key]"
                  @change="(value: boolean) => packageTablePrefs.setColumnVisible(item.key, value)"
                >
                  {{ item.label }}
                </el-checkbox>
              </div>
            </el-popover>
            <el-button size="small" @click="packageTablePrefs.reset()">恢复默认</el-button>
          </div>
          <el-table
            v-loading="loadingPackages"
            :data="packages"
            stripe
            :class="`table-density-${packageTablePrefs.density}`"
            @header-dragend="onPackageHeaderDragEnd"
          >
            <el-table-column
              v-if="packageTablePrefs.visibleColumnMap.packageCode"
              column-key="packageCode"
              prop="packageCode"
              label="套餐编码"
              min-width="160"
              :width="packageTablePrefs.getColumnWidth('packageCode')"
            />
            <el-table-column
              v-if="packageTablePrefs.visibleColumnMap.packageName"
              column-key="packageName"
              label="套餐名称"
              min-width="220"
              :width="packageTablePrefs.getColumnWidth('packageName')"
            >
              <template #default="{ row }">
                <div class="package-name-cell">
                  <strong>{{ row.packageName }}</strong>
                  <span>{{ row.subtitle || row.packageDesc || '未配置运营副标题' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="packageTablePrefs.visibleColumnMap.appKey"
              column-key="appKey"
              prop="appKey"
              label="应用标识"
              min-width="130"
              :width="packageTablePrefs.getColumnWidth('appKey')"
            />
            <el-table-column
              v-if="packageTablePrefs.visibleColumnMap.price"
              column-key="price"
              label="价格"
              min-width="140"
              :width="packageTablePrefs.getColumnWidth('price')"
            >
              <template #default="{ row }">
                <div class="price-cell">
                  <strong>{{ formatPrice(row.salesPrice) }}</strong>
                  <span v-if="row.originalPrice">原价 {{ formatPrice(row.originalPrice) }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="packageTablePrefs.visibleColumnMap.resourceImpact"
              column-key="resourceImpact"
              label="影响资源"
              min-width="130"
              :width="packageTablePrefs.getColumnWidth('resourceImpact')"
            >
              <template #default="{ row }">
                <div class="resource-impact-cell">
                  <span>可见 {{ row.visibleResourceCount || 0 }}</span>
                  <span>授权 {{ row.grantResourceCount || 0 }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              v-if="packageTablePrefs.visibleColumnMap.capabilityCodes"
              column-key="capabilityCodes"
              label="能力集合"
              min-width="240"
              :width="packageTablePrefs.getColumnWidth('capabilityCodes')"
            >
              <template #default="{ row }">
                <el-tag v-for="code in row.capabilityCodes" :key="code" class="scope-tag" size="small">{{ code }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column
              v-if="packageTablePrefs.visibleColumnMap.referencedTenantCount"
              column-key="referencedTenantCount"
              label="引用租户"
              :width="packageTablePrefs.getColumnWidth('referencedTenantCount') || 110"
            >
              <template #default="{ row }">
                <el-tag type="info" effect="plain">{{ row.referencedTenantCount || 0 }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column
              v-if="packageTablePrefs.visibleColumnMap.enabled"
              column-key="enabled"
              label="状态"
              :width="packageTablePrefs.getColumnWidth('enabled') || 100"
            >
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column
              v-if="packageTablePrefs.visibleColumnMap.actions"
              column-key="actions"
              fixed="right"
              label="操作"
              :width="packageTablePrefs.getColumnWidth('actions') || 220"
            >
              <template #default="{ row }">
                <el-button link type="primary" @click="openPackageDetail(row)">详情</el-button>
                <el-button v-permission="'upms:tenantcatalog:edit'" link type="primary" @click="openPackageDialog(row)">编辑</el-button>
                <el-button v-permission="'upms:tenantcatalog:del'" link type="danger" @click="removePackage(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="能力管理" name="capabilities">
          <div class="panel-head sub-head">
            <div>
<span class="eyebrow">能力</span>
              <h4>能力定义</h4>
            </div>
            <el-button v-permission="'upms:tenantcatalog:add'" type="primary" @click="openCapabilityDialog()">新增能力</el-button>
          </div>
          <div class="table-tools">
            <el-radio-group v-model="capabilityTablePrefs.density" size="small">
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
                  v-for="item in capabilityTablePrefs.columns"
                  :key="item.key"
                  :model-value="capabilityTablePrefs.visibleColumnMap[item.key]"
                  @change="(value: boolean) => capabilityTablePrefs.setColumnVisible(item.key, value)"
                >
                  {{ item.label }}
                </el-checkbox>
              </div>
            </el-popover>
            <el-button size="small" @click="capabilityTablePrefs.reset()">恢复默认</el-button>
          </div>
          <el-table
            v-loading="loadingCapabilities"
            :data="capabilities"
            stripe
            :class="`table-density-${capabilityTablePrefs.density}`"
            @header-dragend="onCapabilityHeaderDragEnd"
          >
            <el-table-column
              v-if="capabilityTablePrefs.visibleColumnMap.capabilityCode"
              column-key="capabilityCode"
              prop="capabilityCode"
              label="能力编码"
              min-width="180"
              :width="capabilityTablePrefs.getColumnWidth('capabilityCode')"
            />
            <el-table-column
              v-if="capabilityTablePrefs.visibleColumnMap.capabilityName"
              column-key="capabilityName"
              prop="capabilityName"
              label="能力名称"
              min-width="180"
              :width="capabilityTablePrefs.getColumnWidth('capabilityName')"
            />
            <el-table-column
              v-if="capabilityTablePrefs.visibleColumnMap.capabilityDesc"
              column-key="capabilityDesc"
              prop="capabilityDesc"
              label="能力说明"
              min-width="260"
              show-overflow-tooltip
              :width="capabilityTablePrefs.getColumnWidth('capabilityDesc')"
            />
            <el-table-column
              v-if="capabilityTablePrefs.visibleColumnMap.referencedPackageCount"
              column-key="referencedPackageCount"
              label="引用套餐"
              :width="capabilityTablePrefs.getColumnWidth('referencedPackageCount') || 110"
            >
              <template #default="{ row }">
                <el-tag type="info" effect="plain">{{ row.referencedPackageCount || 0 }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column
              v-if="capabilityTablePrefs.visibleColumnMap.sortOrder"
              column-key="sortOrder"
              prop="sortOrder"
              label="排序"
              :width="capabilityTablePrefs.getColumnWidth('sortOrder') || 90"
            />
            <el-table-column
              v-if="capabilityTablePrefs.visibleColumnMap.enabled"
              column-key="enabled"
              label="状态"
              :width="capabilityTablePrefs.getColumnWidth('enabled') || 100"
            >
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column
              v-if="capabilityTablePrefs.visibleColumnMap.actions"
              column-key="actions"
              fixed="right"
              label="操作"
              :width="capabilityTablePrefs.getColumnWidth('actions') || 220"
            >
              <template #default="{ row }">
                <el-button link type="primary" @click="openCapabilityDetail(row)">详情</el-button>
                <el-button v-permission="'upms:tenantcatalog:edit'" link type="primary" @click="openCapabilityDialog(row)">编辑</el-button>
                <el-button v-permission="'upms:tenantcatalog:del'" link type="danger" @click="removeCapability(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="packageVisible" :title="editingPackageId ? '编辑套餐' : '新增套餐'" width="720px">
      <el-form ref="packageFormRef" :model="packageForm" :rules="packageRules" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="套餐编码" prop="packageCode">
              <el-input v-model="packageForm.packageCode" :disabled="Boolean(editingPackageId)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="套餐名称" prop="packageName">
              <el-input v-model="packageForm.packageName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="运营副标题">
              <el-input v-model="packageForm.subtitle" placeholder="用于套餐卡片展示" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="应用标识">
              <el-input v-model="packageForm.appKey" placeholder="例如 app_base" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="销售价">
              <el-input-number v-model="packageForm.salesPrice" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="原价">
              <el-input-number v-model="packageForm.originalPrice" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="展示排序">
              <el-input-number v-model="packageForm.orderNo" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="用户配额">
              <el-input-number v-model="packageForm.userQuota" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="存储配额(GB)">
              <el-input-number v-model="packageForm.storageQuotaGb" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="套餐说明">
          <el-input v-model="packageForm.packageDesc" type="textarea" :rows="3" placeholder="列表和摘要展示" />
        </el-form-item>
        <el-form-item label="详细描述">
          <el-input v-model="packageForm.descriptionMd" type="textarea" :rows="4" placeholder="支持富文本或 Markdown 内容" />
        </el-form-item>
        <el-form-item label="能力集合" prop="capabilityCodes">
          <el-select v-model="packageForm.capabilityCodes" multiple style="width: 100%">
            <el-option
              v-for="item in capabilities"
              :key="item.capabilityCode"
              :label="`${item.capabilityCode} (${item.capabilityName})`"
              :value="item.capabilityCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="是否启用">
          <el-switch v-model="packageForm.enabled" inline-prompt active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="packageVisible = false">取消</el-button>
        <el-button v-permission="['upms:tenantcatalog:add', 'upms:tenantcatalog:edit']" type="primary" @click="submitPackage">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="capabilityVisible" :title="editingCapabilityId ? '编辑能力' : '新增能力'" width="640px">
      <el-form ref="capabilityFormRef" :model="capabilityForm" :rules="capabilityRules" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="能力编码" prop="capabilityCode">
              <el-input v-model="capabilityForm.capabilityCode" :disabled="Boolean(editingCapabilityId)" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="能力名称" prop="capabilityName">
              <el-input v-model="capabilityForm.capabilityName" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="排序值">
              <el-input-number v-model="capabilityForm.sortOrder" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否启用">
              <el-switch v-model="capabilityForm.enabled" inline-prompt active-text="启用" inactive-text="停用" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="能力说明">
          <el-input v-model="capabilityForm.capabilityDesc" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="capabilityVisible = false">取消</el-button>
        <el-button v-permission="['upms:tenantcatalog:add', 'upms:tenantcatalog:edit']" type="primary" @click="submitCapability">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="packageDetailVisible" title="套餐详情" size="560px">
      <template v-if="detailPackage">
        <el-descriptions :column="1" border class="drawer-section drawer-section--overview">
          <el-descriptions-item label="套餐编码">{{ detailPackage.packageCode }}</el-descriptions-item>
          <el-descriptions-item label="套餐名称">{{ detailPackage.packageName }}</el-descriptions-item>
          <el-descriptions-item label="运营副标题">{{ detailPackage.subtitle || '-' }}</el-descriptions-item>
          <el-descriptions-item label="应用标识">{{ detailPackage.appKey || '-' }}</el-descriptions-item>
          <el-descriptions-item label="价格">
            {{ formatPrice(detailPackage.salesPrice) }}
            <span v-if="detailPackage.originalPrice"> / 原价 {{ formatPrice(detailPackage.originalPrice) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="用户配额">{{ detailPackage.userQuota ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="存储配额(GB)">{{ detailPackage.storageQuotaGb ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="套餐说明">{{ detailPackage.packageDesc || '未配置说明' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailPackage.enabled ? '启用' : '停用' }}</el-descriptions-item>
          <el-descriptions-item label="引用租户数">{{ detailPackage.referencedTenantCount || 0 }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-block drawer-section drawer-section--scopes">
          <div class="eyebrow">能力清单</div>
          <div class="tag-wrap">
            <el-tag v-for="code in detailPackage.capabilityCodes" :key="code" type="info" effect="plain">{{ code }}</el-tag>
          </div>
        </div>

        <div class="detail-block drawer-section drawer-section--scopes" v-loading="packageImpactLoading">
          <div class="eyebrow">能力影响范围</div>
          <div class="impact-grid">
            <div>
              <strong>{{ detailPackageImpact?.visibleResourceCount ?? detailPackage.visibleResourceCount ?? 0 }}</strong>
              <span>可见资源</span>
            </div>
            <div>
              <strong>{{ detailPackageImpact?.grantResourceCount ?? detailPackage.grantResourceCount ?? 0 }}</strong>
              <span>可授权资源</span>
            </div>
            <div>
              <strong>{{ detailPackageImpact?.referencedTenantCount ?? detailPackage.referencedTenantCount ?? 0 }}</strong>
              <span>引用租户</span>
            </div>
          </div>
          <div v-if="(detailPackageImpact?.sampleResourceKeys?.length || detailPackage.sampleResourceKeys?.length)" class="tag-wrap" style="margin-top: 10px">
            <el-tag
              v-for="key in detailPackageImpact?.sampleResourceKeys || detailPackage.sampleResourceKeys"
              :key="key"
              type="success"
              effect="plain"
            >
              {{ key }}
            </el-tag>
          </div>
        </div>

        <div v-if="detailPackageImpact?.rules?.length" class="detail-block drawer-section drawer-section--guide">
          <div class="eyebrow">影响规则</div>
          <div v-if="activeRules(detailPackageImpact.rules).length" class="rule-list">
            <el-tag v-for="rule in activeRules(detailPackageImpact.rules)" :key="rule.ruleCode" :type="ruleTagType(rule)" effect="plain">
              {{ rule.message }}
            </el-tag>
          </div>
          <el-alert v-else title="当前未命中风险规则。" type="success" :closable="false" style="margin-top: 8px" />
        </div>

        <div class="detail-block drawer-section drawer-section--guide" v-if="detailPackage.descriptionMd">
          <div class="eyebrow">详细描述</div>
          <p class="description-text">{{ detailPackage.descriptionMd }}</p>
        </div>

        <div class="detail-block drawer-section drawer-section--guide">
          <div class="eyebrow">引用提示</div>
          <el-alert
            v-if="(detailPackage.referencedTenantCount || 0) > 0"
            :title="`当前套餐被 ${detailPackage.referencedTenantCount} 个租户使用，删除或变更前请先评估租户迁移。`"
            type="warning"
            :closable="false"
            style="margin-top: 8px"
          />
          <el-alert
            v-else
            title="当前套餐尚未被租户引用，可按需调整。"
            type="success"
            :closable="false"
            style="margin-top: 8px"
          />
          <div v-if="detailPackage.referencedTenantIds?.length" class="tag-wrap" style="margin-top: 10px">
            <el-tag v-for="tenantId in detailPackage.referencedTenantIds" :key="tenantId">{{ tenantId }}</el-tag>
          </div>
          <ul v-if="detailPackageImpact?.recommendedActions?.length" class="action-list">
            <li v-for="item in detailPackageImpact.recommendedActions" :key="item">{{ item }}</li>
          </ul>
        </div>
      </template>
    </el-drawer>

    <el-drawer v-model="capabilityDetailVisible" title="能力详情" size="560px">
      <template v-if="detailCapability">
        <el-descriptions :column="1" border class="drawer-section drawer-section--overview">
          <el-descriptions-item label="能力编码">{{ detailCapability.capabilityCode }}</el-descriptions-item>
          <el-descriptions-item label="能力名称">{{ detailCapability.capabilityName }}</el-descriptions-item>
          <el-descriptions-item label="能力说明">{{ detailCapability.capabilityDesc || '未配置说明' }}</el-descriptions-item>
          <el-descriptions-item label="排序值">{{ detailCapability.sortOrder ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detailCapability.enabled ? '启用' : '停用' }}</el-descriptions-item>
          <el-descriptions-item label="引用套餐数">{{ detailCapability.referencedPackageCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="覆盖租户数">{{ detailCapability.referencedTenantCount || 0 }}</el-descriptions-item>
          <el-descriptions-item label="覆盖记录数">{{ detailCapability.overrideReferenceCount || 0 }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-block drawer-section drawer-section--guide" v-loading="capabilityImpactLoading">
          <div class="eyebrow">引用提示</div>
          <el-alert
            v-if="(detailCapabilityImpact?.referencedPackageCount ?? detailCapability.referencedPackageCount ?? 0) > 0 || (detailCapabilityImpact?.overrideReferenceCount ?? detailCapability.overrideReferenceCount ?? 0) > 0"
            title="该能力存在引用关系，删除前请先解除套餐绑定并清理租户覆盖配置。"
            type="warning"
            :closable="false"
            style="margin-top: 8px"
          />
          <el-alert
            v-else
            title="当前能力无引用关系，可按需调整。"
            type="success"
            :closable="false"
            style="margin-top: 8px"
          />
          <div v-if="(detailCapabilityImpact?.referencedPackageCodes?.length || detailCapability.referencedPackageCodes?.length)" class="tag-wrap" style="margin-top: 10px">
            <el-tag
              v-for="pkg in detailCapabilityImpact?.referencedPackageCodes || detailCapability.referencedPackageCodes"
              :key="pkg"
              type="info"
              effect="plain"
            >套餐: {{ pkg }}</el-tag>
          </div>
          <div v-if="(detailCapabilityImpact?.referencedTenantIds?.length || detailCapability.referencedTenantIds?.length)" class="tag-wrap" style="margin-top: 10px">
            <el-tag v-for="tenantId in detailCapabilityImpact?.referencedTenantIds || detailCapability.referencedTenantIds" :key="tenantId">租户: {{ tenantId }}</el-tag>
          </div>
          <div v-if="detailCapabilityImpact?.rules?.length" class="rule-list">
            <el-tag v-for="rule in activeRules(detailCapabilityImpact.rules)" :key="rule.ruleCode" :type="ruleTagType(rule)" effect="plain">
              {{ rule.message }}
            </el-tag>
          </div>
          <ul v-if="detailCapabilityImpact?.recommendedActions?.length" class="action-list">
            <li v-for="item in detailCapabilityImpact.recommendedActions" :key="item">{{ item }}</li>
          </ul>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useTablePreferences } from '@/composables/useTablePreferences'
import {
  createTenantCapability,
  createTenantPackage,
  deleteTenantCapability,
  deleteTenantPackage,
  queryTenantCapabilities,
  queryTenantCapabilityImpact,
  queryTenantPackageImpact,
  queryTenantPackages,
  updateTenantCapability,
  updateTenantPackage,
} from '@/api/modules'
import type {
  ImpactRuleView,
  TenantCapabilityImpactView,
  TenantCapabilityView,
  TenantPackageImpactView,
  TenantPackageView,
} from '@/types/tenant'

const activeTab = ref<'packages' | 'capabilities'>('packages')
const loadingPackages = ref(false)
const loadingCapabilities = ref(false)
const packageImpactLoading = ref(false)
const capabilityImpactLoading = ref(false)
const packageVisible = ref(false)
const capabilityVisible = ref(false)
const packageDetailVisible = ref(false)
const capabilityDetailVisible = ref(false)
const editingPackageId = ref<number | null>(null)
const editingCapabilityId = ref<number | null>(null)
const packageFormRef = ref<FormInstance>()
const capabilityFormRef = ref<FormInstance>()
const packages = ref<TenantPackageView[]>([])
const capabilities = ref<TenantCapabilityView[]>([])
const catalogLoadedAt = ref<number | null>(null)
const detailPackage = ref<TenantPackageView | null>(null)
const detailPackageImpact = ref<TenantPackageImpactView | null>(null)
const detailCapability = ref<TenantCapabilityView | null>(null)
const detailCapabilityImpact = ref<TenantCapabilityImpactView | null>(null)
const packageTablePrefs = useTablePreferences('eap.table.tenant.catalog.packages', [
  { key: 'packageCode', label: '套餐编码', width: 150 },
  { key: 'packageName', label: '套餐名称', width: 220 },
  { key: 'appKey', label: '应用标识', width: 130 },
  { key: 'price', label: '价格', width: 140 },
  { key: 'resourceImpact', label: '影响资源', width: 130 },
  { key: 'capabilityCodes', label: '能力集合', width: 240 },
  { key: 'referencedTenantCount', label: '引用租户', width: 110 },
  { key: 'enabled', label: '状态', width: 100 },
  { key: 'actions', label: '操作', width: 220 },
])
const capabilityTablePrefs = useTablePreferences('eap.table.tenant.catalog.capabilities', [
  { key: 'capabilityCode', label: '能力编码', width: 180 },
  { key: 'capabilityName', label: '能力名称', width: 180 },
  { key: 'capabilityDesc', label: '能力说明', width: 260 },
  { key: 'referencedPackageCount', label: '引用套餐', width: 110 },
  { key: 'sortOrder', label: '排序', width: 90 },
  { key: 'enabled', label: '状态', width: 100 },
  { key: 'actions', label: '操作', width: 220 },
])

const packageForm = reactive({
  packageCode: '',
  packageName: '',
  subtitle: '',
  salesPrice: undefined as number | undefined,
  originalPrice: undefined as number | undefined,
  descriptionMd: '',
  appKey: '',
  orderNo: 0,
  userQuota: 0,
  storageQuotaGb: 0,
  packageDesc: '',
  enabled: true,
  capabilityCodes: [] as string[],
})

const capabilityForm = reactive({
  capabilityCode: '',
  capabilityName: '',
  capabilityDesc: '',
  sortOrder: 0,
  enabled: true,
})

const packageRules = reactive<FormRules>({
  packageCode: [
    { required: true, message: '请输入套餐编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9:_-]{2,64}$/, message: '套餐编码格式不正确', trigger: 'blur' },
  ],
  packageName: [{ required: true, message: '请输入套餐名称', trigger: 'blur' }],
  capabilityCodes: [{ required: true, message: '请至少选择一个能力', trigger: 'change' }],
})

const capabilityRules = reactive<FormRules>({
  capabilityCode: [
    { required: true, message: '请输入能力编码', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9:_-]{2,64}$/, message: '能力编码格式不正确', trigger: 'blur' },
  ],
  capabilityName: [{ required: true, message: '请输入能力名称', trigger: 'blur' }],
})

const enabledPackageCount = computed(() => packages.value.filter((item) => item.enabled).length)
const enabledCapabilityCount = computed(() => capabilities.value.filter((item) => item.enabled).length)
const catalogRefreshTip = computed(() => catalogLoadedAt.value ? `上次刷新：${new Date(catalogLoadedAt.value).toLocaleString()}` : '尚未刷新')

void refreshCatalog()

async function refreshCatalog() {
  await Promise.all([loadPackages(), loadCapabilities()])
  catalogLoadedAt.value = Date.now()
}

async function loadPackages() {
  loadingPackages.value = true
  try {
    packages.value = await queryTenantPackages()
  } finally {
    loadingPackages.value = false
  }
}

async function loadCapabilities() {
  loadingCapabilities.value = true
  try {
    capabilities.value = await queryTenantCapabilities()
  } finally {
    loadingCapabilities.value = false
  }
}

function openPackageDialog(row?: TenantPackageView) {
  editingPackageId.value = row?.id ?? null
  packageForm.packageCode = row?.packageCode ?? ''
  packageForm.packageName = row?.packageName ?? ''
  packageForm.subtitle = row?.subtitle ?? ''
  packageForm.salesPrice = row?.salesPrice ?? undefined
  packageForm.originalPrice = row?.originalPrice ?? undefined
  packageForm.descriptionMd = row?.descriptionMd ?? ''
  packageForm.appKey = row?.appKey ?? ''
  packageForm.orderNo = row?.orderNo ?? 0
  packageForm.userQuota = row?.userQuota ?? 0
  packageForm.storageQuotaGb = row?.storageQuotaGb ?? 0
  packageForm.packageDesc = row?.packageDesc ?? ''
  packageForm.enabled = row?.enabled ?? true
  packageForm.capabilityCodes = [...(row?.capabilityCodes ?? [])]
  packageVisible.value = true
}

function openCapabilityDialog(row?: TenantCapabilityView) {
  editingCapabilityId.value = row?.id ?? null
  capabilityForm.capabilityCode = row?.capabilityCode ?? ''
  capabilityForm.capabilityName = row?.capabilityName ?? ''
  capabilityForm.capabilityDesc = row?.capabilityDesc ?? ''
  capabilityForm.sortOrder = row?.sortOrder ?? 0
  capabilityForm.enabled = row?.enabled ?? true
  capabilityVisible.value = true
}

async function openPackageDetail(row: TenantPackageView) {
  detailPackage.value = row
  detailPackageImpact.value = null
  packageDetailVisible.value = true
  packageImpactLoading.value = true
  try {
    detailPackageImpact.value = await queryTenantPackageImpact(row.id)
  } finally {
    packageImpactLoading.value = false
  }
}

async function openCapabilityDetail(row: TenantCapabilityView) {
  detailCapability.value = row
  detailCapabilityImpact.value = null
  capabilityDetailVisible.value = true
  capabilityImpactLoading.value = true
  try {
    detailCapabilityImpact.value = await queryTenantCapabilityImpact(row.id)
  } finally {
    capabilityImpactLoading.value = false
  }
}

async function submitPackage() {
  await packageFormRef.value?.validate()
  const payload = { ...packageForm }
  if (editingPackageId.value) {
    await updateTenantPackage(editingPackageId.value, payload)
    ElMessage.success('套餐已更新')
  } else {
    await createTenantPackage(payload)
    ElMessage.success('套餐已创建')
  }
  packageVisible.value = false
  await loadPackages()
}

async function submitCapability() {
  await capabilityFormRef.value?.validate()
  const payload = { ...capabilityForm }
  if (editingCapabilityId.value) {
    await updateTenantCapability(editingCapabilityId.value, payload)
    ElMessage.success('能力已更新')
  } else {
    await createTenantCapability(payload)
    ElMessage.success('能力已创建')
  }
  capabilityVisible.value = false
  await Promise.all([loadCapabilities(), loadPackages()])
}

async function removePackage(row: TenantPackageView) {
  const impact = await queryTenantPackageImpact(row.id)
  const blockingRules = impact.rules.filter((rule) => rule.hit && rule.blocking)
  if (blockingRules.length > 0) {
    ElMessage.warning(blockingRules[0].message)
    return
  }
  await ElMessageBox.confirm(`确认删除套餐 ${row.packageName} 吗？`, '删除确认', { type: 'warning' })
  await deleteTenantPackage(row.id)
  ElMessage.success('套餐已删除')
  await loadPackages()
}

async function removeCapability(row: TenantCapabilityView) {
  const impact = await queryTenantCapabilityImpact(row.id)
  const blockingRules = impact.rules.filter((rule) => rule.hit && rule.blocking)
  if (blockingRules.length > 0) {
    ElMessage.warning(blockingRules[0].message)
    return
  }
  await ElMessageBox.confirm(`确认删除能力 ${row.capabilityName} 吗？`, '删除确认', { type: 'warning' })
  await deleteTenantCapability(row.id)
  ElMessage.success('能力已删除')
  await Promise.all([loadCapabilities(), loadPackages()])
}

function onPackageHeaderDragEnd(newWidth: number, _oldWidth: number, column: { property?: string; columnKey?: string }) {
  const key = String(column.columnKey || column.property || '')
  if (!key) {
    return
  }
  packageTablePrefs.setColumnWidth(key, newWidth)
}

function formatPrice(value?: number | null) {
  if (value === null || value === undefined) {
    return '未配置'
  }
  return `¥${Number(value).toFixed(2)}`
}

function activeRules(rules: ImpactRuleView[]) {
  return rules.filter((rule) => rule.hit)
}

function ruleTagType(rule: ImpactRuleView) {
  if (rule.blocking || rule.level === 'ERROR') {
    return 'danger'
  }
  if (rule.level === 'WARN') {
    return 'warning'
  }
  return 'info'
}

function onCapabilityHeaderDragEnd(newWidth: number, _oldWidth: number, column: { property?: string; columnKey?: string }) {
  const key = String(column.columnKey || column.property || '')
  if (!key) {
    return
  }
  capabilityTablePrefs.setColumnWidth(key, newWidth)
}
</script>

<style scoped lang="scss">
.sub-head {
  margin-bottom: 16px;
}

.toolbar-tip {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.scope-tag {
  margin-right: 6px;
  margin-bottom: 6px;
}

.package-name-cell,
.price-cell,
.resource-impact-cell {
  display: grid;
  gap: 4px;
}

.package-name-cell strong,
.price-cell strong,
.impact-grid strong {
  color: #0f172a;
}

.package-name-cell span,
.price-cell span,
.resource-impact-cell span,
.description-text,
.action-list {
  color: #64748b;
  font-size: 12px;
}

.impact-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 10px;
}

.impact-grid > div {
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 10px;
  background: #f8fafc;
  display: grid;
  gap: 4px;
}

.description-text {
  margin: 8px 0 0;
  line-height: 1.7;
  white-space: pre-wrap;
}

.rule-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.action-list {
  margin: 10px 0 0;
  padding-left: 18px;
  line-height: 1.7;
}
</style>
