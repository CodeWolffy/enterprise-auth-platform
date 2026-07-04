<script lang="ts" setup>
import { computed, markRaw, watch } from 'vue';
import { useRouter } from 'vue-router';

import { AuthenticationLoginExpiredModal } from '@vben/common-ui';
import { useWatermark } from '@vben/hooks';
import { UserRoundPen } from '@vben/icons';
import { BasicLayout, LockScreen, UserDropdown } from '@vben/layouts';
import { preferences } from '@vben/preferences';
import { useAccessStore, useUserStore } from '@vben/stores';

import NotificationBell from '#/components/notification/NotificationBell.vue';
import TenantSwitcher from '#/components/tenant-switcher/index.vue';
import { useAuthStore } from '#/store';
import LoginForm from '#/views/_core/authentication/login.vue';

const userStore = useUserStore();
const authStore = useAuthStore();
const accessStore = useAccessStore();
const router = useRouter();
const { destroyWatermark, updateWatermark } = useWatermark();

const avatar = computed(() => {
  return userStore.userInfo?.avatar ?? preferences.app.defaultAvatar;
});

const accountProfileIcon = markRaw(UserRoundPen);

const userDropdownMenus = computed(() => [
  {
    handler: () => {
      void router.push({ name: 'AccountProfile' });
    },
    icon: accountProfileIcon,
    text: '个人中心',
  },
]);

async function handleLogout() {
  await authStore.logout(false);
}

watch(
  () => preferences.app.watermark,
  async (enable) => {
    if (enable) {
      await updateWatermark({
        content: `${userStore.userInfo?.username} - ${userStore.userInfo?.realName}`,
      });
    } else {
      destroyWatermark();
    }
  },
  {
    immediate: true,
  },
);
</script>

<template>
  <BasicLayout @clear-preferences-and-logout="handleLogout">
    <template #user-dropdown>
      <UserDropdown
        :avatar
        :text="userStore.userInfo?.username"
        :description="userStore.userInfo?.email"
        :menus="userDropdownMenus"
        tag-text="Pro"
        @logout="handleLogout"
      />
    </template>
    <template #tenant-select>
      <TenantSwitcher />
    </template>
    <template #notification>
      <NotificationBell />
    </template>
    <template #extra>
      <AuthenticationLoginExpiredModal
        v-model:open="accessStore.loginExpired"
        :avatar
        @logout="authStore.logout"
      >
        <LoginForm />
      </AuthenticationLoginExpiredModal>
    </template>
    <template #lock-screen>
      <LockScreen :avatar @to-login="handleLogout" />
    </template>
  </BasicLayout>
</template>

<style lang="scss" scoped></style>
