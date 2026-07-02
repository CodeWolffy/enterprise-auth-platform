<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import { Delete, Edit, Plus, Refresh, Search, View } from '@element-plus/icons-vue';
import {
  ElButton,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, getPage, refresh } from '#/api/upms/dict';
import { useDictStore } from '#/store/dict';
import { invokeWhenComponentReady } from '#/utils/component-ready';
import { formatDateTime } from '#/utils/datetime';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);
const Form = defineAsyncComponent(() => import('./form.vue'));
const DictValue = defineAsyncComponent(() => import('../dict-value/index.vue'));

const state = reactive({
  queryParams: {
    dictType: '',
    keyword: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    sortBy: 'createdAt',
    sortDirection: 'desc',
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const refForm = ref();
const formMounted = ref(false);
const dictId = ref('');
const currentDict = ref<any>(null);
const dictValueDrawer = ref(false);
const queryRef = ref();

const initPage = async () => {
  loading.value = true;
  const params = {
    page: state.page.currentPage,
    size: state.page.pageSize,
    sortBy: state.page.sortBy,
    sortDirection: state.page.sortDirection,
  };
  await getPage(Object.assign(params, state.queryParams))
    .then((response: any) => {
      state.tableData = response?.records ?? [];
      state.page.total = response?.total ?? 0;
      loading.value = false;
    })
    .catch(() => {
      loading.value = false;
    });
};

const openForm = (row?: any) => {
  formMounted.value = true;
  void invokeWhenComponentReady(refForm, (form: any) => form.initForm(row));
};

/** 新增按钮 */
const add = () => openForm();

/** 修改按钮 */
const edit = (row: any) => openForm(row);

/** 删除按钮 */
const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该字典，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    delObj(id)
      .then(() => {
        useDictStore().cleanDict();
        ElMessage.success('删除成功');
        initPage();
      })
      .catch(() => {});
  });
};

/** 重置搜索表单 */
const resetQuery = () => {
  queryRef.value?.resetFields();
  state.page.currentPage = 1;
  initPage();
};

const refreshCache = () => {
  refresh().then(() => {
    useDictStore().cleanDict();
    ElMessage.success('刷新成功');
  });
};

const openValues = (row: any) => {
  currentDict.value = row;
  dictId.value = row.id;
  dictValueDrawer.value = true;
};

initPage();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <ElForm
        :model="state.queryParams"
        ref="queryRef"
        :inline="true"
        v-show="showSearch"
      >
        <ElFormItem label="字典类型" prop="dictType">
          <ElInput
            v-model="state.queryParams.dictType"
            placeholder="请输入字典类型"
          />
        </ElFormItem>
        <ElFormItem label="关键字" prop="keyword">
          <ElInput
            v-model="state.queryParams.keyword"
            clearable
            placeholder="字典编码/值"
          />
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetQuery" :icon="Refresh"> 重置 </ElButton>
        </ElFormItem>
      </ElForm>
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div>
          <ElButton
            type="primary"
            v-access:code="'upms:sysdict:add'"
            @click="add"
            :icon="Plus"
          >
            新增
          </ElButton>
          <ElButton @click="refreshCache" :icon="Refresh"> 刷新缓存 </ElButton>
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <Form v-if="formMounted" ref="refForm" @init-page="initPage" />
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn prop="dictType" label="字典类型" />
        <ElTableColumn prop="description" label="字典描述" />
        <ElTableColumn prop="valueCount" label="字典值数量" width="120" />
        <ElTableColumn prop="remarks" label="备注" />
        <ElTableColumn prop="enabled" label="状态" width="90">
          <template #default="scope">
            <ElTag :type="scope.row.enabled ? 'success' : 'danger'">
              {{ scope.row.enabled ? '正常' : '停用' }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="更新时间" width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.updatedAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="260" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              v-access:code="'upms:sysdict:edit'"
              @click="edit(scope.row)"
              :icon="Edit"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              v-access:code="'upms:sysdict:del'"
              @click="del(scope.row.id)"
              :icon="Delete"
            >
              删除
            </ElButton>
            <ElButton
              link
              type="primary"
              @click="openValues(scope.row)"
              :icon="View"
            >
              键值列表
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <ElDrawer size="46%" v-model="dictValueDrawer" direction="rtl">
        <template #header>
          <div>
            <h4 style="margin: 0">字典键值</h4>
            <span style="color: var(--el-text-color-secondary); font-size: 12px">
              {{ currentDict?.dictType }} / {{ currentDict?.description }}
            </span>
          </div>
        </template>
        <template #default>
          <DictValue
            :dict-id="String(dictId)"
            :dict-type="currentDict?.dictType ?? ''"
            v-if="dictValueDrawer"
          />
        </template>
      </ElDrawer>
      <!-- 分页 -->
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />
    </div>
  </div>
</template>
