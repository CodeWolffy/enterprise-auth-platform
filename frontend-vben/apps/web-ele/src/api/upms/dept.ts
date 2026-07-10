import { requestClient } from '#/api/request';

export interface DeptTreeItem {
  children?: DeptTreeItem[];
  code?: string;
  enabled?: number;
  id: number | string;
  leaderName?: string;
  leaderPhone?: string;
  leaderUserId?: null | number;
  name: string;
  orderNo?: number;
  parentId?: null | number | string;
}

/** 把后端扁平部门列表(DepartmentView[]) 按 parentId 构建为树 */
function buildTree(list: DeptTreeItem[]): DeptTreeItem[] {
  const map = new Map<DeptTreeItem['id'], DeptTreeItem>();
  const roots: DeptTreeItem[] = [];
  (list ?? []).forEach((d) => map.set(d.id, { ...d, children: [] }));
  map.forEach((node) => {
    const pid = node.parentId;
    if (pid !== null && pid !== undefined && map.has(pid)) {
      const parent = map.get(pid);
      if (parent) {
        (parent.children ??= []).push(node);
      }
    } else {
      roots.push(node);
    }
  });
  map.forEach((n) => {
    if ((n.children?.length ?? 0) === 0) delete n.children;
  });
  return roots;
}

/**
 * 部门树
 * 后端：GET /api/depts -> List<DepartmentView>（扁平，前端构树）
 */
export async function getTreeList() {
  const list = await requestClient.get<DeptTreeItem[]>('/depts');
  return buildTree(list);
}

/**
 * 新增部门
 * 后端：POST /api/depts (DeptCrudRequest)
 */
export async function addObj(data: any) {
  return requestClient.post('/depts', data);
}

/**
 * 修改部门
 * 后端：PUT /api/depts/{deptId}
 */
export async function editObj(data: any) {
  return requestClient.put(`/depts/${data.id}`, data);
}

/**
 * 删除部门
 * 后端：DELETE /api/depts/{deptId}
 */
export async function delObj(id: number | string) {
  return requestClient.delete(`/depts/${id}`);
}
