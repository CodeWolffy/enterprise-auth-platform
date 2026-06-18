import type { DirectiveBinding, ObjectDirective } from 'vue'
import { useAuthStore } from '@/stores/auth'

export const permissionDirective: ObjectDirective<HTMLElement, string | string[] | undefined> = {
  mounted(el, binding: DirectiveBinding<string | string[] | undefined>) {
    checkPermission(el, binding)
  },
  updated(el, binding: DirectiveBinding<string | string[] | undefined>) {
    checkPermission(el, binding)
  }
}

function checkPermission(el: HTMLElement, binding: DirectiveBinding<string | string[] | undefined>) {
  const authStore = useAuthStore()
  const requiredPermissions = binding.value

  if (!authStore.hasPermission(requiredPermissions)) {
    el.style.display = 'none'
    el.setAttribute('aria-hidden', 'true')
    return
  }
  el.style.removeProperty('display')
  el.removeAttribute('aria-hidden')
}
