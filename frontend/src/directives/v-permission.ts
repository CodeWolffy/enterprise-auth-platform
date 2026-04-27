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

  if (!authStore.hasGrant(requiredPermissions)) {
    el.parentNode?.removeChild(el)
    el.style.display = 'none'
  }
}
