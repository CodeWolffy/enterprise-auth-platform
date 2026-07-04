import type { SubMenuProvider } from '../types';

import { computed, getCurrentInstance } from 'vue';

import { findComponentUpward } from '../utils';
import { menuComponentNames, subMenuComponentNames } from './use-menu-context';

function isMenuComponentName(name?: string) {
  return !!name && menuComponentNames.includes(name);
}

function useMenu() {
  const instance = getCurrentInstance();
  if (!instance) {
    throw new Error('instance is required');
  }

  /**
   * @zh_CN 获取所有父级菜单链路
   */
  const parentPaths = computed(() => {
    let parent = instance.parent;
    const paths: string[] = [instance.props.path as string];
    while (parent && !isMenuComponentName(parent.type.name)) {
      if (parent?.props.path) {
        paths.unshift(parent.props.path as string);
      }
      parent = parent?.parent ?? null;
    }

    return paths;
  });

  const parentMenu = computed(() => {
    return findComponentUpward(instance, [
      ...menuComponentNames,
      ...subMenuComponentNames,
    ]);
  });

  return {
    parentMenu,
    parentPaths,
  };
}

function useMenuStyle(menu?: SubMenuProvider) {
  const subMenuStyle = computed(() => {
    return {
      '--menu-level': menu ? (menu?.level ?? 0 + 1) : 0,
    };
  });
  return subMenuStyle;
}

export { useMenu, useMenuStyle };
