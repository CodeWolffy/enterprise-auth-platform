<script setup lang="ts" name="rich-text-editor">
import { ref, watch } from 'vue';

import { TiptapEditor } from '@vben-core/editor-ui';

import {
  ArrowLeftBold,
  ArrowRightBold,
  DeleteFilled,
  Hide,
  Picture,
  Right,
  View,
} from '@element-plus/icons-vue';
import {
  ElButton,
  ElButtonGroup,
  ElDialog,
  ElInput,
  ElMessage,
  ElPopover,
  ElTabPane,
  ElTabs,
  ElTooltip,
  ElUpload,
} from 'element-plus';

import RichTextViewer from '../rich-text-viewer/index.vue';

interface Props {
  /** 编辑器内容 HTML */
  modelValue?: string;
  /** 是否禁用 */
  disabled?: boolean;
  /** 占位符文本 */
  placeholder?: string;
  /** 编辑区高度 */
  height?: number | string;
  /** 编辑区最小高度 */
  minHeight?: number | string;
  /** 上传图片适配器，返回可访问 URL */
  uploadImage?: (file: File) => Promise<string>;
  /** 是否默认显示预览 */
  defaultPreview?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  defaultPreview: false,
  disabled: false,
  height: 360,
  minHeight: 240,
  modelValue: '',
  placeholder: '请输入内容...',
  uploadImage: undefined,
});

const emit = defineEmits<{
  change: [editor: any];
  'update:modelValue': [value: string];
}>();

const editorRef = ref<any>(null);
const internalValue = ref(props.modelValue || '');
const mode = ref<'edit' | 'preview'>(props.defaultPreview ? 'preview' : 'edit');
const linkDialog = ref(false);
const linkUrl = ref('');
const imageDialog = ref(false);
const imageUrl = ref('');
const imageTab = ref<'upload' | 'url'>('upload');
const uploadingImage = ref(false);
const textColor = ref('#111827');
const colorPalette = [
  '#111827',
  '#374151',
  '#6b7280',
  '#ef4444',
  '#f97316',
  '#eab308',
  '#22c55e',
  '#14b8a6',
  '#3b82f6',
  '#6366f1',
  '#a855f7',
  '#ec4899',
];

watch(
  () => props.disabled,
  (disabled) => {
    editorRef.value?.setEditable(!disabled && mode.value === 'edit');
  },
);

watch(
  () => props.modelValue,
  (value, oldValue) => {
    const nextValue = value || '';
    // 每次接收到新内容（如打开编辑弹窗）都切回编辑模式，避免停留在上次的预览模式
    if (nextValue && !oldValue) {
      mode.value = 'edit';
    }
    if (nextValue === internalValue.value) {
      return;
    }
    internalValue.value = nextValue;
    if (editorRef.value && editorRef.value.getHTML() !== nextValue) {
      editorRef.value.commands.setContent(nextValue, { emitUpdate: false });
    }
  },
);

function onCreated(editor: any) {
  editorRef.value = editor;
  // 编辑器创建时同步一次当前 modelValue，防止 prop 在编辑器就绪前已变化
  const current = internalValue.value;
  if (editor.getHTML() !== current) {
    editor.commands.setContent(current, { emitUpdate: false });
  }
  editor.setEditable(!props.disabled && mode.value === 'edit');
}

function run(fn: (editor: any) => void) {
  const editor = editorRef.value;
  if (!editor || props.disabled || mode.value === 'preview') return;
  fn(editor);
}

function toggleMode(target: 'edit' | 'preview') {
  mode.value = target;
  editorRef.value?.setEditable(target === 'edit' && !props.disabled);
}

function setBlockType(type: string) {
  run((editor) => {
    const chain = editor.chain().focus();
    switch (type) {
      case 'heading1': {
        chain.toggleHeading({ level: 1 }).run();
        break;
      }
      case 'heading2': {
        chain.toggleHeading({ level: 2 }).run();
        break;
      }
      default: {
        chain.setParagraph().run();
      }
    }
  });
}

function setTextColor(color: string) {
  textColor.value = color;
  run((editor) => editor.chain().focus().setColor(color).run());
}

function openLinkDialog() {
  linkUrl.value = editorRef.value?.getAttributes('link').href || '';
  linkDialog.value = true;
}

function confirmLink() {
  const url = linkUrl.value.trim();
  if (!url) {
    removeLink();
    return;
  }
  run((editor) =>
    editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run(),
  );
  closeLinkDialog();
}

function removeLink() {
  run((editor) =>
    editor.chain().focus().extendMarkRange('link').unsetLink().run(),
  );
  closeLinkDialog();
}

function closeLinkDialog() {
  linkDialog.value = false;
  linkUrl.value = '';
}

function openImageDialog() {
  imageUrl.value = '';
  imageTab.value = 'upload';
  imageDialog.value = true;
}

function closeImageDialog() {
  imageDialog.value = false;
  imageUrl.value = '';
}

function confirmImageUrl() {
  const url = imageUrl.value.trim();
  if (!url) {
    ElMessage.warning('请输入图片地址');
    return;
  }
  insertImage(url, '');
  closeImageDialog();
}

async function handleImageUpload(rawFile?: File) {
  if (!rawFile) return;
  if (!rawFile.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件');
    return;
  }
  if (rawFile.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB');
    return;
  }
  if (!props.uploadImage) {
    ElMessage.warning('当前未配置图片上传');
    return;
  }

  try {
    uploadingImage.value = true;
    const url = await props.uploadImage(rawFile);
    if (!url) {
      ElMessage.error('图片上传后未返回可访问地址');
      return;
    }
    insertImage(url, rawFile.name);
    closeImageDialog();
  } catch (error: any) {
    ElMessage.error(error?.message || '图片上传失败');
  } finally {
    uploadingImage.value = false;
  }
}

function onUploadChange(file: any) {
  handleImageUpload(file?.raw);
}

function insertImage(src: string, alt: string) {
  run((editor) => editor.chain().focus().setImage({ alt, src }).run());
}

function onModelUpdate(value: string) {
  internalValue.value = value;
  emit('update:modelValue', value);
}

function onChange(editor: any) {
  emit('change', editor);
}

defineExpose({
  getEditor: () => editorRef.value,
  getHTML: () => editorRef.value?.getHTML() || internalValue.value,
});
</script>

<template>
  <div
    class="rich-text-editor"
    :class="{ 'is-preview': mode === 'preview', 'is-disabled': disabled }"
  >
    <div class="rich-text-editor__header">
      <div class="rich-text-editor__toolbar">
        <ElTooltip content="正文/标题" placement="top">
          <select
            :disabled="disabled || mode === 'preview'"
            class="rich-text-editor__block-select"
            :value="
              editorRef?.isActive('heading', { level: 1 })
                ? 'heading1'
                : editorRef?.isActive('heading', { level: 2 })
                  ? 'heading2'
                  : 'paragraph'
            "
            @change="(e) => setBlockType((e.target as HTMLSelectElement).value)"
          >
            <option value="paragraph">正文</option>
            <option value="heading1">标题 1</option>
            <option value="heading2">标题 2</option>
          </select>
        </ElTooltip>

        <div class="rich-text-editor__divider"></div>

        <ElButtonGroup>
          <ElTooltip content="粗体" placement="top">
            <ElButton
              :disabled="disabled || mode === 'preview'"
              :type="editorRef?.isActive('bold') ? 'primary' : 'default'"
              size="small"
              @click="
                run((editor) => editor.chain().focus().toggleBold().run())
              "
            >
              <strong>B</strong>
            </ElButton>
          </ElTooltip>
          <ElTooltip content="斜体" placement="top">
            <ElButton
              :disabled="disabled || mode === 'preview'"
              :type="editorRef?.isActive('italic') ? 'primary' : 'default'"
              size="small"
              @click="
                run((editor) => editor.chain().focus().toggleItalic().run())
              "
            >
              <em>I</em>
            </ElButton>
          </ElTooltip>
          <ElTooltip content="下划线" placement="top">
            <ElButton
              :disabled="disabled || mode === 'preview'"
              :type="editorRef?.isActive('underline') ? 'primary' : 'default'"
              size="small"
              @click="
                run((editor) => editor.chain().focus().toggleUnderline().run())
              "
            >
              <u>U</u>
            </ElButton>
          </ElTooltip>
        </ElButtonGroup>

        <div class="rich-text-editor__divider"></div>

        <ElButtonGroup>
          <ElTooltip content="左对齐" placement="top">
            <ElButton
              :disabled="disabled || mode === 'preview'"
              :type="
                editorRef?.isActive({ textAlign: 'left' })
                  ? 'primary'
                  : 'default'
              "
              size="small"
              @click="
                run((editor) =>
                  editor.chain().focus().setTextAlign('left').run(),
                )
              "
            >
              左
            </ElButton>
          </ElTooltip>
          <ElTooltip content="居中对齐" placement="top">
            <ElButton
              :disabled="disabled || mode === 'preview'"
              :type="
                editorRef?.isActive({ textAlign: 'center' })
                  ? 'primary'
                  : 'default'
              "
              size="small"
              @click="
                run((editor) =>
                  editor.chain().focus().setTextAlign('center').run(),
                )
              "
            >
              中
            </ElButton>
          </ElTooltip>
          <ElTooltip content="右对齐" placement="top">
            <ElButton
              :disabled="disabled || mode === 'preview'"
              :type="
                editorRef?.isActive({ textAlign: 'right' })
                  ? 'primary'
                  : 'default'
              "
              size="small"
              @click="
                run((editor) =>
                  editor.chain().focus().setTextAlign('right').run(),
                )
              "
            >
              右
            </ElButton>
          </ElTooltip>
        </ElButtonGroup>

        <div class="rich-text-editor__divider"></div>

        <ElButtonGroup>
          <ElTooltip content="无序列表" placement="top">
            <ElButton
              :disabled="disabled || mode === 'preview'"
              :type="editorRef?.isActive('bulletList') ? 'primary' : 'default'"
              size="small"
              @click="
                run((editor) => editor.chain().focus().toggleBulletList().run())
              "
            >
              • 列表
            </ElButton>
          </ElTooltip>
          <ElTooltip content="有序列表" placement="top">
            <ElButton
              :disabled="disabled || mode === 'preview'"
              :type="editorRef?.isActive('orderedList') ? 'primary' : 'default'"
              size="small"
              @click="
                run((editor) =>
                  editor.chain().focus().toggleOrderedList().run(),
                )
              "
            >
              1. 列表
            </ElButton>
          </ElTooltip>
        </ElButtonGroup>

        <div class="rich-text-editor__divider"></div>

        <ElButtonGroup>
          <ElTooltip content="文字颜色" placement="top">
            <ElPopover
              trigger="click"
              placement="bottom"
              :width="220"
              popper-class="rich-text-color-popover"
            >
              <template #reference>
                <ElButton
                  :disabled="disabled || mode === 'preview'"
                  size="small"
                >
                  A<span
                    class="rich-text-editor__color-line"
                    :style="{
                      backgroundColor:
                        editorRef?.getAttributes('textStyle').color ||
                        textColor,
                    }"
                  ></span>
                </ElButton>
              </template>
              <div class="rich-text-editor__color-grid">
                <button
                  v-for="color in colorPalette"
                  :key="color"
                  class="rich-text-editor__color-swatch"
                  :style="{ backgroundColor: color }"
                  type="button"
                  @click="setTextColor(color)"
                ></button>
              </div>
            </ElPopover>
          </ElTooltip>
        </ElButtonGroup>

        <div class="rich-text-editor__divider"></div>

        <ElButtonGroup>
          <ElTooltip content="插入链接" placement="top">
            <ElButton
              :disabled="disabled || mode === 'preview'"
              :type="editorRef?.isActive('link') ? 'primary' : 'default'"
              size="small"
              :icon="Right"
              @click="openLinkDialog"
            >
              链接
            </ElButton>
          </ElTooltip>
          <ElTooltip content="插入图片" placement="top">
            <ElButton
              :disabled="disabled || mode === 'preview'"
              size="small"
              :icon="Picture"
              @click="openImageDialog"
            >
              图片
            </ElButton>
          </ElTooltip>
        </ElButtonGroup>

        <div class="rich-text-editor__divider"></div>

        <ElButtonGroup>
          <ElTooltip content="清除格式" placement="top">
            <ElButton
              :disabled="disabled || mode === 'preview'"
              size="small"
              :icon="DeleteFilled"
              @click="
                run((editor) =>
                  editor.chain().focus().unsetAllMarks().clearNodes().run(),
                )
              "
            />
          </ElTooltip>
          <ElTooltip content="撤销" placement="top">
            <ElButton
              :disabled="disabled || mode === 'preview'"
              size="small"
              :icon="ArrowLeftBold"
              @click="run((editor) => editor.chain().focus().undo().run())"
            />
          </ElTooltip>
          <ElTooltip content="重做" placement="top">
            <ElButton
              :disabled="disabled || mode === 'preview'"
              size="small"
              :icon="ArrowRightBold"
              @click="run((editor) => editor.chain().focus().redo().run())"
            />
          </ElTooltip>
        </ElButtonGroup>
      </div>

      <div class="rich-text-editor__mode-switch">
        <ElButton
          :type="mode === 'edit' ? 'primary' : 'default'"
          size="small"
          :disabled="disabled"
          :icon="View"
          @click="toggleMode('edit')"
        >
          编辑
        </ElButton>
        <ElButton
          :type="mode === 'preview' ? 'primary' : 'default'"
          size="small"
          :disabled="disabled"
          :icon="Hide"
          @click="toggleMode('preview')"
        >
          预览
        </ElButton>
      </div>
    </div>

    <div
      class="rich-text-editor__body"
      :style="{ height: typeof height === 'number' ? `${height}px` : height }"
    >
      <TiptapEditor
        v-show="mode === 'edit'"
        :model-value="internalValue"
        :disabled="disabled"
        :placeholder="placeholder"
        height="100%"
        :min-height="
          typeof minHeight === 'number' ? `${minHeight}px` : minHeight
        "
        :show-toolbar="false"
        @created="onCreated"
        @update:model-value="onModelUpdate"
        @change="onChange"
      />
      <div v-if="mode === 'preview'" class="rich-text-editor__preview">
        <RichTextViewer :content="internalValue" />
      </div>
    </div>

    <ElDialog
      v-model="linkDialog"
      title="插入链接"
      width="420px"
      align-center
      destroy-on-close
    >
      <ElInput
        v-model="linkUrl"
        placeholder="https://example.com"
        clearable
        @keyup.enter="confirmLink"
      />
      <template #footer>
        <ElButton @click="closeLinkDialog">取消</ElButton>
        <ElButton
          type="danger"
          :disabled="!editorRef?.isActive('link')"
          @click="removeLink"
        >
          移除链接
        </ElButton>
        <ElButton type="primary" @click="confirmLink">确认</ElButton>
      </template>
    </ElDialog>

    <ElDialog
      v-model="imageDialog"
      title="插入图片"
      width="480px"
      align-center
      destroy-on-close
    >
      <ElTabs v-model="imageTab">
        <ElTabPane label="本地上传" name="upload">
          <div class="rich-text-editor__upload-area">
            <ElUpload
              v-loading="uploadingImage"
              drag
              action=""
              :auto-upload="false"
              :show-file-list="false"
              :on-change="onUploadChange"
              accept="image/*"
            >
              <Picture class="rich-text-editor__upload-icon" />
              <div class="rich-text-editor__upload-text">
                拖拽图片到此处，或<span>点击上传</span>
              </div>
              <template #tip>
                <div class="rich-text-editor__upload-tip">
                  支持 JPG、PNG、GIF、WebP，单张不超过 5MB
                </div>
              </template>
            </ElUpload>
          </div>
        </ElTabPane>
        <ElTabPane label="图片地址" name="url">
          <ElInput
            v-model="imageUrl"
            placeholder="请输入图片 URL"
            clearable
            @keyup.enter="confirmImageUrl"
          />
        </ElTabPane>
      </ElTabs>
      <template #footer>
        <ElButton @click="closeImageDialog">取消</ElButton>
        <ElButton
          v-if="imageTab === 'url'"
          type="primary"
          :disabled="!imageUrl.trim()"
          @click="confirmImageUrl"
        >
          确认
        </ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped lang="scss">
.rich-text-editor {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  transition: border-color 0.2s;

  &:hover,
  &:focus-within {
    border-color: var(--el-border-color-hover);
  }

  &.is-disabled {
    background: var(--el-fill-color-light);
  }
}

.rich-text-editor__header {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-light);
}

.rich-text-editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.rich-text-editor__divider {
  width: 1px;
  height: 22px;
  background: var(--el-border-color);
}

.rich-text-editor__block-select {
  height: 28px;
  padding: 0 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  outline: none;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-small);

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }

  &:focus {
    border-color: var(--el-color-primary);
  }
}

.rich-text-editor__color-line {
  display: inline-block;
  width: 14px;
  height: 3px;
  margin-left: 3px;
  vertical-align: middle;
  border-radius: 2px;
}

.rich-text-editor__mode-switch {
  display: flex;
  gap: 6px;
}

.rich-text-editor__body {
  position: relative;
  flex: 1;
  min-height: 200px;
}

.rich-text-editor__preview {
  box-sizing: border-box;
  height: 100%;
  padding: 16px;
  overflow: auto;
}

.rich-text-editor__upload-area {
  :deep(.el-upload-dragger) {
    width: 100%;
    padding: 24px;
  }
}

.rich-text-editor__upload-icon {
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
}

.rich-text-editor__upload-text {
  font-size: 14px;
  color: var(--el-text-color-regular);

  span {
    color: var(--el-color-primary);
    cursor: pointer;
  }
}

.rich-text-editor__upload-tip {
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  text-align: center;
}

:deep(.rich-text-color-popover) {
  padding: 10px;
}
</style>

<style lang="scss">
.rich-text-editor__color-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
}

.rich-text-editor__color-swatch {
  width: 24px;
  height: 24px;
  padding: 0;
  cursor: pointer;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-small);
  transition: transform 0.15s;

  &:hover {
    transform: scale(1.1);
  }
}

// 隐藏底层 TiptapEditor 的 bubble menu 与原生 toolbar，由当前组件接管
.rich-text-editor {
  .tiptap-editor__toolbar,
  .tiptap-bubble-menu {
    display: none !important;
  }

  .tiptap-editor {
    border: none;
    border-radius: 0;
  }

  .tiptap-editor__content {
    padding: 16px;
  }
}
</style>
