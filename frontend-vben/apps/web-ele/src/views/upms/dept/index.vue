<script lang="ts" setup>
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

import { delObj, getTreeList } from '#/api/upms/dept';
import { invokeWhenComponentReady } from '#/utils/component-ready';

const Form = defineAsyncComponent(() => import('./form.vue'));

const state = reactive({ tableData: [] as any[] });
const loading = ref(false);
const formRef = ref();
const formMounted = ref(false);

const initPage = async () => {
  loading.value = true;
  try {
    state.tableData = (await getTreeList()) as any[];
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
  ElMessageBox.confirm('此操作将删除该部门，是否继续?', '提示', {
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
          v-access:code="'upms:sysdept:add'"
          :icon="Plus"
          type="primary"
          @click="add"
        >
          新增
        </ElButton>
        <ElButton :icon="Refresh" @click="initPage"> 刷新 </ElButton>
      </div>

      <Form v-if="formMounted" ref="formRef" @init-page="initPage" />

      <ElTable
        v-loading="loading"
        :data="state.tableData"
        border
        default-expand-all
        row-key="id"
      >
        <ElTableColumn label="部门名称" prop="name" />
        <ElTableColumn label="部门编码" prop="code" />
        <ElTableColumn label="负责人" prop="leaderName" />
        <ElTableColumn label="负责人手机号" prop="leaderPhone" />
        <ElTableColumn label="排序" prop="orderNo" width="90" />
        <ElTableColumn label="状态" width="90">
          <template #default="scope">
            <ElTag :type="scope.row.enabled === 1 ? 'success' : 'info'">
              {{ scope.row.enabled === 1 ? '启用' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn align="center" fixed="right" label="操作" width="200">
          <template #default="scope">
            <ElButton
              v-access:code="'upms:sysdept:edit'"
              :icon="Edit"
              link
              type="primary"
              @click="edit(scope.row)"
            >
              修改
            </ElButton>
            <ElButton
              v-access:code="'upms:sysdept:del'"
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
