import type { Recordable, UserInfo } from '@vben/types';

import type { PermissionSnapshot } from '#/types/auth-models';

import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';

import { LOGIN_PATH } from '@vben/constants';
import { preferences } from '@vben/preferences';
import { resetAllStores, useAccessStore, useUserStore } from '@vben/stores';

import { ElNotification } from 'element-plus';
import { defineStore } from 'pinia';

import {
  getPermissionSnapshotApi,
  loginApi,
  logoutApi,
  switchTenantApi,
} from '#/api';
import { $t } from '#/locales';

export const useAuthStore = defineStore('auth', () => {
  const accessStore = useAccessStore();
  const userStore = useUserStore();
  const router = useRouter();

  const loginLoading = ref(false);
  const snapshot = ref<null | PermissionSnapshot>(null);
  const passwordChangeRequired = ref(false);
  let tenantSwitchRequestId = 0;

  function invalidateTenantSwitchRequests() {
    tenantSwitchRequestId += 1;
  }

  const tenantId = computed(() => {
    return (
      snapshot.value?.tenantId ?? (userStore.userInfo as any)?.tenantId ?? ''
    );
  });
  const operatorTenantId = computed(() => {
    return (
      snapshot.value?.operatorTenantId ??
      (userStore.userInfo as any)?.operatorTenantId ??
      ''
    );
  });
  const isPlatformSuperAdmin = computed(() => {
    return Boolean(
      snapshot.value?.superAdmin ?? (userStore.userInfo as any)?.superAdmin,
    );
  });

  function toUserInfo(me: PermissionSnapshot): UserInfo {
    const roles: string[] = [...(me?.roles ?? [])];
    const permissions: string[] = [...(me?.grants ?? [])];
    return {
      userId: me?.userId,
      username: me?.username,
      realName: me?.username,
      avatar: me?.avatarUrl ?? '',
      roles,
      permissions,
      tenantId: me?.tenantId,
      operatorTenantId: me?.operatorTenantId,
      superAdmin: me?.superAdmin,
      dataScopeType: me?.dataScopeType,
      customDeptIds: me?.customDeptIds,
    } as unknown as UserInfo;
  }

  function applySnapshot(
    me: PermissionSnapshot,
    requiresPasswordChange = false,
  ) {
    snapshot.value = me;
    passwordChangeRequired.value = requiresPasswordChange;
    const userInfo = toUserInfo(me);
    userStore.setUserInfo(userInfo);
    accessStore.setAccessCodes((userInfo as any).permissions ?? []);
    return userInfo;
  }

  /**
   * 异步处理登录操作
   * Asynchronously handle the login process
   * @param params 登录表单数据
   */
  async function authLogin(
    params: Recordable<any>,
    onSuccess?: () => Promise<void> | void,
  ) {
    // 异步处理用户登录操作并获取 accessToken
    let userInfo: null | UserInfo = null;
    try {
      loginLoading.value = true;
      // 后端为明文密码 + 服务端 BCrypt 校验，前端不做 AES 加密
      const { passwordChangeRequired: requiresPasswordChange, tokenValue } =
        await loginApi(params);

      // 如果成功获取到 accessToken
      if (tokenValue) {
        // 将 accessToken 存储到 accessStore 中
        accessStore.setAccessToken(tokenValue);

        // 获取用户信息并存储到 accessStore 中
        userInfo = await fetchUserInfo(Boolean(requiresPasswordChange));

        if (accessStore.loginExpired) {
          accessStore.setLoginExpired(false);
        } else {
          onSuccess
            ? await onSuccess?.()
            : await router.push(
                userInfo.homePath || preferences.app.defaultHomePath,
              );
        }

        if (userInfo?.realName) {
          ElNotification({
            message: `${$t('authentication.loginSuccessDesc')}:${userInfo?.realName}`,
            title: $t('authentication.loginSuccess'),
            type: 'success',
          });
        }
      }
    } finally {
      loginLoading.value = false;
    }

    return {
      userInfo,
    };
  }

  async function logout(redirect: boolean = true) {
    invalidateTenantSwitchRequests();
    try {
      await logoutApi();
    } catch {
      // 不做任何处理
    }
    resetAllStores();
    accessStore.setLoginExpired(false);

    // 回登录页带上当前路由地址
    await router.replace({
      path: LOGIN_PATH,
      query: redirect
        ? {
            redirect: encodeURIComponent(router.currentRoute.value.fullPath),
          }
        : {},
    });
  }

  async function fetchUserInfo(requiresPasswordChange = false) {
    const me = await getPermissionSnapshotApi();
    return applySnapshot(me, requiresPasswordChange);
  }

  async function bootstrapSnapshot() {
    const me = await getPermissionSnapshotApi();
    applySnapshot(me);
    return snapshot.value;
  }

  async function switchTenant(tenantId: string) {
    // 生成唯一请求 ID，防止并发切换时旧响应覆盖新状态
    const requestId = ++tenantSwitchRequestId;
    const me = await switchTenantApi(tenantId);

    // 丢弃过期请求结果
    if (requestId !== tenantSwitchRequestId) {
      return { snapshot: null, userInfo: null };
    }

    const userInfo = applySnapshot(me);
    accessStore.setAccessMenus([]);
    accessStore.setAccessRoutes([]);
    accessStore.setIsAccessChecked(false);
    return { snapshot: me, userInfo };
  }

  function clearPasswordChangeRequirement() {
    passwordChangeRequired.value = false;
  }

  function $reset() {
    invalidateTenantSwitchRequests();
    loginLoading.value = false;
    snapshot.value = null;
    passwordChangeRequired.value = false;
  }

  return {
    $reset,
    authLogin,
    bootstrapSnapshot,
    clearPasswordChangeRequirement,
    fetchUserInfo,
    isPlatformSuperAdmin,
    loginLoading,
    operatorTenantId,
    passwordChangeRequired,
    snapshot,
    switchTenant,
    tenantId,
    logout,
  };
});
