<script setup lang="ts">
import type { Editor } from '@tiptap/vue-3';

import { computed, onBeforeUnmount, ref, watch } from 'vue';

import { BubbleMenu, EditorContent, useEditor } from '@tiptap/vue-3';
import Color from '@tiptap/extension-color';
import Highlight from '@tiptap/extension-highlight';
import Image from '@tiptap/extension-image';
import Link from '@tiptap/extension-link';
import Placeholder from '@tiptap/extension-placeholder';
import { Table } from '@tiptap/extension-table';
import TableCell from '@tiptap/extension-table-cell';
import TableHeader from '@tiptap/extension-table-header';
import TableRow from '@tiptap/extension-table-row';
import TextAlign from '@tiptap/extension-text-align';
import { TextStyle } from '@tiptap/extension-text-style';
import Underline from '@tiptap/extension-underline';
import StarterKit from '@tiptap/starter-kit';

interface Props {
  /** 编辑器内容 HTML */
  modelValue?: string;
  /** 是否禁用 */
  disabled?: boolean;
  /** 占位符文本 */
  placeholder?: string;
  /** 编辑区固定高度 */
  height?: number | string;
  /** 编辑区最小高度 */
  minHeight?: number | string;
  /** 编辑器宽度 */
  width?: number | string;
  /** 是否显示工具栏 */
  showToolbar?: boolean;
  /** 上传图片适配器，返回可访问 URL */
  uploadImage?: (file: File) => Promise<string>;
  /** 允许上传的图片 MIME 类型 */
  allowedImageTypes?: string[];
  /** 图片最大体积，单位 byte */
  maxImageSize?: number;
}

const props = withDefaults(defineProps<Props>(), {
  allowedImageTypes: () => ['image/jpeg', 'image/png', 'image/gif', 'image/webp'],
  disabled: false,
  height: '400px',
  maxImageSize: 5 * 1024 * 1024,
  minHeight: '240px',
  modelValue: '',
  placeholder: '请输入内容...',
  showToolbar: true,
  uploadImage: undefined,
  width: '100%',
});

const emit = defineEmits([
  'update:modelValue',
  'change',
  'created',
  'error',
]);

const editorRef = useEditor({
  content: props.modelValue,
  editable: !props.disabled,
  extensions: [
    StarterKit,
    Underline,
    Image.configure({
      allowBase64: false,
      HTMLAttributes: {
        decoding: 'async',
        loading: 'lazy',
      },
    }),
    Link.configure({
      defaultProtocol: 'https',
      openOnClick: false,
      HTMLAttributes: {
        rel: 'noopener noreferrer',
        target: '_blank',
      },
    }),
    Table.configure({
      resizable: true,
      HTMLAttributes: {
        class: 'tiptap-table',
      },
    }),
    TableRow,
    TableHeader,
    TableCell,
    Color,
    TextStyle,
    Highlight.configure({ multicolor: true }),
    TextAlign.configure({ types: ['heading', 'paragraph'] }),
    Placeholder.configure({
      placeholder: props.placeholder,
    }),
  ],
  onCreate: ({ editor }) => {
    emit('created', editor);
  },
  onUpdate: ({ editor }) => {
    if (sourceMode.value) return;
    const html = editor.getHTML();
    emit('update:modelValue', html);
    emit('change', editor);
  },
});

const showColorPanel = ref(false);
const showHighlightPanel = ref(false);
const showLinkPanel = ref(false);
const showImagePanel = ref(false);
const showTablePanel = ref(false);
const fullscreen = ref(false);
const sourceMode = ref(false);
const sourceHtml = ref('');
const uploadingImage = ref(false);

const linkUrl = ref('');
const imageUrl = ref('');
const tableRows = ref(3);
const tableCols = ref(3);
const imageFileInputRef = ref<HTMLInputElement>();

const colorPalette = [
  '#111827',
  '#374151',
  '#6b7280',
  '#9ca3af',
  '#d1d5db',
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

const highlightPalette = [
  'transparent',
  '#fef08a',
  '#fde047',
  '#bbf7d0',
  '#99f6e4',
  '#bae6fd',
  '#c7d2fe',
  '#e9d5ff',
];

const editorStyle = computed(() => ({
  width: normalizeSize(props.width),
}));

const contentStyle = computed(() => ({
  height: normalizeSize(fullscreen.value ? 'calc(100vh - 112px)' : props.height),
  minHeight: normalizeSize(props.minHeight),
}));

const currentBlock = computed(() => {
  const editor = editorRef.value;
  if (!editor) return 'paragraph';
  if (editor.isActive('heading', { level: 1 })) return 'heading1';
  if (editor.isActive('heading', { level: 2 })) return 'heading2';
  if (editor.isActive('heading', { level: 3 })) return 'heading3';
  if (editor.isActive('blockquote')) return 'blockquote';
  if (editor.isActive('codeBlock')) return 'codeBlock';
  return 'paragraph';
});

const editorClasses = computed(() => ({
  'is-disabled': props.disabled,
  'is-fullscreen': fullscreen.value,
  'is-source-mode': sourceMode.value,
}));

watch(
  () => props.disabled,
  (disabled) => {
    editorRef.value?.setEditable(!disabled && !sourceMode.value);
  },
);

watch(
  () => props.modelValue,
  (value) => {
    const editor = editorRef.value;
    if (sourceMode.value) {
      sourceHtml.value = value || '';
      return;
    }
    if (editor && editor.getHTML() !== value) {
      editor.commands.setContent(value || '', false);
    }
  },
);

onBeforeUnmount(() => {
  editorRef.value?.destroy();
});

function normalizeSize(value?: number | string) {
  if (typeof value === 'number') return `${value}px`;
  return value || undefined;
}

function closeFloatingPanels() {
  showColorPanel.value = false;
  showHighlightPanel.value = false;
}

function showError(message: string) {
  emit('error', message);
}

function setBlockType(type: string) {
  const chain = editorRef.value?.chain().focus();
  if (!chain) return;

  switch (type) {
    case 'heading1':
      chain.setHeading({ level: 1 }).run();
      break;
    case 'heading2':
      chain.setHeading({ level: 2 }).run();
      break;
    case 'heading3':
      chain.setHeading({ level: 3 }).run();
      break;
    case 'blockquote':
      chain.toggleBlockquote().run();
      break;
    case 'codeBlock':
      chain.toggleCodeBlock().run();
      break;
    default:
      chain.setParagraph().run();
  }
}

function runCommand(command: (editor: Editor) => void) {
  const editor = editorRef.value;
  if (!editor || props.disabled || sourceMode.value) return;
  command(editor);
}

function setTextColor(color: string) {
  runCommand((editor) => editor.chain().focus().setColor(color).run());
  showColorPanel.value = false;
}

function setHighlightColor(color: string) {
  runCommand((editor) => {
    if (color === 'transparent') {
      editor.chain().focus().unsetHighlight().run();
      return;
    }
    editor.chain().focus().setHighlight({ color }).run();
  });
  showHighlightPanel.value = false;
}

function openLinkPanel() {
  const editor = editorRef.value;
  if (!editor) return;
  linkUrl.value = editor.getAttributes('link').href || '';
  showLinkPanel.value = true;
}

function confirmLink() {
  const url = linkUrl.value.trim();
  if (!url) {
    unsetLink();
    return;
  }
  runCommand((editor) => editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run());
  linkUrl.value = '';
  showLinkPanel.value = false;
}

function unsetLink() {
  runCommand((editor) => editor.chain().focus().extendMarkRange('link').unsetLink().run());
  linkUrl.value = '';
  showLinkPanel.value = false;
}

function confirmImageUrl() {
  const url = imageUrl.value.trim();
  if (!url) return;
  runCommand((editor) => editor.chain().focus().setImage({ alt: '', src: url }).run());
  imageUrl.value = '';
  showImagePanel.value = false;
}

function selectImageFile() {
  imageFileInputRef.value?.click();
}

async function uploadSelectedImage(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) return;
  if (!props.allowedImageTypes.includes(file.type)) {
    showError('仅支持 JPG、PNG、GIF、WebP 图片');
    return;
  }
  if (file.size > props.maxImageSize) {
    showError(`图片不能超过 ${Math.round(props.maxImageSize / 1024 / 1024)}MB`);
    return;
  }
  if (!props.uploadImage) {
    showError('当前页面未配置图片上传能力');
    return;
  }
  try {
    uploadingImage.value = true;
    const url = await props.uploadImage(file);
    if (!url) {
      showError('图片上传后未返回可访问地址');
      return;
    }
    runCommand((editor) => editor.chain().focus().setImage({ alt: file.name, src: url }).run());
    showImagePanel.value = false;
  } catch (error: any) {
    showError(error?.message || '图片上传失败');
  } finally {
    uploadingImage.value = false;
  }
}

function confirmTable() {
  const rows = Math.max(1, Math.min(20, Number(tableRows.value) || 3));
  const cols = Math.max(1, Math.min(20, Number(tableCols.value) || 3));
  runCommand((editor) => editor.chain().focus().insertTable({ cols, rows, withHeaderRow: true }).run());
  showTablePanel.value = false;
}

function toggleSourceMode() {
  const editor = editorRef.value;
  if (!editor) return;
  if (sourceMode.value) {
    editor.commands.setContent(sourceHtml.value || '', false);
    emit('update:modelValue', editor.getHTML());
    editor.setEditable(!props.disabled);
    sourceMode.value = false;
    return;
  }
  sourceHtml.value = editor.getHTML();
  sourceMode.value = true;
  editor.setEditable(false);
}

function toggleFullscreen() {
  fullscreen.value = !fullscreen.value;
}

defineExpose({
  getEditor: () => editorRef.value,
});
</script>

<template>
  <div class="tiptap-editor" :class="editorClasses" :style="editorStyle">
    <div v-if="showToolbar" class="tiptap-editor__toolbar">
      <select
        :disabled="disabled || sourceMode"
        :value="currentBlock"
        class="tiptap-toolbar__select"
        @change="(e) => setBlockType((e.target as HTMLSelectElement).value)"
      >
        <option value="paragraph">正文</option>
        <option value="heading1">标题 1</option>
        <option value="heading2">标题 2</option>
        <option value="heading3">标题 3</option>
        <option value="blockquote">引用</option>
        <option value="codeBlock">代码块</option>
      </select>

      <div class="toolbar-divider" />

      <div class="tiptap-toolbar__group">
        <button :class="{ 'is-active': editorRef?.isActive('bold') }" title="粗体" type="button" @click="runCommand((editor) => editor.chain().focus().toggleBold().run())">B</button>
        <button :class="{ 'is-active': editorRef?.isActive('italic') }" title="斜体" type="button" @click="runCommand((editor) => editor.chain().focus().toggleItalic().run())">I</button>
        <button :class="{ 'is-active': editorRef?.isActive('underline') }" title="下划线" type="button" @click="runCommand((editor) => editor.chain().focus().toggleUnderline().run())">U</button>
        <button :class="{ 'is-active': editorRef?.isActive('strike') }" title="删除线" type="button" @click="runCommand((editor) => editor.chain().focus().toggleStrike().run())">S</button>
      </div>

      <div class="toolbar-divider" />

      <div class="tiptap-toolbar__group">
        <button :class="{ 'is-active': editorRef?.isActive({ textAlign: 'left' }) }" title="左对齐" type="button" @click="runCommand((editor) => editor.chain().focus().setTextAlign('left').run())">左</button>
        <button :class="{ 'is-active': editorRef?.isActive({ textAlign: 'center' }) }" title="居中" type="button" @click="runCommand((editor) => editor.chain().focus().setTextAlign('center').run())">中</button>
        <button :class="{ 'is-active': editorRef?.isActive({ textAlign: 'right' }) }" title="右对齐" type="button" @click="runCommand((editor) => editor.chain().focus().setTextAlign('right').run())">右</button>
        <button :class="{ 'is-active': editorRef?.isActive({ textAlign: 'justify' }) }" title="两端对齐" type="button" @click="runCommand((editor) => editor.chain().focus().setTextAlign('justify').run())">齐</button>
      </div>

      <div class="toolbar-divider" />

      <div class="tiptap-toolbar__group">
        <button :class="{ 'is-active': editorRef?.isActive('bulletList') }" title="无序列表" type="button" @click="runCommand((editor) => editor.chain().focus().toggleBulletList().run())">•</button>
        <button :class="{ 'is-active': editorRef?.isActive('orderedList') }" title="有序列表" type="button" @click="runCommand((editor) => editor.chain().focus().toggleOrderedList().run())">1.</button>
      </div>

      <div class="toolbar-divider" />

      <div class="tiptap-toolbar__group">
        <div class="color-picker-wrapper">
          <button class="color-btn" title="文字颜色" type="button" @click="showColorPanel = !showColorPanel; showHighlightPanel = false">
            A
            <span class="color-underline" :style="{ backgroundColor: editorRef?.getAttributes('textStyle').color || '#3b82f6' }" />
          </button>
          <div v-if="showColorPanel" class="color-panel">
            <button v-for="color in colorPalette" :key="color" class="color-swatch" :style="{ backgroundColor: color }" type="button" @click="setTextColor(color)" />
          </div>
        </div>
        <div class="color-picker-wrapper">
          <button class="highlight-btn" title="文本高亮" type="button" @click="showHighlightPanel = !showHighlightPanel; showColorPanel = false">H</button>
          <div v-if="showHighlightPanel" class="color-panel">
            <button v-for="color in highlightPalette" :key="color" class="color-swatch" :style="{ backgroundColor: color === 'transparent' ? '#fff' : color }" type="button" @click="setHighlightColor(color)">
              <span v-if="color === 'transparent'" class="transparent-line" />
            </button>
          </div>
        </div>
      </div>

      <div class="toolbar-divider" />

      <div class="tiptap-toolbar__group">
        <button :class="{ 'is-active': editorRef?.isActive('link') }" title="链接" type="button" @click="openLinkPanel">链接</button>
        <button title="移除链接" type="button" @click="unsetLink">断链</button>
        <button title="图片" type="button" @click="showImagePanel = true; closeFloatingPanels()">图片</button>
        <button :class="{ 'is-active': editorRef?.isActive('table') }" title="表格" type="button" @click="showTablePanel = true; closeFloatingPanels()">表格</button>
      </div>

      <div v-if="editorRef?.isActive('table')" class="tiptap-toolbar__group tiptap-toolbar__group--table">
        <button title="前插列" type="button" @click="runCommand((editor) => editor.chain().focus().addColumnBefore().run())">前列</button>
        <button title="后插列" type="button" @click="runCommand((editor) => editor.chain().focus().addColumnAfter().run())">后列</button>
        <button title="删列" type="button" @click="runCommand((editor) => editor.chain().focus().deleteColumn().run())">删列</button>
        <button title="前插行" type="button" @click="runCommand((editor) => editor.chain().focus().addRowBefore().run())">前行</button>
        <button title="后插行" type="button" @click="runCommand((editor) => editor.chain().focus().addRowAfter().run())">后行</button>
        <button title="删行" type="button" @click="runCommand((editor) => editor.chain().focus().deleteRow().run())">删行</button>
        <button title="删表" type="button" @click="runCommand((editor) => editor.chain().focus().deleteTable().run())">删表</button>
      </div>

      <div class="toolbar-divider" />

      <div class="tiptap-toolbar__group">
        <button title="清除格式" type="button" @click="runCommand((editor) => editor.chain().focus().unsetAllMarks().clearNodes().run())">清格</button>
        <button title="分割线" type="button" @click="runCommand((editor) => editor.chain().focus().setHorizontalRule().run())">分割</button>
        <button title="撤销" type="button" @click="runCommand((editor) => editor.chain().focus().undo().run())">撤销</button>
        <button title="重做" type="button" @click="runCommand((editor) => editor.chain().focus().redo().run())">重做</button>
      </div>

      <div class="toolbar-divider" />

      <div class="tiptap-toolbar__group">
        <button :class="{ 'is-active': sourceMode }" title="源码模式" type="button" @click="toggleSourceMode">源码</button>
        <button :class="{ 'is-active': fullscreen }" title="全屏" type="button" @click="toggleFullscreen">{{ fullscreen ? '还原' : '全屏' }}</button>
      </div>
    </div>

    <BubbleMenu v-if="editorRef && !sourceMode" :editor="editorRef" :tippy-options="{ duration: 120 }" class="tiptap-bubble-menu">
      <button :class="{ 'is-active': editorRef.isActive('bold') }" type="button" @click="runCommand((editor) => editor.chain().focus().toggleBold().run())">B</button>
      <button :class="{ 'is-active': editorRef.isActive('italic') }" type="button" @click="runCommand((editor) => editor.chain().focus().toggleItalic().run())">I</button>
      <button :class="{ 'is-active': editorRef.isActive('link') }" type="button" @click="openLinkPanel">链接</button>
    </BubbleMenu>

    <div v-if="showLinkPanel" class="editor-modal" @click.self="showLinkPanel = false">
      <div class="editor-modal__content">
        <h3>插入链接</h3>
        <input v-model="linkUrl" class="editor-modal__input" placeholder="https://example.com" @keyup.enter="confirmLink">
        <div class="editor-modal__actions">
          <button type="button" @click="showLinkPanel = false">取消</button>
          <button type="button" @click="unsetLink">移除链接</button>
          <button type="button" @click="confirmLink">确认</button>
        </div>
      </div>
    </div>

    <div v-if="showImagePanel" class="editor-modal" @click.self="showImagePanel = false">
      <div class="editor-modal__content">
        <h3>插入图片</h3>
        <input v-model="imageUrl" class="editor-modal__input" placeholder="图片 URL" @keyup.enter="confirmImageUrl">
        <input ref="imageFileInputRef" accept="image/*" class="editor-file-input" type="file" @change="uploadSelectedImage">
        <div class="editor-modal__actions editor-modal__actions--split">
          <button type="button" @click="showImagePanel = false">取消</button>
          <span class="editor-modal__spacer" />
          <button :disabled="uploadingImage" type="button" @click="selectImageFile">{{ uploadingImage ? '上传中...' : '上传图片' }}</button>
          <button type="button" @click="confirmImageUrl">使用 URL</button>
        </div>
      </div>
    </div>

    <div v-if="showTablePanel" class="editor-modal" @click.self="showTablePanel = false">
      <div class="editor-modal__content">
        <h3>插入表格</h3>
        <div class="editor-form-row">
          <label>行数 <input v-model.number="tableRows" max="20" min="1" type="number"></label>
          <label>列数 <input v-model.number="tableCols" max="20" min="1" type="number"></label>
        </div>
        <div class="editor-modal__actions">
          <button type="button" @click="showTablePanel = false">取消</button>
          <button type="button" @click="confirmTable">确认</button>
        </div>
      </div>
    </div>

    <textarea
      v-if="sourceMode"
      v-model="sourceHtml"
      class="tiptap-editor__source"
      :style="contentStyle"
      spellcheck="false"
    />
    <EditorContent
      v-else
      :editor="editorRef"
      class="tiptap-editor__content"
      :style="contentStyle"
    />
  </div>
</template>

<style scoped>
.tiptap-editor {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
  overflow: hidden;
}

.tiptap-editor.is-fullscreen {
  position: fixed;
  inset: 16px;
  z-index: 3000;
  width: auto !important;
  box-shadow: 0 20px 60px rgb(15 23 42 / 25%);
}

.tiptap-editor.is-disabled {
  background: #f9fafb;
}

.tiptap-editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px;
  border-bottom: 1px solid #e5e7eb;
  background: #f8fafc;
}

.toolbar-divider {
  width: 1px;
  height: 26px;
  background: #e5e7eb;
}

.tiptap-toolbar__select {
  height: 32px;
  padding: 0 10px;
  border: 1px solid #d1d5db;
  border-radius: 7px;
  background: #fff;
  color: #374151;
  font-size: 14px;
}

.tiptap-toolbar__group {
  display: flex;
  gap: 4px;
}

.tiptap-toolbar__group button,
.tiptap-bubble-menu button {
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  border: 1px solid #d1d5db;
  border-radius: 7px;
  background: #fff;
  color: #374151;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.16s ease;
}

.tiptap-toolbar__group--table button {
  min-width: 44px;
}

.tiptap-toolbar__group button:hover,
.tiptap-bubble-menu button:hover {
  background: #eff6ff;
  border-color: #93c5fd;
  color: #1d4ed8;
}

.tiptap-toolbar__group button:disabled,
.tiptap-toolbar__select:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.tiptap-toolbar__group button.is-active,
.tiptap-bubble-menu button.is-active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}

.color-picker-wrapper {
  position: relative;
  display: inline-block;
}

.color-btn {
  position: relative;
  flex-direction: column;
  line-height: 1;
}

.color-underline {
  display: block;
  width: 16px;
  height: 3px;
  margin: 2px auto 0;
  border-radius: 2px;
}

.color-panel {
  position: absolute;
  top: 40px;
  left: 0;
  z-index: 20;
  display: grid;
  grid-template-columns: repeat(7, 22px);
  gap: 6px;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 32px rgb(15 23 42 / 18%);
}

.color-swatch {
  position: relative;
  width: 22px !important;
  min-width: 22px !important;
  height: 22px !important;
  padding: 0 !important;
  border: 1px solid #d1d5db !important;
  border-radius: 6px !important;
}

.transparent-line {
  position: absolute;
  top: 10px;
  left: 2px;
  width: 18px;
  height: 2px;
  background: #ef4444;
  transform: rotate(-35deg);
}

.tiptap-bubble-menu {
  display: flex;
  gap: 4px;
  padding: 6px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 12px 32px rgb(15 23 42 / 18%);
}

.editor-modal {
  position: fixed;
  inset: 0;
  z-index: 4000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgb(15 23 42 / 42%);
}

.editor-modal__content {
  min-width: 360px;
  max-width: 92vw;
  padding: 22px;
  border-radius: 12px;
  background: #fff;
  box-shadow: 0 24px 64px rgb(15 23 42 / 28%);
}

.editor-modal__content h3 {
  margin: 0 0 16px;
  color: #111827;
  font-size: 16px;
  font-weight: 700;
}

.editor-modal__input {
  box-sizing: border-box;
  width: 100%;
  height: 38px;
  margin-bottom: 16px;
  padding: 0 12px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  outline: none;
  font-size: 14px;
}

.editor-modal__input:focus {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgb(37 99 235 / 12%);
}

.editor-file-input {
  display: none;
}

.editor-form-row {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
}

.editor-form-row label {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #374151;
  font-size: 14px;
}

.editor-form-row input {
  width: 72px;
  height: 34px;
  border: 1px solid #d1d5db;
  border-radius: 7px;
  text-align: center;
}

.editor-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.editor-modal__actions--split {
  align-items: center;
}

.editor-modal__spacer {
  flex: 1;
}

.editor-modal__actions button {
  height: 34px;
  padding: 0 16px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  color: #374151;
  cursor: pointer;
}

.editor-modal__actions button:last-child {
  border-color: #2563eb;
  background: #2563eb;
  color: #fff;
}

.editor-modal__actions button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.tiptap-editor__content,
.tiptap-editor__source {
  box-sizing: border-box;
  width: 100%;
  overflow: auto;
  padding: 16px;
}

.tiptap-editor__source {
  border: 0;
  outline: none;
  resize: vertical;
  background: #0f172a;
  color: #e5e7eb;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 13px;
  line-height: 1.7;
}

.tiptap-editor__content :deep(.ProseMirror) {
  min-height: 100%;
  outline: none;
  color: #111827;
  line-height: 1.75;
}

.tiptap-editor__content :deep(.ProseMirror p.is-editor-empty:first-child::before) {
  content: attr(data-placeholder);
  float: left;
  height: 0;
  color: #9ca3af;
  pointer-events: none;
}

.tiptap-editor__content :deep(.ProseMirror h1),
.tiptap-editor__content :deep(.ProseMirror h2),
.tiptap-editor__content :deep(.ProseMirror h3) {
  margin: 16px 0 8px;
  font-weight: 700;
  line-height: 1.35;
}

.tiptap-editor__content :deep(.ProseMirror h1) {
  font-size: 26px;
}

.tiptap-editor__content :deep(.ProseMirror h2) {
  font-size: 22px;
}

.tiptap-editor__content :deep(.ProseMirror h3) {
  font-size: 18px;
}

.tiptap-editor__content :deep(.ProseMirror p) {
  margin: 0 0 10px;
}

.tiptap-editor__content :deep(.ProseMirror blockquote) {
  margin: 12px 0;
  padding: 10px 14px;
  border-left: 4px solid #2563eb;
  border-radius: 0 8px 8px 0;
  background: #eff6ff;
  color: #1e40af;
}

.tiptap-editor__content :deep(.ProseMirror pre) {
  overflow: auto;
  margin: 12px 0;
  padding: 14px;
  border-radius: 8px;
  background: #111827;
  color: #e5e7eb;
}

.tiptap-editor__content :deep(.ProseMirror ul),
.tiptap-editor__content :deep(.ProseMirror ol) {
  margin: 0 0 10px 22px;
  padding: 0;
}

.tiptap-editor__content :deep(.ProseMirror a) {
  color: #2563eb;
  text-decoration: underline;
}

.tiptap-editor__content :deep(.ProseMirror img) {
  max-width: 100%;
  height: auto;
  margin: 10px 0;
  border-radius: 8px;
}

.tiptap-editor__content :deep(.ProseMirror .tiptap-table) {
  width: 100%;
  margin: 14px 0;
  border-collapse: collapse;
  table-layout: fixed;
}

.tiptap-editor__content :deep(.ProseMirror .tiptap-table th),
.tiptap-editor__content :deep(.ProseMirror .tiptap-table td) {
  position: relative;
  min-width: 48px;
  padding: 8px 10px;
  border: 1px solid #d1d5db;
  vertical-align: top;
}

.tiptap-editor__content :deep(.ProseMirror .tiptap-table th) {
  background: #f8fafc;
  font-weight: 700;
}

.tiptap-editor__content :deep(.ProseMirror .tiptap-table .selectedCell::after) {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 2;
  background: rgb(37 99 235 / 12%);
  pointer-events: none;
}

.tiptap-editor__content :deep(.ProseMirror .tiptap-table .column-resize-handle) {
  position: absolute;
  top: 0;
  right: -2px;
  bottom: -2px;
  width: 4px;
  background-color: #2563eb;
  pointer-events: none;
}
</style>