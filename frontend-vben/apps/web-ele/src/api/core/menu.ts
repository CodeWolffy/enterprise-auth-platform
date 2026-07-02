import { requestClient } from '#/api/request';

function toRoute(node: any): any {
  // Vben 要求 route.name 唯一且稳定：由 path 推导（/system/role -> system-role），
  // 目录无 path 时回退到菜单 ID。
  const routeName = node?.path
    ? String(node.path)
        .replace(/^\/+/, '')
        .replace(/\//g, '-')
    : `menu-${node?.id}`;
  const route: any = {
    name: routeName,
    path: node?.path,
    meta: {
      title: node?.title,
      icon: node?.icon,
      order: node?.sort,
      // 外链
      ...(node?.outerStatus && node?.path
        ? { link: node.path, openInNewWindow: true }
        : {}),
    },
  };
  if (node?.component) {
    route.component = node.component;
  }
  return route;
}

function toRoutes(nodes: any[]): any[] {
  return nodes.flatMap((node) => {
    const route = toRoute(node);
    const childRoutes = Array.isArray(node?.children)
      ? toRoutes(node.children)
      : [];

    if (childRoutes.length === 0) {
      return [route];
    }

    if (node?.component) {
      return [route, ...childRoutes];
    }

    route.children = childRoutes;
    return [route];
  });
}

/**
 * 获取当前用户菜单（路由形态）
 * 后端：GET /api/auth/me -> PermissionSnapshotResponse.menus(MenuNode[])
 */
export async function getAllMenusApi() {
  const me = await requestClient.get<any>('/auth/me', {
    headers: { isSwitchTenant: false },
  });
  const menus = (me?.menus ?? []) as any[];
  return toRoutes(menus);
}