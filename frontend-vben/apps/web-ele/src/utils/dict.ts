import { ref, toRefs } from 'vue';

// 根据字典类型查询字典数据信息
import { getByType } from '#/api/upms/dict-value';
import { useDictStore } from '#/store/dict';
/**
 * 获取字典数据
 * @param args 字典类型字符串列表
 */
export function useDict(...args: any) {
  const res = ref({}) as any;
  return (() => {
    args.forEach((dictType: string) => {
      res.value[dictType] = [];
      // dictType 字典名称
      const dicts = useDictStore().getDict(dictType);
      // 如果 dicts 有值，说明仓库中已经存储，直接使用
      if (dicts) {
        res.value[dictType] = dicts;
      } else {
        // 如果无值，代表仓库中还未存储，调用按类型查询字典值的接口
        getByType(dictType).then((resp) => {
          // 后端 DictValueView: { dictLabel, dictValue, showClass, ... }
          res.value[dictType] = resp.map((p: any) => ({
            label: p.dictLabel,
            value: p.dictValue,
            showClass: p.showClass,
          }));
          useDictStore().setDict(dictType, res.value[dictType]);
        });
      }
    });
    return toRefs(res.value);
  })();
}
