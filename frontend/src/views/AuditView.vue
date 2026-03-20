<template>
  <div class="panel-stack">
    <section class="dashboard-panel">
      <div class="panel-head">
        <div>
          <span class="eyebrow">Audit</span>
          <h3>安全审计</h3>
        </div>
      </div>

      <el-form inline class="toolbar-inline">
        <el-form-item label="事件类型"><el-input v-model="query.eventType" placeholder="例如 USER_UPDATED" clearable /></el-form-item>
        <el-form-item label="操作人"><el-input v-model="query.operator" clearable /></el-form-item>
        <el-form-item label="请求 ID"><el-input v-model="query.requestId" clearable /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="page.records" stripe>
        <el-table-column prop="type" label="事件类型" min-width="160" />
        <el-table-column prop="operator" label="操作人" min-width="120" />
        <el-table-column prop="tenantId" label="租户" min-width="120" />
        <el-table-column prop="requestId" label="请求 ID" min-width="180" />
        <el-table-column prop="clientIp" label="客户端 IP" min-width="140" />
        <el-table-column prop="occurredAt" label="发生时间" min-width="180" />
        <el-table-column label="事件明细" min-width="220">
          <template #default="{ row }">
            <el-popover placement="left" :width="420" trigger="click">
              <template #reference>
                <el-button link type="primary">查看明细</el-button>
              </template>
              <pre class="json-pre">{{ JSON.stringify(row.details, null, 2) }}</pre>
            </el-popover>
          </template>
        </el-table-column>
      </el-table>

      <div class="toolbar-inline" style="margin-top: 16px">
        <span>共 {{ page.total }} 条记录</span>
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="query.page"
          :page-size="query.size"
          :total="page.total"
          @current-change="handlePageChange"
        />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { queryAuditEvents } from '@/api/platform'
import type { AuditPage } from '@/types/auth'

const query = reactive({
  eventType: '',
  operator: '',
  requestId: '',
  page: 1,
  size: 20,
})

const page = ref<AuditPage>({
  total: 0,
  page: 1,
  size: 20,
  records: [],
})

void load()

async function load() {
  page.value = await queryAuditEvents({
    eventType: query.eventType || undefined,
    operator: query.operator || undefined,
    requestId: query.requestId || undefined,
    page: query.page,
    size: query.size,
  })
}

async function handlePageChange(nextPage: number) {
  query.page = nextPage
  await load()
}
</script>
