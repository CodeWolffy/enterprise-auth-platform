<script setup lang="ts">
import { nextTick, reactive, ref, watch } from 'vue';

import {
  ElButton,
  ElDialog,
  ElInput,
  ElMessage,
  ElTag,
  ElTree,
} from 'element-plus';

import { getMenusByRole, getTenantMenu } from '#/api/upms/menu';
import { saveRoleMenu } from '#/api/upms/sys-role';

interface State {
  menuList: any[];
  menuIds: Array<number | string>;
}
const emit = defineEmits(['initPage']);
const menuRef = ref();
const dialog = ref(false);
const state = reactive<State>({ menuList: [], menuIds: [] });
const roleId = ref<number | string>('');
const roleRow = ref<any>(null);
const loading = ref(false);
const filterText = ref('');
const defaultProps = {
  children: 'children',
  label: 'name',
  disabled: 'disabled',
};

const filterNodeMethod = (value: string, data: any) => {
  if (!value) return true;
  const name = data.name || data.label || '';
  return name.toLowerCase().includes(value.toLowerCase());
};

watch(filterText, (val) => {
  menuRef.value?.filter(val);
});

const handleChange = () => {
  const checkedKeys = menuRef.value.getCheckedKeys(false);
  const halfCheckedKeys = menuRef.value.getHalfCheckedKeys(false);
  state.menuIds = [...checkedKeys, ...halfCheckedKeys];
};

const getRoleMenus = async (id: number | string) => {
  roleId.value = id;
  const tree: any = await getTenantMenu();
  state.menuList = (tree as any[]) ?? [];
  const assigned: any = await getMenusByRole(id);
  state.menuIds = (assigned as Array<number | string>) ?? [];
  state.menuIds.forEach((mid) => {
    nextTick(() => {
      menuRef.value?.setChecked(mid, true, false);
    });
  });
};

const onsubmit = () => {
  loading.value = true;
  saveRoleMenu({ menuIds: state.menuIds, roleId: roleId.value })
    .then(() => {
      ElMessage.success('更新成功');
      dialog.value = false;
      emit('initPage');
    })
    .finally(() => {
      loading.value = false;
    });
};

const initRoleMenu = async (row: any) => {
  if (row?.id === undefined || row.id === null || row.id === '') {
    ElMessage.error('角色ID不存在，无法分配菜单');
    return;
  }
  roleRow.value = row;
  roleId.value = row.id;
  dialog.value = true;
  try {
    await getRoleMenus(row.id);
  } catch {
    ElMessage.error('菜单授权数据加载失败');
    dialog.value = false;
  }
};

defineExpose({ initRoleMenu });
</script>

<template>
  <ElDialog v-model="dialog" destroy-on-close :title="`分配菜单 - ${roleRow?.name || ''}`" width="60%">
    <div class="rolemenu-toolbar">
      <ElInput
        v-model="filterText"
        placeholder="过滤菜单名称"
        clearable
        style="width: 260px"
      />
      <div class="rolemenu-summary">
        <ElTag size="small" type="info" effect="plain">已选 {{ state.menuIds.length }} 项</ElTag>
      </div>
    </div>
    <div class="hx-menus">
      <ElTree
        ref="menuRef"
        :data="state.menuList"
        node-key="id"
        :props="defaultProps"
        show-checkbox
        :filter-node-method="filterNodeMethod"
        @check="handleChange"
      >
        <template #default="{ data }">
          <span class="custom-tree-node">
            <span>{{ data.name || data.label }}</span>
            <div class="tree-tags" v-if="data.type || data.permission">
              <ElTag v-if="data.type" size="small" effect="plain" type="info">{{ data.type }}</ElTag>
              <ElTag v-if="data.permission" size="small" effect="plain">{{ data.permission }}</ElTag>
            </div>
          </span>
        </template>
      </ElTree>
    </div>
    <template #footer>
      <span class="dialog-footer">
        <ElButton @click="dialog = false">取 消</ElButton>
        <ElButton :loading="loading" type="primary" @click="onsubmit">
          确 认
        </ElButton>
      </span>
    </template>
  </ElDialog>
</template>

<style scoped>
.rolemenu-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.rolemenu-summary {
  display: flex;
  gap: 8px;
}

.hx-menus {
  width: 100%;
  height: 500px;
  padding: 4px;
  overflow-y: auto;
  border: 1px solid #dcdee0;
  border-radius: 4px;
}

.custom-tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 8px;
}

.tree-tags {
  display: flex;
  gap: 4px;
}
</style>
