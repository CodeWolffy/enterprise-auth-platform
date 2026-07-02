<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import { TiptapEditor } from '@vben-core/editor-ui';

import {
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElRadio,
  ElRadioGroup,
} from 'element-plus';

import { upload as uploadFile } from '#/api/upms/file';
import { addObj, editObj, getById } from '#/api/upms/notice';
import { formatDateTime } from '#/utils/datetime';

const emit = defineEmits(['initPage']);

const state = reactive({
  form: {
    id: '',
    noticeTitle: '',
    noticeContent: '',
    published: false,
    publishTime: '',
  },
  rules: {
    noticeTitle: [
      { required: true, message: '请输入公告标题', trigger: 'change' },
    ],
    noticeContent: [
      { required: true, message: '请输入公告内容', trigger: 'change' },
    ],
  },
});

const dialog = ref(false);
const loading = ref(false);
const formRef = ref();

const initForm = (row?: any) => {
  if (row && row.id) {
    getDetail(row.id);
  } else {
    resetForm();
    dialog.value = true;
  }
};

const getDetail = (id: string) => {
  loading.value = true;
  getById(id)
    .then((response) => {
      state.form.id = response.id;
      state.form.noticeTitle = response.noticeTitle;
      state.form.noticeContent = response.noticeContent || '';
      state.form.published = response.published ?? false;
      state.form.publishTime = formatDateTime(response.publishTime, '');
      loading.value = false;
      dialog.value = true;
    })
    .catch(() => {
      loading.value = false;
    });
};

const handleClose = () => {
  resetForm(formRef.value);
};

const resetForm = (formEl?: FormInstance) => {
  state.form.id = '';
  state.form.noticeTitle = '';
  state.form.noticeContent = '';
  state.form.published = false;
  state.form.publishTime = '';
  loading.value = false;
  dialog.value = false;
  formEl?.resetFields();
};

const uploadNoticeImage = async (file: File) => {
  const response = await uploadFile(file, 'PUBLIC');
  return response.url || `/api/files/public/${response.fileKey}`;
};

const toEpochMilli = (value: string) => {
  const normalized = value.replace(' ', 'T');
  const timestamp = new Date(normalized).getTime();
  return Number.isFinite(timestamp) ? timestamp : undefined;
};

const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      loading.value = true;
      const payload: any = {
        noticeTitle: state.form.noticeTitle,
        noticeContent: state.form.noticeContent,
        published: state.form.published,
      };
      if (state.form.publishTime) {
        const publishTime = toEpochMilli(state.form.publishTime);
        if (publishTime) {
          payload.publishTime = publishTime;
        }
      }
      if (state.form.id) {
        payload.id = state.form.id;
        edit(payload);
      } else {
        add(payload);
      }
    }
  });
};

const add = (payload: any) => {
  addObj(payload)
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('新增成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};

const edit = (payload: any) => {
  editObj(payload)
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('修改成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};

defineExpose({
  initForm,
});
</script>

<template>
  <ElDialog
    v-model="dialog"
    :title="state.form.id ? '修改公告' : '添加公告'"
    width="70%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="120px"
      :rules="state.rules"
    >
      <ElFormItem label="公告标题" prop="noticeTitle">
        <ElInput v-model="state.form.noticeTitle" show-word-limit maxlength="200" />
      </ElFormItem>
      <ElFormItem label="公告内容" prop="noticeContent">
        <TiptapEditor
          v-model="state.form.noticeContent"
          :min-height="360"
          :height="420"
          :upload-image="uploadNoticeImage"
          @error="ElMessage.error"
        />
      </ElFormItem>
      <ElFormItem label="发布时间" prop="publishTime">
        <ElDatePicker
          v-model="state.form.publishTime"
          type="datetime"
          placeholder="选择发布时间"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
      </ElFormItem>
      <ElFormItem label="发布状态" prop="published">
        <ElRadioGroup v-model="state.form.published">
          <ElRadio :value="true">发布</ElRadio>
          <ElRadio :value="false">草稿</ElRadio>
        </ElRadioGroup>
      </ElFormItem>
    </ElForm>
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="handleClose">关 闭</ElButton>
        <ElButton type="primary" @click="submitForm(formRef)" :loading="loading">
          确 认
        </ElButton>
      </span>
    </template>
  </ElDialog>
</template>