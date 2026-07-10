package com.enterprise.auth.platform.modules.codegen.domain.render;

import com.enterprise.auth.platform.modules.codegen.domain.CodegenTypeMappings;
import com.enterprise.auth.platform.modules.codegen.domain.model.RenderContext;

/**
 * Vue 页面级模板渲染纯逻辑：Vben Grid 列表页与 Vben Form 弹窗。
 */
class VueViewRenderer {

    private final VueSnippetRenderer snippets;

    VueViewRenderer(VueSnippetRenderer snippets) {
        this.snippets = snippets;
    }

    String renderIndexView(RenderContext model) {
        return """
                <script lang="ts" setup>
                import type { VbenFormSchema } from '#/adapter/form';
                import type { VxeTableGridOptions } from '#/adapter/vxe-table';
                import type { __CLASS__QueryParams, __CLASS__View } from '#/types/__MODULE__';

                import { ref } from 'vue';

                import { Page, useVbenModal } from '@vben/common-ui';
                import { Plus } from '@vben/icons';

                import { ElButton, ElMessage, ElMessageBox } from 'element-plus';

                import { useVbenVxeGrid } from '#/adapter/vxe-table';
                import { delete__CLASS__, query__CLASS__Page } from '#/api/__KEBAB__';

                import Form from './form.vue';

                const searchSchema: VbenFormSchema[] = __SEARCH_SCHEMA__;
                const columns: VxeTableGridOptions<__CLASS__View>['columns'] = [
                __GRID_COLUMNS__  {
                    align: 'center',
                    field: 'operation',
                    fixed: 'right',
                    headerAlign: 'center',
                    showOverflow: false,
                    slots: { default: 'operation' },
                    title: '操作',
                    width: 200,
                  },
                ];

                const detailItem = ref<null | __CLASS__View>(null);

                const [FormModal, formModalApi] = useVbenModal({
                  connectedComponent: Form,
                  destroyOnClose: true,
                });
                const [DetailModal, detailModalApi] = useVbenModal({
                  footer: false,
                  fullscreenButton: false,
                });

                const [Grid, gridApi] = useVbenVxeGrid({
                  formOptions: {
                    schema: searchSchema,
                    submitOnChange: false,
                  },
                  gridOptions: {
                    columns,
                    height: 'auto',
                    keepSource: true,
                    pagerConfig: { enabled: true, pageSize: 20 },
                    proxyConfig: {
                      ajax: {
                        query: async ({ page }, formValues) => {
                          const result = await query__CLASS__Page({
                            ...formValues,
                            page: page.currentPage,
                            size: page.pageSize,
                          } as __CLASS__QueryParams);
                          return {
                            list: result.records ?? [],
                            total: result.total ?? 0,
                          };
                        },
                      },
                    },
                    rowConfig: { keyField: '__PRIMARY_KEY__' },
                    toolbarConfig: {
                      refresh: true,
                      refreshOptions: { code: 'query' },
                      search: true,
                      zoom: false,
                    },
                  } as VxeTableGridOptions<__CLASS__View>,
                });

                function onRefresh() {
                  gridApi.query();
                }

                function openForm(row?: __CLASS__View) {
                  formModalApi.setData(row ?? {}).open();
                }

                function openDetail(row: __CLASS__View) {
                  detailItem.value = row;
                  detailModalApi.open();
                }

                async function remove(row: __CLASS__View) {
                  try {
                    await ElMessageBox.confirm(
                      '删除后不可恢复，是否继续？',
                      '删除确认',
                      { type: 'warning' },
                    );
                  } catch {
                    return;
                  }
                  await delete__CLASS__(row.__PRIMARY_KEY__);
                  ElMessage.success('已删除');
                  onRefresh();
                }
                </script>

                <template>
                  <Page auto-content-height>
                    <FormModal @success="onRefresh" />
                    <DetailModal title="详情">
                      <dl v-if="detailItem" class="overflow-hidden rounded border border-border">
                __DETAIL_ITEMS__      </dl>
                    </DetailModal>

                    <Grid>
                      <template #toolbar-tools>
                        <ElButton
                          v-access:code="'__MODULE__:add'"
                          type="primary"
                          @click="openForm()"
                        >
                          <Plus class="size-5" />
                          新增
                        </ElButton>
                      </template>

                      <template #operation="{ row }">
                        <ElButton
                          v-access:code="'__MODULE__:get'"
                          link
                          type="primary"
                          @click="openDetail(row)"
                        >
                          详情
                        </ElButton>
                        <ElButton
                          v-access:code="'__MODULE__:edit'"
                          link
                          type="primary"
                          @click="openForm(row)"
                        >
                          修改
                        </ElButton>
                        <ElButton
                          v-access:code="'__MODULE__:del'"
                          link
                          type="danger"
                          @click="remove(row)"
                        >
                          删除
                        </ElButton>
                      </template>
                    </Grid>
                  </Page>
                </template>
                """
                .replace("__CLASS__", model.className())
                .replace("__MODULE__", model.moduleName())
                .replace("__KEBAB__", model.kebabName())
                .replace("__PRIMARY_KEY__", model.primaryKeyField())
                .replace("__SEARCH_SCHEMA__", snippets.renderVbenSearchSchema(model))
                .replace("__GRID_COLUMNS__", snippets.renderVbenGridColumns(model))
                .replace("__DETAIL_ITEMS__", snippets.renderVbenDetailItems(model));
    }

    String renderFormView(RenderContext model) {
        return """
                <script lang="ts" setup>
                import type { VbenFormSchema } from '#/adapter/form';
                import type {
                  __CLASS__CreateRequest,
                  __CLASS__UpdateRequest,
                  __CLASS__View,
                } from '#/types/__MODULE__';

                import { computed, ref } from 'vue';

                import { useVbenModal } from '@vben/common-ui';

                import { ElMessage } from 'element-plus';

                import { useVbenForm } from '#/adapter/form';
                import { create__CLASS__, update__CLASS__ } from '#/api/__KEBAB__';

                const emit = defineEmits<{ success: [] }>();

                const createSchema: VbenFormSchema[] = __CREATE_SCHEMA__;
                const updateSchema: VbenFormSchema[] = __UPDATE_SCHEMA__;
                const formData = ref<null | __CLASS__View>(null);
                const editingId = computed<__PRIMARY_KEY_TYPE__ | null>(
                  () => formData.value?.__PRIMARY_KEY__ ?? null,
                );

                const [Form, formApi] = useVbenForm({
                  commonConfig: {
                    colon: true,
                    formItemClass: 'col-span-2 md:col-span-1',
                  },
                  schema: createSchema,
                  showDefaultActions: false,
                  wrapperClass: 'grid-cols-2 gap-x-4',
                });

                const [Modal, modalApi] = useVbenModal({
                  onConfirm: onSubmit,
                  async onOpenChange(isOpen) {
                    if (!isOpen) return;
                    const data = modalApi.getData<__CLASS__View>();
                    formData.value =
                      data?.__PRIMARY_KEY__ === undefined || data.__PRIMARY_KEY__ === null
                        ? null
                        : data;
                    formApi.setState({
                      schema: formData.value ? updateSchema : createSchema,
                    });
                    await formApi.resetForm();
                    if (formData.value) {
                      await formApi.setValues(toForm(formData.value));
                    }
                  },
                });

                const title = computed(() => (editingId.value === null ? '新增' : '编辑'));

                async function onSubmit() {
                  const { valid } = await formApi.validate();
                  if (!valid) return;

                  modalApi.lock();
                  try {
                    const form = await formApi.getValues<
                      __CLASS__CreateRequest & __CLASS__UpdateRequest
                    >();
                    if (editingId.value === null) {
                      await create__CLASS__(toCreatePayload(form));
                      ElMessage.success('新增成功');
                    } else {
                      await update__CLASS__(editingId.value, toUpdatePayload(form));
                      ElMessage.success('修改成功');
                    }
                    modalApi.close();
                    emit('success');
                  } finally {
                    modalApi.unlock();
                  }
                }

                function toCreatePayload(
                  form: __CLASS__CreateRequest & __CLASS__UpdateRequest,
                ): __CLASS__CreateRequest {
                __CREATE_PAYLOAD__}

                function toUpdatePayload(
                  form: __CLASS__CreateRequest & __CLASS__UpdateRequest,
                ): __CLASS__UpdateRequest {
                __UPDATE_PAYLOAD__}

                function toForm(
                  row?: __CLASS__View,
                ): __CLASS__CreateRequest & __CLASS__UpdateRequest {
                __TO_FORM__}
                </script>

                <template>
                  <Modal class="w-full max-w-[640px]" :title="title">
                    <Form class="mx-4" />
                  </Modal>
                </template>
                """
                .replace("__CLASS__", model.className())
                .replace("__MODULE__", model.moduleName())
                .replace("__KEBAB__", model.kebabName())
                .replace("__PRIMARY_KEY__", model.primaryKeyField())
                .replace("__PRIMARY_KEY_TYPE__", CodegenTypeMappings.tsScalarType(model.primaryKeyJavaType()))
                .replace("__CREATE_SCHEMA__", snippets.renderVbenFormSchema(model.insertColumns()))
                .replace("__UPDATE_SCHEMA__", snippets.renderVbenFormSchema(model.editColumns()))
                .replace("__CREATE_PAYLOAD__", RenderSupport.renderTsPayload(model.insertColumns()))
                .replace("__UPDATE_PAYLOAD__", RenderSupport.renderTsPayload(model.editColumns()))
                .replace("__TO_FORM__", RenderSupport.renderTsToForm(model));
    }
}
