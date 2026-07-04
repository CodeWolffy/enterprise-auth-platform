import type { MenuProvider, SubMenuProvider } from '../types';

import { getCurrentInstance, inject, provide } from 'vue';

import { findComponentUpward } from '../utils';

const menuContextKey = Symbol('menuContext');
const menuComponentNames = ['Menu', 'VbenMenu'];
const subMenuComponentNames = ['SubMenu'];

/**
 * @zh_CN Provide menu context
 */
function createMenuContext(injectMenuData: MenuProvider) {
  provide(menuContextKey, injectMenuData);
}

/**
 * @zh_CN Provide menu context
 */
function createSubMenuContext(injectSubMenuData: SubMenuProvider) {
  const instance = getCurrentInstance();

  provide(`subMenu:${instance?.uid}`, injectSubMenuData);
}

/**
 * @zh_CN Inject menu context
 */
function useMenuContext() {
  const instance = getCurrentInstance();
  if (!instance) {
    throw new Error('instance is required');
  }
  const rootMenu = inject(menuContextKey) as MenuProvider;
  return rootMenu;
}

/**
 * @zh_CN Inject menu context
 */
function useSubMenuContext() {
  const instance = getCurrentInstance();
  if (!instance) {
    throw new Error('instance is required');
  }
  const parentMenu = findComponentUpward(instance, [
    ...menuComponentNames,
    ...subMenuComponentNames,
  ]);
  if (!parentMenu) {
    return;
  }
  const subMenu = inject<SubMenuProvider | undefined>(
    `subMenu:${parentMenu.uid}`,
    undefined,
  );
  return subMenu;
}

export {
  createMenuContext,
  createSubMenuContext,
  menuComponentNames,
  subMenuComponentNames,
  useMenuContext,
  useSubMenuContext,
};
