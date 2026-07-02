import type { Preferences } from '@vben-core/preferences';
import type { DeepPartial } from '@vben-core/typings';

/**
 * 如果你想所有的app都使用相同的默认偏好设置，你可以在这里定义
 * 而不是去修改 @vben-core/preferences 中的默认偏好设置
 * @param preferences
 * @returns
 */

function defineOverridesPreferences(preferences: DeepPartial<Preferences>) {
  preferences.app = {
    accessMode: 'backend',
    defaultHomePath: '/dashboard',
    name: '企业认证平台',
  };
  preferences.theme = {
    mode: 'light',
  };
  preferences.logo = {
    enable: true,
    source: '/static/logo.svg',
  };
  preferences.copyright = {
    companyName: '企业认证平台',
    companySiteLink: '',
    date: '2026',
    enable: true,
  };
  return preferences;
}

export { defineOverridesPreferences };

export * from '@vben-core/preferences';
