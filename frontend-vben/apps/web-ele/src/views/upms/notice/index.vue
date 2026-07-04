<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  Delete,
  Document,
  Edit,
  Plus,
  Refresh,
  Search,
} from '@element-plus/icons-vue';
import {
  ElButton,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus';

import { delObj, getById, getPage } from '#/api/upms/notice';
import RichTextViewer from '#/components/rich-text-viewer/index.vue';
import { invokeWhenComponentReady } from '#/utils/component-ready';
import { formatDateTime } from '#/utils/datetime';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const Form = defineAsyncComponent(() => import('./form.vue'));

const state = reactive({
  queryParams: {
    keyword: '',
    published: '',
    workflowStatus: '',
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
const queryRef = ref();
const detailDrawer = ref(false);
const detailData = ref<any>(null);

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
      state.tableData = response.records;
      state.page.total = response.total;
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

const add = () => openForm();

const edit = (row: any) => openForm(row);

const viewDetail = async (row: any) => {
  try {
    detailData.value = await getById(row.id);
    detailDrawer.value = true;
  } catch (error: any) {
    ElMessage.error(error?.message || '加载详情失败');
  }
};

const del = (id: string) => {
  ElMessageBox.confirm('此操作将删除该公告，是否继续?', '提示', {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
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

const resetQuery = () => {
  queryRef.value.resetFields();
  state.page.currentPage = 1;
  initPage();
};
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
        <ElFormItem label="关键字" prop="keyword">
          <ElInput
            v-model="state.queryParams.keyword"
            clearable
            placeholder="请输入关键字"
          />
        </ElFormItem>
        <ElFormItem label="公告状态" prop="workflowStatus">
          <ElSelect
            v-model="state.queryParams.workflowStatus"
            clearable
            placeholder="请选择"
            style="width: 120px"
          >
            <ElOption label="草稿" value="DRAFT" />
            <ElOption label="已排期" value="SCHEDULED" />
            <ElOption label="已发布" value="PUBLISHED" />
          </ElSelect>
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
            @click="add"
            :icon="Plus"
            v-access:code="'upms:sysnotice:add'"
          >
            新增
          </ElButton>
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
        <ElTableColumn prop="noticeTitle" label="公告标题" />
        <ElTableColumn
          prop="workflowStatus"
          label="状态"
          width="100"
          align="center"
        >
          <template #default="scope">
            <ElTag
              :type="
                scope.row.workflowStatus === 'PUBLISHED'
                  ? 'success'
                  : scope.row.workflowStatus === 'SCHEDULED'
                    ? 'warning'
                    : 'info'
              "
            >
              {{
                scope.row.workflowStatus === 'PUBLISHED'
                  ? '已发布'
                  : scope.row.workflowStatus === 'SCHEDULED'
                    ? '已排期'
                    : '草稿'
              }}
            </ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn label="发布时间" width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.publishTime) }}
          </template>
        </ElTableColumn>
        <ElTableColumn prop="createdBy" label="创建人" width="120" />
        <ElTableColumn label="操作" width="280" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              @click="viewDetail(scope.row)"
              :icon="Document"
            >
              详情
            </ElButton>
            <ElButton
              link
              type="primary"
              @click="edit(scope.row)"
              :icon="Edit"
              v-access:code="'upms:sysnotice:edit'"
            >
              修改
            </ElButton>
            <ElButton
              link
              type="danger"
              @click="del(scope.row.id)"
              :icon="Delete"
              v-access:code="'upms:sysnotice:del'"
            >
              删除
            </ElButton>
          </template>
        </ElTableColumn>
      </ElTable>
      <!-- 分页 -->
      <Pagination
        :total="state.page.total"
        v-model:current="state.page.currentPage"
        v-model:size="state.page.pageSize"
        @change="initPage"
      />

      <!-- 详情抽屉 -->
      <ElDrawer v-model="detailDrawer" title="公告详情" size="600px">
        <div v-if="detailData" style="padding: 0 16px">
          <h2 style="margin: 0 0 16px; font-size: 20px">
            {{ detailData.noticeTitle }}
          </h2>
          <div
            style="
              padding-bottom: 16px;
              margin-bottom: 16px;
              font-size: 13px;
              color: #64748b;
              border-bottom: 1px solid #e5e7eb;
            "
          >
            <div>
              发布状态:
              <ElTag
                :type="detailData.published ? 'success' : 'info'"
                size="small"
              >
                {{ detailData.published ? '已发布' : '草稿' }}
              </ElTag>
            </div>
            <div style="margin-top: 4px">
              发布时间: {{ formatDateTime(detailData.publishTime) }}
            </div>
            <div style="margin-top: 4px">
              创建人: {{ detailData.createdBy || '-' }}
            </div>
          </div>
          <RichTextViewer :content="detailData.noticeContent" />
        </div>
      </ElDrawer>
    </div>
  </div>
</template>
