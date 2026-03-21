import type { DirectiveBinding, ObjectDirective } from 'vue'
import { useAuthStore } from '@/stores/auth'

export const permissionDirective: ObjectDirective<HTMLElement, string | string[]> = {
  mounted(el, binding: DirectiveBinding<string | string[]>) {
    checkPermission(el, binding)
  },
  updated(el, binding: DirectiveBinding<string | string[]>) {
    checkPermission(el, binding)
  }
}

function checkPermission(el: HTMLElement, binding: DirectiveBinding<string | string[]>) {
  const authStore = useAuthStore()
  const requiredPermissions = binding.value

  if (!requiredPermissions || (Array.isArray(requiredPermissions) && requiredPermissions.length === 0)) {
    return
  }

  const userPermissions = authStore.snapshot?.permissions || []
  
  // Super admin bypass
  const hasAdmin = userPermissions.includes('*:*:*') || userPermissions.includes('admin')
  if (hasAdmin) {
    return
  }

  let hasPermission = false
  if (Array.isArray(requiredPermissions)) {
    // If array, require AT LEAST ONE permission (OR logic)
    hasPermission = requiredPermissions.some(perm => userPermissions.includes(perm))
  } else {
    hasPermission = userPermissions.includes(requiredPermissions)
  }

  if (!hasPermission) {
    el.parentNode?.removeChild(el)
    // fallback for virtual DOM
    el.style.display = 'none'
  }
}
