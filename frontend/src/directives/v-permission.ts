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

  const grantSet = new Set(authStore.snapshot?.grants ?? [])

  if (authStore.snapshot?.superAdmin) {
    return
  }

  let hasPermission = false
  if (Array.isArray(requiredPermissions)) {
    hasPermission = requiredPermissions.some((perm) => grantSet.has(perm))
  } else {
    hasPermission = grantSet.has(requiredPermissions)
  }

  if (!hasPermission) {
    el.parentNode?.removeChild(el)
    el.style.display = 'none'
  }
}
