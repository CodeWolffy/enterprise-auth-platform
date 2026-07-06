<script setup lang="ts">
import { PERMS } from '#/constants/permissions';

import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh } from '@element-plus/icons-vue';
import {
  ElButton,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, getList } from '#/api/upms/tenant-package';
import { invokeWhenComponentReady } from '#/utils/component-ready';

const Form = defineAsyncComponent(() => import('./form.vue'));

const state = reactive({ tableData: [] as any[] });
const loading = ref(false);
const formRef = ref();
const formMounted = ref(false);

const initPage = async () => {
  loading.value = true;
  try {
    state.tableData = (await getList()) as any[];
  } finally {
    loading.value = false;
  }
};
const openForm = (row?: any) => {
  formMounted.value = true;
  void invokeWhenComponentReady(formRef, (form: any) => form.initForm(row));
};
const add = () => openForm();
const edit = (row: any) => openForm(row);
const del = (id: number | string) => {
  ElMessageBox.confirm('此操作将删除该套餐，是否继续?', '提示', {
    cancelButtonText: '取消',
    confirmButtonText: '确认',
    type: 'warning',
  }).then(() => {
    delObj(id)
      .then(() => {
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};

initPage();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <div
        class="hx-table-toolbar"
        style="display: flex; gap: 8px; margin-bottom: 12px"
      >
        <ElButton
          v-access:code="PERMS.upms.tenantPackage.add"
          :icon="Plus"
          type="primary"
          @click="add"
        >
          新增
        </ElButton>
        <ElButton :icon="Refresh" @click="initPage"> 刷新 </ElButton>
      </div>

      <Form v-if="formMounted" ref="formRef" @init-page="initPage" />

      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn label="套餐名称" prop="packageName" />
        <ElTableColumn label="套餐编码" prop="packageCode" />
        <ElTableColumn label="原价（元）" prop="originalPrice" width="120" />
        <ElTableColumn label="销售价（元）" prop="salesPrice" width="120" />
        <ElTableColumn label="应用标识" prop="appKey" show-overflow-tooltip />
        <ElTableColumn label="状态" width="90">
          <template #default="scope">
            <ElTag :type="scope.row.status === '0' ? 'success' : 'info'">
              {{ scope.row.status === '0' ? '正常' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn align="center" fixed="right" label="操作" width="200">
          <template #default="scope">
            <ElButton
              v-access:code="PERMS.upms.tenantPackage.edit"
              :icon="Edit"
              link
              type="primary"
              @click="edit(scope.row)"
            >
              修改
            </ElButton>
            <ElButton
              v-access:code="PERMS.upms.tenantPackage.del"
              :icon="Delete"
              link
              type="danger"
              @click="del(scope.row.id)"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
    </div>
  </div>
</template>
