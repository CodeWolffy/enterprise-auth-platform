<template>
  <el-sub-menu v-if="item.children.length" :index="item.id">
    <template #title>
      <div class="nav-submenu-title" @click.stop="goToDefaultPath">
        <el-icon><component :is="item.icon" /></el-icon>
        <span>{{ item.label }}</span>
      </div>
    </template>
    <AppNavItem v-for="child in item.children" :key="child.id" :item="child" />
  </el-sub-menu>
  <el-menu-item v-else :index="item.to">
    <el-icon><component :is="item.icon" /></el-icon>
    <template #title>{{ item.label }}</template>
  </el-menu-item>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { useRouter } from 'vue-router'

interface NavLink {
  id: string
  to: string
  label: string
  icon: Component
  children: NavLink[]
}

const props = defineProps<{
  item: NavLink
}>()

const router = useRouter()

function goToDefaultPath() {
  if (props.item.to) {
    void router.push(props.item.to)
  }
}
</script>

<style scoped lang="scss">
.nav-submenu-title {
  display: inline-flex;
  align-items: center;
  min-width: 0;
  flex: 1;
}
</style>