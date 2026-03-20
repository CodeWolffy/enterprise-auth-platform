<template>
  <div class="panel-stack">
    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Tenants</span>
          <h3>租户管理</h3>
        </div>
        <el-button type="primary" @click="openTenant()">新增租户</el-button>
      </div>

      <el-table :data="tenants" stripe>
        <el-table-column prop="tenantId" label="租户编码" min-width="140" />
        <el-table-column prop="name" label="租户名称" min-width="160" />
        <el-table-column label="级别" min-width="100">
          <template #default="{ row }">{{ row.platformLevel ? '平台级' : '业务租户' }}</template>
        </el-table-column>
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }">{{ row.tenantStatus === 1 ? '启用' : '禁用' }}</template>
        </el-table-column>
        <el-table-column prop="expireAt" label="到期时间" min-width="180" />
        <el-table-column fixed="right" label="操作" width="160">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTenant(row)">编辑</el-button>
            <el-button link type="danger" @click="removeTenant(row.tenantId)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="visible" :title="editingTenantId ? '编辑租户' : '新增租户'" width="660px">
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="租户编码"><el-input v-model="form.tenantId" :disabled="Boolean(editingTenantId)" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="租户名称"><el-input v-model="form.tenantName" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="租户级别">
              <el-switch v-model="form.platformLevel" inline-prompt active-text="平台级" inactive-text="业务" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="租户状态">
              <el-select v-model="form.tenantStatus" style="width: 100%">
                <el-option label="启用" :value="1" />
                <el-option label="禁用" :value="0" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="到期时间">
          <el-date-picker v-model="form.expireAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createTenant, deleteTenant, queryTenants, updateTenant } from '@/api/platform'
import type { TenantView } from '@/types/auth'

const tenants = ref<TenantView[]>([])
const visible = ref(false)
const editingTenantId = ref<string | null>(null)
const form = reactive({
  tenantId: '',
  tenantName: '',
  platformLevel: false,
  tenantStatus: 1,
  expireAt: '',
})

void load()

async function load() {
  tenants.value = await queryTenants()
}

function openTenant(row?: TenantView) {
  editingTenantId.value = row?.tenantId ?? null
  Object.assign(form, {
    tenantId: row?.tenantId ?? '',
    tenantName: row?.name ?? '',
    platformLevel: row?.platformLevel ?? false,
    tenantStatus: row?.tenantStatus ?? 1,
    expireAt: row?.expireAt ?? '',
  })
  visible.value = true
}

async function submit() {
  const payload = {
    tenantId: form.tenantId,
    tenantName: form.tenantName,
    platformLevel: form.platformLevel,
    tenantStatus: form.tenantStatus,
    expireAt: form.expireAt || null,
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
  await ElMessageBox.confirm('删除租户后相关租户数据将不可继续访问，是否继续？', '删除确认', { type: 'warning' })
  await deleteTenant(tenantId)
  ElMessage.success('租户已删除')
  await load()
}
</script>
