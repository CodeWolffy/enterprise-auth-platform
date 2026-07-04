<script lang="ts" setup>
import { Settings } from '@vben/icons';

import { VbenIconButton } from '@vben-core/shadcn-ui';

import Preferences from './preferences.vue';

const emit = defineEmits<{ clearPreferencesAndLogout: [] }>();

function clearPreferencesAndLogout() {
  emit('clearPreferencesAndLogout');
}
</script>
<template>
  <Preferences @clear-preferences-and-logout="clearPreferencesAndLogout">
    <template #default="{ open }">
      <VbenIconButton
        class="hover:animate-[shrink_0.3s_ease-in-out]"
        @click="open"
      >
        <Settings class="size-4 text-foreground" />
      </VbenIconButton>
    </template>
  </Preferences>
</template>
