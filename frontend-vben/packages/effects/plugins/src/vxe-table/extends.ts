import type { VxeGridProps, VxeUIExport } from 'vxe-table';

import type { Recordable } from '@vben/types';

import { formatDate, formatDateTime, isFunction } from '@vben/utils';

interface ProxyExtendApi {
  setState: (state: Recordable<any>) => void;
}

export function extendProxyOptions(
  api: ProxyExtendApi,
  options: VxeGridProps,
  getFormValues: () => Recordable<any>,
) {
  [
    'query',
    'querySuccess',
    'queryError',
    'queryAll',
    'queryAllSuccess',
    'queryAllError',
  ].forEach((key) => {
    extendProxyOption(key, api, options, getFormValues);
  });
}

function extendProxyOption(
  key: string,
  api: ProxyExtendApi,
  options: VxeGridProps,
  getFormValues: () => Recordable<any>,
) {
  const { proxyConfig } = options;
  const configFn = (proxyConfig?.ajax as Recordable<any>)?.[key];
  if (!isFunction(configFn)) {
    return options;
  }

  const wrapperFn = async (
    params: Recordable<any>,
    customValues: Recordable<any>,
    ...args: Recordable<any>[]
  ) => {
    const formValues = getFormValues();
    const data = await configFn(
      params,
      {
        /**
         * 开启toolbarConfig.refresh功能
         * 点击刷新按钮 这里的值为PointerEvent 会携带错误参数
         */
        ...(customValues instanceof PointerEvent ? {} : customValues),
        ...formValues,
      },
      ...args,
    );
    return data;
  };
  api.setState({
    gridOptions: {
      proxyConfig: {
        ajax: {
          [key]: wrapperFn,
        },
      },
    },
  });
}

export function extendsDefaultFormatter(vxeUI: VxeUIExport) {
  vxeUI.formats.add('formatDate', {
    tableCellFormatMethod({ cellValue }) {
      return formatDate(cellValue);
    },
  });

  vxeUI.formats.add('formatDateTime', {
    tableCellFormatMethod({ cellValue }) {
      return formatDateTime(cellValue);
    },
  });
}
