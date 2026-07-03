<script lang="ts" setup>
import type { FormInstance } from 'element-plus';

import { reactive, ref } from 'vue';

import {
  ElButton,
  ElCol,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElRadio,
  ElRadioGroup,
  ElRow,
  ElSelect,
} from 'element-plus';

import { addObj, editObj, getById } from '#/api/upms/tenant';
import { getList } from '#/api/upms/tenant-package';
import { useDict } from '#/utils/dict';
import { formatDateTime, toInstantIso } from '#/utils/datetime';

const emit = defineEmits(['initPage']);

interface State {
  form: {
    tenantId: string;
    tenantName: string;
    status: string;
    address: string;
    website: string;
    contactEmail: string;
    contactPhone: string;
    datatimes: Array<string>;
    packageCode: string;
    logoUrl: string;
    contactName: string;
    platformLevel: string;
    lifecycleNote: string;
  };
  rules: any;
  tenantPackageList: Array<any>;
}

// 字典
const { status } = useDict('status');

const state = reactive<State>({
  form: {
    tenantId: '',
    tenantName: '',
    status: '1',
    address: '',
    website: '',
    contactEmail: '',
    contactPhone: '',
    datatimes: [],
    packageCode: '',
    logoUrl: '',
    contactName: '',
    platformLevel: 'BUSINESS',
    lifecycleNote: '',
  },
  rules: {
    tenantId: [
      {
        required: true,
        message: '请输入租户编码',
        trigger: 'change',
      },
    ],
    tenantName: [
      {
        required: true,
        message: '请输入租户名',
        trigger: 'change',
      },
    ],
    address: [
      {
        required: true,
        message: '请输入租户地址',
        trigger: 'change',
      },
    ],
    website: [
      {
        required: true,
        message: '请输入租户官网',
        trigger: 'change',
      },
    ],
    datatimes: [
      {
        required: true,
        message: '请选择有效期',
        trigger: 'change',
      },
    ],
    contactEmail: [
      {
        pattern:
          /[\w!#$%&'*+/=?^`{|}~-]+(?:\.[\w!#$%&'*+/=?^`{|}~-]+)*@(?:\w(?:[\w-]*\w)?\.)+\w(?:[\w-]*\w)?/,
        message: '邮箱格式错误',
        trigger: 'blur',
      },
    ],
    contactPhone: [
      {
        required: true,
        message: '请输入手机号',
        trigger: 'change',
      },
      {
        pattern: /^1[3-9]\d{9}$/,
        message: '手机号格式不对',
        trigger: 'blur',
      },
    ],
    packageCode: [
      {
        required: true,
        message: '请选择租户套餐',
        trigger: 'change',
      },
    ],
    status: [
      {
        required: true,
        message: '请选择状态',
        trigger: 'change',
      },
    ],
  },
  tenantPackageList: [],
});

const dialog = ref(false);
const loading = ref(false);
const formRef = ref();
const isEdit = ref(false);

const initForm = (row?: any) => {
  resetFormFields();
  dialog.value = true;
  isEdit.value = !!row?.tenantId;
  getTenantPackageList();
  if (row && row.tenantId) {
    getDetail(row.tenantId);
  }
};

const getDetail = (tenantId: string) => {
  loading.value = true;
  getById(tenantId).then((response: any) => {
    state.form = {
      tenantId: response.tenantId,
      tenantName: response.name,
      status: String(response.tenantStatus ?? 1),
      address: response.address || '',
      website: response.website || '',
      contactEmail: response.contactEmail || '',
      contactPhone: response.contactPhone || '',
      datatimes:
        response.authBeginAt && response.expireAt
          ? [formatDateTime(response.authBeginAt), formatDateTime(response.expireAt)]
          : [],
      packageCode: response.packageCode || '',
      logoUrl: response.logoUrl || '',
      contactName: response.contactName || '',
      platformLevel: response.platformLevel ? 'PLATFORM' : 'BUSINESS',
      lifecycleNote: response.lifecycleNote || '',
    };
    loading.value = false;
  }).catch(() => {
    loading.value = false;
  });
};

/** 关闭事件 */
const handleClose = () => {
  resetForm(formRef.value);
};

const resetFormFields = () => {
  state.form = {
    tenantId: '',
    tenantName: '',
    status: '1',
    address: '',
    website: '',
    contactEmail: '',
    contactPhone: '',
    datatimes: [],
    packageCode: '',
    logoUrl: '',
    contactName: '',
    platformLevel: 'BUSINESS',
    lifecycleNote: '',
  };
};

/** 重置表单 */
const resetForm = (formEl?: FormInstance) => {
  resetFormFields();
  isEdit.value = false;
  loading.value = false;
  dialog.value = false;
  formEl?.resetFields();
};

const buildPayload = () => {
  const [authBeginAt, expireAt] = state.form.datatimes ?? [];
  return {
    tenantId: state.form.tenantId,
    tenantName: state.form.tenantName,
    platformLevel: state.form.platformLevel === 'PLATFORM',
    tenantStatus: Number(state.form.status),
    authBeginAt: toInstantIso(authBeginAt) ?? null,
    expireAt: toInstantIso(expireAt) ?? null,
    packageCode: state.form.packageCode,
    logoUrl: state.form.logoUrl,
    contactName: state.form.contactName,
    contactPhone: state.form.contactPhone,
    contactEmail: state.form.contactEmail,
    website: state.form.website,
    address: state.form.address,
    lifecycleNote: state.form.lifecycleNote,
  };
};

/** 提交按钮 */
const submitForm = async (formEl: FormInstance | undefined) => {
  if (!formEl) return;
  await formEl.validate((valid) => {
    if (valid) {
      loading.value = true;
      if (isEdit.value) {
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
  addObj(buildPayload())
    .then(() => {
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
  editObj(buildPayload())
    .then(() => {
      resetForm(formRef.value);
      ElMessage.success('修改成功');
      emit('initPage');
    })
    .catch(() => {
      loading.value = false;
    });
};

const getTenantPackageList = () => {
  getList().then((response) => {
    state.tenantPackageList = response;
  });
};

defineExpose({
  initForm,
});
</script>

<template>
  <ElDialog
    v-model="dialog"
    :title="isEdit ? '修改租户' : '添加租户'"
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
      <ElRow>
        <ElCol :span="12">
          <ElFormItem label="租户编码" prop="tenantId">
            <ElInput
              v-model="state.form.tenantId"
              show-word-limit
              maxlength="50"
              :disabled="isEdit"
            />
          </ElFormItem>
          <ElFormItem label="官网" prop="website">
            <ElInput v-model="state.form.website" />
          </ElFormItem>
          <ElFormItem label="有效时间" prop="datatimes">
            <ElDatePicker
              v-model="state.form.datatimes"
              type="datetimerange"
              range-separator="-"
              start-placeholder="授权开始时间"
              end-placeholder="授权结束时间"
              value-format="YYYY-MM-DD HH:mm:ss"
            />
          </ElFormItem>
          <ElFormItem label="租户套餐" prop="packageCode">
            <ElSelect
              v-model="state.form.packageCode"
              clearable
              :disabled="isEdit"
              style="width: 100%"
            >
              <ElOption
                v-for="item in state.tenantPackageList"
                :key="item.id"
                :label="item.packageName"
                :value="item.packageCode"
              />
            </ElSelect>
          </ElFormItem>
          <ElFormItem label="联系人" prop="contactName">
            <ElInput v-model="state.form.contactName" />
          </ElFormItem>

          <ElFormItem label="租户级别" prop="platformLevel">
            <ElRadioGroup v-model="state.form.platformLevel">
              <ElRadio value="BUSINESS">业务级</ElRadio>
              <ElRadio value="PLATFORM">平台级</ElRadio>
            </ElRadioGroup>
          </ElFormItem>

          <ElFormItem label="运营备注" prop="lifecycleNote">
            <ElInput
              v-model="state.form.lifecycleNote"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="租户生命周期相关备注信息"
            />
          </ElFormItem>
        </ElCol>
        <ElCol :span="12">
          <ElFormItem label="租户名" prop="tenantName">
            <ElInput v-model="state.form.tenantName" show-word-limit maxlength="50" />
          </ElFormItem>
          <ElFormItem label="邮箱" prop="contactEmail">
            <ElInput v-model="state.form.contactEmail" />
          </ElFormItem>
          <ElFormItem label="手机号" prop="contactPhone">
            <ElInput v-model="state.form.contactPhone" />
          </ElFormItem>
          <ElFormItem label="状态" prop="status">
            <ElRadioGroup v-model="state.form.status">
              <ElRadio
                v-for="item in status"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </ElRadio>
            </ElRadioGroup>
          </ElFormItem>
          <ElFormItem label="Logo地址" prop="logoUrl">
            <ElInput v-model="state.form.logoUrl" />
          </ElFormItem>
        </ElCol>
      </ElRow>
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
