import { defineStore } from 'pinia';

export const useDictStore = defineStore('dict', {
  state: () => ({
    dict: [] as Array<{ key: string; value: any }>,
  }),
  actions: {
    getDict(key: string) {
      if (!key) {
        return null;
      }
      return this.dict.find((item) => item.key === key)?.value ?? null;
    },
    setDict(key: string, value: any) {
      if (!key) {
        return;
      }
      const existing = this.dict.find((item) => item.key === key);
      if (existing) {
        existing.value = value;
        return;
      }
      this.dict.push({ key, value });
    },
    removeDict(key: string) {
      const index = this.dict.findIndex((item) => item.key === key);
      if (index < 0) {
        return false;
      }
      this.dict.splice(index, 1);
      return true;
    },
    cleanDict() {
      this.dict = [];
    },
  },
});
