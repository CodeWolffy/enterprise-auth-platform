import type { Language } from 'element-plus/es/locale';

import type { App } from 'vue';

import type { LocaleSetupOptions } from '@vben/locales';

import { ref } from 'vue';

import { $t, setupI18n as coreSetup } from '@vben/locales';

import dayjs from 'dayjs';
import defaultLocale from 'element-plus/es/locale/lang/zh-cn';

import 'dayjs/locale/zh-cn';

const elementLocale = ref<Language>(defaultLocale);

async function setupI18n(app: App, options: LocaleSetupOptions = {}) {
  dayjs.locale('zh-cn');
  elementLocale.value = defaultLocale;
  await coreSetup(app, {
    ...options,
    defaultLocale: 'zh-CN',
    loadMessages: async () => ({}),
    missingWarn: !import.meta.env.PROD,
  });
}

export { $t, elementLocale, setupI18n };
