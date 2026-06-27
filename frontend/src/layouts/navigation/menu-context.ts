import type { Component, InjectionKey } from 'vue'

export interface NavLink {
  id: string
  to: string
  label: string
  icon: Component
  children: NavLink[]
}

export interface MenuContext {
  collapse: boolean
  activePath: string
  openedMenus: Set<string>
  toggleMenu: (id: string) => void
  setMenuOpen?: (id: string, open: boolean) => void
  selectMenu: (path: string) => void
}

export const menuContextKey: InjectionKey<MenuContext> = Symbol('menuContext')
