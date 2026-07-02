<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';

import { queryPasswordPolicy, updatePasswordPolicy } from '#/api/system';
import type { SecurityPasswordPolicy } from '#/api/system';

const loading = ref(false);
const saving = ref(false);
const editing = ref(false);

const policy = reactive<SecurityPasswordPolicy>({
  passwordMinLength: 8,
  passwordMaxLength: 64,
  passwordRequireLetter: true,
  passwordRequireNumber: true,
  passwordRequireSpecial: false,
});

const editForm = reactive<SecurityPasswordPolicy>({
  passwordMinLength: 8,
  passwordMaxLength: 64,
  passwordRequireLetter: true,
  passwordRequireNumber: true,
  passwordRequireSpecial: false,
});

onMounted(async () => {
  loading.value = true;
  try {
    const data = await queryPasswordPolicy();
    Object.assign(policy, data);
  } finally {
    loading.value = false;
  }
});

function startEdit() {
  Object.assign(editForm, policy);
  editing.value = true;
}

function cancelEdit() {
  editing.value = false;
}

async function savePolicy() {
  saving.value = true;
  try {
    await updatePasswordPolicy({ ...editForm });
    Object.assign(policy, editForm);
    editing.value = false;
    ElMessage.success('安全策略保存成功');
  } catch {
    ElMessage.error('保存失败，请重试');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div class="hx-layout-container">
    <div class="hx-layout-container-auto hx-layout-container-view">
      <div v-loading="loading">
        <el-card shadow="never">
          <div class="custom-card-header">
            <span class="custom-card-title">安全策略</span>
            <div v-if="!editing">
              <el-button type="primary" size="small" @click="startEdit">
                编辑
              </el-button>
            </div>
          </div>

          <!-- 查看模式 -->
          <el-descriptions v-if="!editing" :column="1" border>
            <el-descriptions-item label="密码最小长度">
              {{ policy.passwordMinLength }}
            </el-descriptions-item>
            <el-descriptions-item label="密码最大长度">
              {{ policy.passwordMaxLength }}
            </el-descriptions-item>
            <el-descriptions-item label="需要字母">
              {{ policy.passwordRequireLetter ? '是' : '否' }}
            </el-descriptions-item>
            <el-descriptions-item label="需要数字">
              {{ policy.passwordRequireNumber ? '是' : '否' }}
            </el-descriptions-item>
            <el-descriptions-item label="需要特殊字符">
              {{ policy.passwordRequireSpecial ? '是' : '否' }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- 编辑模式 -->
          <el-form
            v-else
            label-width="140px"
            label-position="left"
            :model="editForm"
            @submit.prevent="savePolicy"
          >
            <el-form-item label="密码最小长度">
              <el-input-number
                v-model="editForm.passwordMinLength"
                :min="4"
                :max="64"
              />
            </el-form-item>
            <el-form-item label="密码最大长度">
              <el-input-number
                v-model="editForm.passwordMaxLength"
                :min="4"
                :max="128"
              />
            </el-form-item>
            <el-form-item label="需要字母">
              <el-switch v-model="editForm.passwordRequireLetter" />
            </el-form-item>
            <el-form-item label="需要数字">
              <el-switch v-model="editForm.passwordRequireNumber" />
            </el-form-item>
            <el-form-item label="需要特殊字符">
              <el-switch v-model="editForm.passwordRequireSpecial" />
            </el-form-item>
            <el-form-item>
              <el-button
                type="primary"
                :loading="saving"
                @click="savePolicy"
              >
                保存
              </el-button>
              <el-button :disabled="saving" @click="cancelEdit">取消</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </div>
  </div>
</template>

<style scoped>
.custom-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 0 16px;
  border-bottom: 1px solid var(--el-border-color-light);
  margin-bottom: 16px;
}

.custom-card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>