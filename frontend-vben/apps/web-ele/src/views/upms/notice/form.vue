<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { nextTick, reactive, ref } from 'vue';

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
import RichTextEditor from '#/components/rich-text-editor/index.vue';
import { formatDateTime, toInstantIso } from '#/utils/datetime';
import { hasMeaningfulRichText, sanitizeRichText } from '#/utils/rich-text';

const emit = defineEmits(['initPage']);

function validateNoticeContent(
  _rule: unknown,
  value: string,
  callback: (error?: Error) => void,
) {
  if (!hasMeaningfulRichText(value)) {
    callback(new Error('请输入公告内容'));
    return;
  }
  callback();
}

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
    noticeContent: [{ trigger: 'change', validator: validateNoticeContent }],
  },
});

const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const contentEditorRef = ref<InstanceType<typeof RichTextEditor> | null>(null);

const syncEditorContent = () => {
  const html = contentEditorRef.value?.getHTML?.() ?? state.form.noticeContent;
  state.form.noticeContent = html;
  return html;
};

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

const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  syncEditorContent();
  await nextTick();
  await formEl.validate((valid) => {
    if (valid) {
      const noticeContent = syncEditorContent();
      loading.value = true;
      const payload: any = {
        noticeTitle: state.form.noticeTitle,
        noticeContent: sanitizeRichText(noticeContent),
        published: state.form.published,
      };
      if (state.form.publishTime) {
        const publishTime = toInstantIso(state.form.publishTime);
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
        <ElInput
          v-model="state.form.noticeTitle"
          show-word-limit
          maxlength="200"
        />
      </ElFormItem>
      <ElFormItem label="公告内容" prop="noticeContent">
        <RichTextEditor
          :key="state.form.id || 'new'"
          ref="contentEditorRef"
          v-model="state.form.noticeContent"
          :height="420"
          :min-height="280"
          :upload-image="uploadNoticeImage"
          placeholder="请输入公告内容，支持图文混排"
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
        <ElButton
          type="primary"
          @click="submitForm(formRef)"
          :loading="loading"
        >
          确 认
        </ElButton>
      </span>
    </template>
  </ElDialog>
</template>
