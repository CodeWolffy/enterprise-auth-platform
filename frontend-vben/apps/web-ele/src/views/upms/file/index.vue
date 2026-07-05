<script lang="ts" setup>
import { defineAsyncComponent, reactive, ref } from 'vue';

import {
  Download,
  Link,
  Refresh,
  Search,
  Upload,
} from '@element-plus/icons-vue';
import {
  ElButton,
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

import { requestClient } from '#/api/request';
import { delObj, getPage, upload } from '#/api/upms/file';
import { formatDateTime } from '#/utils/datetime';

const RightToolbar = defineAsyncComponent(
  () => import('#/components/right-toolbar/index.vue'),
);
const Pagination = defineAsyncComponent(
  () => import('#/components/pagination/index.vue'),
);

const state = reactive({
  queryParams: {
    keyword: '',
    contentType: '',
    storageType: '',
    visibility: '',
  },
  page: {
    total: 0,
    currentPage: 1,
    pageSize: 10,
    asc: '',
    desc: 'create_time',
  },
  tableData: [],
});
const showSearch = ref(true);
const loading = ref(false);
const uploadRef = ref();
const defaultUploadVisibility = 'OWNER';

const initPage = async () => {
  loading.value = true;
  const params = {
    page: state.page.currentPage,
    size: state.page.pageSize,
    asc: state.page.asc,
    desc: state.page.desc,
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

const resetSearch = () => {
  state.queryParams.keyword = '';
  state.queryParams.contentType = '';
  state.queryParams.storageType = '';
  state.queryParams.visibility = '';
  initPage();
};

const handleUpload = async (file: File) => {
  loading.value = true;
  await upload(file, defaultUploadVisibility)
    .then(() => {
      ElMessage.success('上传成功');
      initPage();
    })
    .catch(() => {});
  loading.value = false;
  return false;
};

const downloadFile = async (row: any) => {
  try {
    const response = await requestClient.get(`/files/${row.fileKey}`, {
      responseType: 'blob',
      headers: { isSwitchTenant: false },
    });
    const blob = new Blob([response]);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = row.originalName || row.fileKey;
    anchor.click();
    URL.revokeObjectURL(url);
    ElMessage.success('文件下载成功');
  } catch (error: any) {
    ElMessage.error(error?.message || '下载失败');
  }
};

const copyUrl = async (url: string) => {
  try {
    await navigator.clipboard.writeText(url);
    ElMessage.success('公开链接已复制');
  } catch {
    ElMessage.error('复制失败');
  }
};

const del = (fileKey: string) => {
  ElMessageBox.confirm('此操作将删除该文件，是否继续?', '提示', {
    cancelButtonText: '取消',
    confirmButtonText: '确认',
    type: 'warning',
  }).then(() => {
    delObj(fileKey).then(() => {
      ElMessage.success('删除成功');
      initPage();
    });
  });
};

initPage();
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <!-- 搜索 -->
      <ElForm :model="state.queryParams" :inline="true" v-show="showSearch">
        <ElFormItem label="文件名" prop="keyword">
          <ElInput
            v-model="state.queryParams.keyword"
            clearable
            placeholder="请输入文件名"
          />
        </ElFormItem>
        <ElFormItem label="内容类型" prop="contentType">
          <ElInput
            v-model="state.queryParams.contentType"
            clearable
            placeholder="MIME类型"
            style="width: 140px"
          />
        </ElFormItem>
        <ElFormItem label="存储类型" prop="storageType">
          <ElSelect
            v-model="state.queryParams.storageType"
            clearable
            placeholder="请选择"
            style="width: 120px"
          >
            <ElOption label="MinIO" value="MINIO" />
            <ElOption label="本地" value="LOCAL" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem label="可见性" prop="visibility">
          <ElSelect
            v-model="state.queryParams.visibility"
            clearable
            placeholder="请选择"
            style="width: 120px"
          >
            <ElOption label="仅自己" value="OWNER" />
            <ElOption label="租户内" value="TENANT" />
            <ElOption label="公开" value="PUBLIC" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem>
          <ElButton type="primary" @click="initPage" :icon="Search">
            搜索
          </ElButton>
          <ElButton @click="resetSearch" :icon="Refresh"> 重置 </ElButton>
        </ElFormItem>
      </ElForm>
      <!-- 工具栏 -->
      <div class="hx-table-toolbar">
        <div style="display: flex; gap: 12px; align-items: center">
          <ElButton
            type="primary"
            @click="uploadRef?.click()"
            :icon="Upload"
            v-access:code="'upms:file:add'"
          >
            上传文件
          </ElButton>
          <input
            ref="uploadRef"
            type="file"
            style="display: none"
            @change="
              (e: any) => e.target.files[0] && handleUpload(e.target.files[0])
            "
          />
        </div>
        <RightToolbar
          :search-btn="true"
          :refresh-btn="true"
          @search="showSearch = !showSearch"
          @refresh="initPage"
        />
      </div>
      <!-- 列表 -->
      <ElTable v-loading="loading" :data="state.tableData" border>
        <ElTableColumn
          prop="originalName"
          label="文件名"
          show-overflow-tooltip
        />
        <ElTableColumn prop="contentType" label="内容类型" width="150" />
        <ElTableColumn prop="size" label="大小" width="100">
          <template #default="scope">
            {{ (scope.row.size / 1024).toFixed(1) }} KB
          </template>
        </ElTableColumn>
        <ElTableColumn prop="storageType" label="存储类型" width="100">
          <template #default="scope">
            <ElTag>{{ scope.row.storageType }}</ElTag>
          </template>
        </ElTableColumn>
        <ElTableColumn prop="visibility" label="可见性" width="100" />
        <ElTableColumn label="上传时间" width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.createdAt) }}
          </template>
        </ElTableColumn>
        <ElTableColumn label="操作" width="240" align="center" fixed="right">
          <template #default="scope">
            <ElButton
              link
              type="primary"
              :icon="Download"
              @click="downloadFile(scope.row)"
            >
              下载
            </ElButton>
            <ElButton
              v-if="scope.row.url"
              link
              type="primary"
              :icon="Link"
              @click="copyUrl(scope.row.url)"
            >
              复制链接
            </ElButton>
            <ElButton
              link
              type="danger"
              @click="del(scope.row.fileKey)"
              v-access:code="'upms:file:del'"
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
    </div>
  </div>
</template>
