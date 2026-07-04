<script setup lang="ts">
import type { SwitchableTenantView } from '#/types/tenant';

import { computed, onMounted, ref, watch } from 'vue';

import {
  ArrowDown,
  Check,
  OfficeBuilding,
  Switch,
} from '@element-plus/icons-vue';
import {
  ElButton,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElIcon,
  ElMessage,
  ElTag,
} from 'element-plus';

import { getSwitchableTenantsApi } from '#/api';
import { useAuthStore } from '#/store';

const authStore = useAuthStore();
const tenants = ref<SwitchableTenantView[]>([]);
const loading = ref(false);
const switching = ref(false);

const canSwitchTenant = computed(() => authStore.isPlatformSuperAdmin);
const activeTenantId = computed(() => authStore.tenantId || 'platform');
const originTenantId = computed(
  () => authStore.operatorTenantId || activeTenantId.value,
);

const currentTenant = computed(() => {
  return (
    tenants.value.find((tenant) => tenant.tenantId === activeTenantId.value) ??
    fallbackTenant(activeTenantId.value, true)
  );
});

const dropdownDisabled = computed(() => {
  return loading.value || switching.value || tenants.value.length <= 1;
});

function fallbackTenant(
  tenantId: string,
  active = false,
): SwitchableTenantView {
  return {
    active,
    name: tenantId,
    origin: tenantId === originTenantId.value,
    platformLevel: tenantId === originTenantId.value,
    switchable: true,
    tenantId,
    tenantStatus: 1,
  };
}

function fallbackTenants() {
  const values = [fallbackTenant(activeTenantId.value, true)];
  if (originTenantId.value && originTenantId.value !== activeTenantId.value) {
    values.push(fallbackTenant(originTenantId.value));
  }
  return values;
}

async function loadTenants() {
  if (!canSwitchTenant.value) {
    tenants.value = [];
    return;
  }
  loading.value = true;
  try {
    const data = await getSwitchableTenantsApi();
    tenants.value =
      Array.isArray(data) && data.length > 0 ? data : fallbackTenants();
  } catch (error) {
    console.error('加载可切换租户失败:', error);
    tenants.value = fallbackTenants();
  } finally {
    loading.value = false;
  }
}

async function handleSwitchTenant(command: number | object | string) {
  const tenantId = String(command);
  const target = tenants.value.find((tenant) => tenant.tenantId === tenantId);
  if (!target || target.active || switching.value) {
    return;
  }
  if (!target.switchable) {
    ElMessage.warning(target.disabledReason || '目标租户当前不可切换');
    return;
  }

  switching.value = true;
  try {
    await authStore.switchTenant(tenantId);
    ElMessage.success(`已切换到 ${target.name || target.tenantId}`);
    window.location.reload();
  } catch (error) {
    console.error('切换租户失败:', error);
  } finally {
    switching.value = false;
  }
}

onMounted(loadTenants);

watch(canSwitchTenant, (enabled) => {
  if (enabled) {
    void loadTenants();
  } else {
    tenants.value = [];
  }
});
</script>

<template>
  <ElDropdown
    v-if="canSwitchTenant"
    trigger="click"
    :disabled="dropdownDisabled"
    @command="handleSwitchTenant"
  >
    <ElButton class="tenant-switcher-button" :loading="loading || switching">
      <ElIcon v-if="!loading && !switching">
        <OfficeBuilding />
      </ElIcon>
      <span class="tenant-switcher-name">{{ currentTenant.name }}</span>
      <ElIcon class="tenant-switcher-arrow">
        <ArrowDown />
      </ElIcon>
    </ElButton>
    <template #dropdown>
      <ElDropdownMenu>
        <ElDropdownItem
          v-for="tenant in tenants"
          :key="tenant.tenantId"
          :command="tenant.tenantId"
          :disabled="tenant.active || !tenant.switchable || switching"
        >
          <div class="tenant-option">
            <ElIcon class="tenant-option-icon">
              <Check v-if="tenant.active" />
              <Switch v-else />
            </ElIcon>
            <div class="tenant-option-main">
              <div class="tenant-option-title">
                <span>{{ tenant.name }}</span>
                <ElTag v-if="tenant.platformLevel" size="small" effect="plain">
                  平台
                </ElTag>
                <ElTag
                  v-if="tenant.origin"
                  size="small"
                  effect="plain"
                  type="success"
                >
                  登录租户
                </ElTag>
                <ElTag v-if="!tenant.switchable" size="small" type="danger">
                  不可切换
                </ElTag>
              </div>
              <div class="tenant-option-meta">
                {{ tenant.tenantId }}
                <template v-if="tenant.disabledReason">
                  · {{ tenant.disabledReason }}
                </template>
              </div>
            </div>
          </div>
        </ElDropdownItem>
      </ElDropdownMenu>
    </template>
  </ElDropdown>
</template>

<style scoped>
.tenant-switcher-button {
  min-width: 132px;
  max-width: 220px;
  padding: 0 10px;
}

.tenant-switcher-name {
  min-width: 0;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tenant-switcher-arrow {
  margin-left: 2px;
}

.tenant-option {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  min-width: 240px;
  max-width: 320px;
}

.tenant-option-icon {
  margin-top: 3px;
  color: var(--el-color-primary);
}

.tenant-option-main {
  min-width: 0;
}

.tenant-option-title {
  display: flex;
  gap: 6px;
  align-items: center;
}

.tenant-option-title span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tenant-option-meta {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.3;
  color: var(--el-text-color-secondary);
}
</style>
