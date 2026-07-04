<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElSelect,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/upms/dict-value';
import { useDictStore } from '#/store/dict';

const emit = defineEmits(['initPage']);

const defaultForm = () => ({
  id: '',
  dictId: '',
  dictType: '',
  dictLabel: '',
  dictValue: '',
  remarks: '',
  enabled: true,
  sort: 0,
  showClass: 'default',
});

const state = reactive({
  form: defaultForm(),
  rules: {
    dictLabel: [
      {
        required: true,
        message: '请输入字典标签',
        trigger: 'change',
      },
    ],
    dictValue: [
      {
        required: true,
        message: '请输入字典键值',
        trigger: 'change',
      },
    ],
    enabled: [
      {
        required: true,
        message: '请选择状态',
        trigger: 'change',
      },
    ],
    sort: [
      {
        required: true,
        message: '请输入排序序号',
        trigger: 'change',
      },
    ],
  },
});

const showClassList = ref([
  {
    value: 'default',
    label: '默认',
  },
  {
    value: 'primary',
    label: 'primary',
  },
  {
    value: 'success',
    label: 'success',
  },
  {
    value: 'info',
    label: 'info',
  },
  {
    value: 'warning',
    label: 'warning',
  },
  {
    value: 'danger',
    label: 'danger',
  },
]);

const dialog = ref(false);
const loading = ref(false);
const formRef = ref();

const initForm = (row: any) => {
  state.form = defaultForm();
  if (row && row.id) {
    getDetail(row.id);
  } else {
    state.form.dictId = row?.dictId ?? '';
    state.form.dictType = row?.dictType ?? '';
  }
  dialog.value = true;
};

const getDetail = (id: string) => {
  loading.value = true;
  // 修改
  getById(id)
    .then((response: any) => {
      loading.value = false;
      state.form = { ...defaultForm(), ...response };
    })
    .catch(() => {
      loading.value = false;
    });
};

/** 关闭事件 */
const handleClose = () => {
  resetForm(formRef.value);
};

/** 重置表单 */
const resetForm = (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  loading.value = false;
  dialog.value = false;
  state.form = defaultForm();
  formEl.resetFields();
};

/** 提交按钮 */
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      loading.value = true;
      if (state.form.id) {
        // 修改
        edit();
      } else {
        // 新增
        add();
      }
    }
  });
};

/** 新增 */
const add = () => {
  addObj(state.form)
    .then(() => {
      useDictStore().removeDict(state.form.dictType);
      resetForm(formRef.value);
      ElMessage.success('新增成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};

/** 修改 */
const edit = () => {
  editObj(state.form)
    .then(() => {
      useDictStore().removeDict(state.form.dictType);
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
    :title="state.form.id ? '修改字典值' : '添加字典值'"
    width="60%"
    :before-close="handleClose"
  >
    <ElForm
      ref="formRef"
      :model="state.form"
      label-width="120px"
      :rules="state.rules"
      status-icon
    >
      <ElFormItem label="字典标签" prop="dictLabel">
        <ElInput
          v-model="state.form.dictLabel"
          show-word-limit
          maxlength="128"
        />
      </ElFormItem>
      <ElFormItem label="字典键值" prop="dictValue">
        <ElInput
          v-model="state.form.dictValue"
          show-word-limit
          maxlength="255"
        />
      </ElFormItem>
      <ElFormItem label="备注" prop="remarks">
        <ElInput
          v-model="state.form.remarks"
          show-word-limit
          type="textarea"
          maxlength="255"
        />
      </ElFormItem>
      <ElFormItem label="排序序号" prop="sort">
        <ElInputNumber v-model="state.form.sort" :min="0" />
      </ElFormItem>
      <ElFormItem label="回显样式" prop="showClass">
        <ElSelect v-model="state.form.showClass" placeholder="请选择回显样式">
          <ElOption
            v-for="item in showClassList"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="状态" prop="enabled">
        <ElRadioGroup v-model="state.form.enabled">
          <ElRadio :value="true">正常</ElRadio>
          <ElRadio :value="false">停用</ElRadio>
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
