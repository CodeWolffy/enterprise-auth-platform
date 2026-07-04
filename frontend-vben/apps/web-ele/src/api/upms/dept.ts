import { requestClient } from '#/api/request';

/** 把后端扁平部门列表(DepartmentView[]) 按 parentId 构建为树 */
function buildTree(list: any[]): any[] {
  const map = new Map<any, any>();
  const roots: any[] = [];
  (list ?? []).forEach((d) => map.set(d.id, { ...d, children: [] }));
  map.forEach((node) => {
    const pid = node.parentId;
    if (pid !== null && pid !== undefined && map.has(pid)) {
      map.get(pid).children.push(node);
    } else {
      roots.push(node);
    }
  });
  map.forEach((n) => {
    if (n.children.length === 0) delete n.children;
  });
  return roots;
}

/**
 * 部门树
 * 后端：GET /api/depts -> List<DepartmentView>（扁平，前端构树）
 */
export async function getTreeList() {
  const list: any = await requestClient.get('/depts');
  return buildTree(list as any[]);
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
