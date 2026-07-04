<script setup lang="ts">
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, getPage, queryRoleImpact } from '#/api/upms/sys-role';
import { invokeWhenComponentReady } from '#/utils/component-ready';

const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));
const RoleMenu = defineAsyncComponent(() => import('./rolemenu.vue'));

const DATA_SCOPE_LABELS: Record<string, string> = {
  ALL: '全部',
  CUSTOM: '自定义',
  DEPT: '本部门',
  DEPT_AND_CHILDREN: '本部门及以下',
  SELF: '仅本人',
};

const state = reactive({
  queryParams: {
    keyword: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
  },
  tableData: [] as any[],
});
const loading = ref(false);
const formRef = ref();
const roleMenuRef = ref();
const formMounted = ref(false);
const menuMounted = ref(false);

const initPage = async () => {
  loading.value = true;
  try {
    const response: any = await getPage({
      page: state.page.currentPage,
      size: state.page.pageSize,
      keyword: state.queryParams.keyword,
    });
    state.tableData = response?.records ?? [];
    state.page.total = response?.total ?? 0;
  } finally {
    loading.value = false;
  }
};

const resetQuery = () => {
  state.queryParams.keyword = '';
  state.page.currentPage = 1;
  initPage();
};
const openForm = (row?: any) => {
  formMounted.value = true;
  void invokeWhenComponentReady(formRef, (form: any) => form.initForm(row));
};
const add = () => openForm();
const edit = (row: any) => openForm(row);
const del = (id: number | string) => {
  ElMessageBox.confirm('此操作将删除该角色，是否继续?', '提示', {
    cancelButtonText: '取消',
    confirmButtonText: '确认',
    type: 'warning',
  }).then(() => {
    // 先检查影响分析
    queryRoleImpact(id)
      .then((impact: any) => {
        const userCount = impact?.assignedUserCount ?? 0;
        const menuCount = impact?.assignedMenuCount ?? 0;
        if (userCount > 0 || menuCount > 0) {
          const details = [];
          if (userCount > 0) details.push(`${userCount} 个用户引用`);
          if (menuCount > 0) details.push(`${menuCount} 个菜单授权`);
          ElMessageBox.confirm(
            `该角色存在关联引用（${details.join('、')}），删除后相关用户将失去此角色权限。是否继续？`,
            '删除影响提示',
            {
              cancelButtonText: '取消',
              confirmButtonText: '强制删除',
              type: 'warning',
            },
          ).then(() => doDelete(id));
        } else {
          doDelete(id);
        }
      })
      .catch(() => {
        // 影响分析查询失败，直接删除
        doDelete(id);
      });
  });
};

const doDelete = (id: number | string) => {
  delObj(id)
    .then(() => {
      ElMessage.success('删除成功');
      initPage();
    })
    .catch(() => {});
};
const onAuth = (row: any) => {
  menuMounted.value = true;
  void invokeWhenComponentReady(roleMenuRef, (menu: any) => {
    void menu.initRoleMenu(row);
  });
};

initPage();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <ElForm :inline="true" :model="state.queryParams">
        <ElFormItem label="关键字" prop="keyword">
          <ElInput
            v-model="state.queryParams.keyword"
            clearable
            placeholder="角色名称 / 编码"
            @keyup.enter="initPage"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton :icon="Search" type="primary" @click="initPage">
            搜索
          </ElButton>
          <ElButton :icon="Refresh" @click="resetQuery"> 重置 </ElButton>
        </ElFormItem>
      </ElForm>

      <div class="hx-table-toolbar" style="margin-bottom: 12px">
        <ElButton
          v-access:code="'upms:sysrole:add'"
          :icon="Plus"
          type="primary"
          @click="add"
        >
          新增
        </ElButton>
      </div>

      <Form v-if="formMounted" ref="formRef" @init-page="initPage" />
      <RoleMenu v-if="menuMounted" ref="roleMenuRef" @init-page="initPage" />

      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn label="角色名称" prop="name" />
        <ElTableColumn label="角色编码" prop="code" />
        <ElTableColumn
          label="角色描述"
          prop="description"
          show-overflow-tooltip
        />
        <ElTableColumn label="数据权限" width="140">
          <template #default="scope">
            <ElTag>
              {{
                DATA_SCOPE_LABELS[scope.row.dataScopeType] ??
                scope.row.dataScopeType
              }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn align="center" fixed="right" label="操作" width="300">
          <template #default="scope">
            <ElButton
              v-if="scope.row.code !== 'ADMIN'"
              v-access:code="'upms:sysrole:edit'"
              :icon="Edit"
              link
              type="primary"
              @click="edit(scope.row)"
            >
              修改
            </ElButton>
            <ElButton
              v-if="scope.row.code !== 'ADMIN'"
              v-access:code="'upms:sysrole:del'"
              :icon="Delete"
              link
              type="danger"
              @click="del(scope.row.id)"
            >
              删除
            </ElButton>
            <ElButton
              v-access:code="'upms:sysrole:edit'"
              :icon="Plus"
              link
              type="primary"
              @click="onAuth(scope.row)"
            >
              分配菜单
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>

      <Pagination
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        :total="state.page.total"
        @change="initPage"
      />
    </div>
  </div>
</template>
