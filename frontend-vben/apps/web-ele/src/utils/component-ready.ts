import type { Ref } from 'vue';

import { nextTick } from 'vue';

interface InvokeWhenReadyOptions {
  retries?: number;
  intervalMs?: number;
}

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export async function invokeWhenComponentReady<T>(
  componentRef: Ref<T | undefined>,
  invoke: (component: T) => void,
  options: InvokeWhenReadyOptions = {},
) {
  const retries = options.retries ?? 20;
  const intervalMs = options.intervalMs ?? 16;

  for (let index = 0; index < retries; index += 1) {
    await nextTick();
    if (componentRef.value) {
      invoke(componentRef.value);
      return true;
    }
    await sleep(intervalMs);
  }

  return false;
}