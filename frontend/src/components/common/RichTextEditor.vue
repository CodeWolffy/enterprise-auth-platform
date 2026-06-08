<template>
  <div class="rich-editor" :class="{ 'rich-editor--fullscreen': isFullscreen }">
    <div class="rich-editor__topbar">
      <div>
        <span class="eyebrow">富文本编辑</span>
        <strong>公告正文</strong>
      </div>
      <div class="rich-editor__stats">
        <span>{{ stats.textLength }} 字</span>
        <span>{{ stats.imageCount }} 图</span>
        <span>{{ stats.linkCount }} 链接</span>
      </div>
    </div>

    <div class="rich-editor__toolbar">
      <el-select
        class="toolbar-select"
        placeholder="段落"
        size="small"
        :model-value="currentBlock"
        @change="setBlockType"
      >
        <el-option label="正文" value="paragraph" />
        <el-option label="标题 1" value="heading1" />
        <el-option label="标题 2" value="heading2" />
        <el-option label="标题 3" value="heading3" />
        <el-option label="引用" value="blockquote" />
        <el-option label="代码块" value="codeBlock" />
      </el-select>

      <el-button-group>
        <el-button
          size="small"
          :type="editor?.isActive('bold') ? 'primary' : 'default'"
          @click="editor?.chain().focus().toggleBold().run()"
        >B</el-button>
        <el-button
          size="small"
          :type="editor?.isActive('italic') ? 'primary' : 'default'"
          @click="editor?.chain().focus().toggleItalic().run()"
        >I</el-button>
        <el-button
          size="small"
          :type="editor?.isActive('underline') ? 'primary' : 'default'"
          @click="editor?.chain().focus().toggleUnderline().run()"
        >U</el-button>
        <el-button
          size="small"
          :type="editor?.isActive('strike') ? 'primary' : 'default'"
          @click="editor?.chain().focus().toggleStrike().run()"
        >S</el-button>
      </el-button-group>

      <el-button-group>
        <el-button
          size="small"
          :type="editor?.isActive({ textAlign: 'left' }) ? 'primary' : 'default'"
          @click="editor?.chain().focus().setTextAlign('left').run()"
        >左对齐</el-button>
        <el-button
          size="small"
          :type="editor?.isActive({ textAlign: 'center' }) ? 'primary' : 'default'"
          @click="editor?.chain().focus().setTextAlign('center').run()"
        >居中</el-button>
        <el-button
          size="small"
          :type="editor?.isActive({ textAlign: 'right' }) ? 'primary' : 'default'"
          @click="editor?.chain().focus().setTextAlign('right').run()"
        >右对齐</el-button>
      </el-button-group>

      <el-button-group>
        <el-button
          size="small"
          :type="editor?.isActive('bulletList') ? 'primary' : 'default'"
          @click="editor?.chain().focus().toggleBulletList().run()"
        >项目符号</el-button>
        <el-button
          size="small"
          :type="editor?.isActive('orderedList') ? 'primary' : 'default'"
          @click="editor?.chain().focus().toggleOrderedList().run()"
        >编号</el-button>
      </el-button-group>

      <el-button-group>
        <el-button size="small" @click="editor?.chain().focus().sinkListItem('listItem').run()">增加缩进</el-button>
        <el-button size="small" @click="editor?.chain().focus().liftListItem('listItem').run()">减少缩进</el-button>
      </el-button-group>

      <el-button-group>
        <el-button
          size="small"
          :type="editor?.isActive('link') ? 'primary' : 'default'"
          @click="insertLink"
        >链接</el-button>
        <el-button size="small" :loading="imageUploading" @click="triggerImageUpload">图片</el-button>
        <el-button size="small" @click="editor?.chain().focus().setHorizontalRule().run()">分割线</el-button>
      </el-button-group>

      <el-button-group>
        <el-button size="small" @click="editor?.chain().focus().undo().run()">撤销</el-button>
        <el-button size="small" @click="editor?.chain().focus().redo().run()">重做</el-button>
        <el-button size="small" @click="editor?.chain().focus().clearNodes().unsetAllMarks().run()">清除格式</el-button>
      </el-button-group>

      <el-button-group>
        <el-button size="small" @click="insertTable">表格</el-button>
        <el-button size="small" :type="isFullscreen ? 'primary' : 'default'" @click="toggleFullscreen">
          {{ isFullscreen ? '退出全屏' : '全屏' }}
        </el-button>
      </el-button-group>

      <label class="color-tool">
        <span>文字</span>
        <input type="color" :value="currentColor" @input="(e) => applyColor(e)" />
      </label>
      <label class="color-tool">
        <span>高亮</span>
        <input type="color" :value="currentHighlight" @input="(e) => applyHighlight(e)" />
      </label>

      <input
        ref="fileInputRef"
        type="file"
        accept="image/*"
        style="display: none"
        @change="handleImageUpload"
      />
    </div>

    <div class="rich-editor__workspace">
      <div class="rich-editor__pane rich-editor__pane--edit">
        <div class="rich-editor__pane-head">
          <span>编辑区</span>
          <el-button size="small" text @click="editor?.chain().focus().run()">聚焦编辑</el-button>
        </div>
        <editor-content :editor="editor" class="rich-editor__surface" />
      </div>

      <div class="rich-editor__pane rich-editor__pane--preview">
        <div class="rich-editor__pane-head">
          <span>实时预览</span>
          <el-tag size="small" effect="plain">{{ published ? '发布态' : '草稿态' }}</el-tag>
        </div>
        <article class="notice-preview-card">
          <h2>{{ previewTitle || '公告标题预览' }}</h2>
          <div class="notice-preview-card__meta">
            <span>{{ published ? '已发布' : '草稿' }}</span>
            <span>{{ previewPublishTime || '未设置发布时间' }}</span>
          </div>
          <div class="notice-rich-content" v-html="safeHtml" />
        </article>
      </div>
    </div>

    <el-collapse class="rich-editor__source">
      <el-collapse-item title="HTML 源码编辑" name="source">
        <el-input v-model="sourceHtml" type="textarea" :rows="6" @input="onSourceInput" />
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import Image from '@tiptap/extension-image'
import Link from '@tiptap/extension-link'
import { Table } from '@tiptap/extension-table'
import TableRow from '@tiptap/extension-table-row'
import TableCell from '@tiptap/extension-table-cell'
import TableHeader from '@tiptap/extension-table-header'
import Color from '@tiptap/extension-color'
import { TextStyle } from '@tiptap/extension-text-style'
import Highlight from '@tiptap/extension-highlight'
import Underline from '@tiptap/extension-underline'
import TextAlign from '@tiptap/extension-text-align'
import Placeholder from '@tiptap/extension-placeholder'
import { ElMessage, ElMessageBox } from 'element-plus'
import { uploadStorageFile } from '@/api/modules'
import { countRichTextElements, richTextToPlainText, sanitizeRichText } from '@/utils/richText'

const props = defineProps<{
  modelValue: string
  previewTitle?: string
  previewPublishTime?: string
  published?: boolean
  placeholder?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'fullscreen', value: boolean): void
}>()

const fileInputRef = ref<HTMLInputElement | null>(null)
const isFullscreen = ref(false)

const imageUploading = ref(false)

const editor = useEditor({
  content: sanitizeRichText(props.modelValue),
  extensions: [
    StarterKit,
    Underline,
    Image.configure({
      allowBase64: false,
      HTMLAttributes: {
        loading: 'lazy',
        decoding: 'async',
      },
    }),
    Link.configure({
      openOnClick: false,
      defaultProtocol: 'https',
      HTMLAttributes: {
        target: '_blank',
        rel: 'noopener noreferrer',
      },
    }),
    Table.configure({
      resizable: true,
      HTMLAttributes: {
        class: 'rich-table',
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
      placeholder: props.placeholder || '输入公告正文，可粘贴图文内容',
    }),
  ],
  onUpdate: ({ editor }) => {
    emit('update:modelValue', sanitizeRichText(editor.getHTML()))
  },
})

const currentBlock = computed(() => {
  if (!editor.value) return 'paragraph'
  if (editor.value.isActive('heading', { level: 1 })) return 'heading1'
  if (editor.value.isActive('heading', { level: 2 })) return 'heading2'
  if (editor.value.isActive('heading', { level: 3 })) return 'heading3'
  if (editor.value.isActive('blockquote')) return 'blockquote'
  if (editor.value.isActive('codeBlock')) return 'codeBlock'
  return 'paragraph'
})

const currentColor = computed(() => {
  if (!editor.value) return '#1f2937'
  const attrs = editor.value.getAttributes('textStyle')
  return (attrs.color as string) || '#1f2937'
})

const currentHighlight = computed(() => {
  if (!editor.value) return '#fff7cc'
  const attrs = editor.value.getAttributes('highlight')
  return (attrs.color as string) || '#fff7cc'
})

const stats = computed(() => {
  const html = props.modelValue || ''
  const safe = sanitizeRichText(html)
  return {
    textLength: richTextToPlainText(safe).length,
    imageCount: countRichTextElements(safe, 'img'),
    linkCount: countRichTextElements(safe, 'a[href]'),
  }
})

const safeHtml = computed(() => sanitizeRichText(props.modelValue))

const sourceHtml = computed({
  get: () => sanitizeRichText(props.modelValue),
  set: (val) => emit('update:modelValue', sanitizeRichText(val)),
})

watch(
  () => props.modelValue,
  (value) => {
    const safeValue = sanitizeRichText(value)
    if (editor.value && editor.value.getHTML() !== safeValue) {
      editor.value.commands.setContent(safeValue, false)
    }
  },
)

onBeforeUnmount(() => {
  if (isFullscreen.value) {
    document.body.classList.remove('rich-editor-fullscreen-open')
  }
})

function onSourceInput(val: string) {
  const safeValue = sanitizeRichText(val)
  emit('update:modelValue', safeValue)
  if (editor.value) {
    editor.value.commands.setContent(safeValue, false)
  }
}

function setBlockType(type: string) {
  const chain = editor.value?.chain().focus()
  if (!chain) return

  if (type === 'heading1') {
    chain.setHeading({ level: 1 }).run()
  } else if (type === 'heading2') {
    chain.setHeading({ level: 2 }).run()
  } else if (type === 'heading3') {
    chain.setHeading({ level: 3 }).run()
  } else if (type === 'blockquote') {
    chain.toggleBlockquote().run()
  } else if (type === 'codeBlock') {
    chain.toggleCodeBlock().run()
  } else {
    chain.setParagraph().run()
  }
}

function applyColor(event: Event) {
  const color = (event.target as HTMLInputElement).value
  editor.value?.chain().focus().setColor(color).run()
}

function applyHighlight(event: Event) {
  const color = (event.target as HTMLInputElement).value
  editor.value?.chain().focus().setHighlight({ color }).run()
}

function insertLink() {
  if (!editor.value) return
  if (editor.value.isActive('link')) {
    editor.value.chain().focus().unsetLink().run()
    return
  }
  ElMessageBox.prompt('请输入链接地址，例如 https://example.com', '插入链接', {
    confirmButtonText: '插入',
    cancelButtonText: '取消',
    inputPattern: /\S+/,
    inputErrorMessage: '内容不能为空',
  })
    .then(({ value }) => {
      const url = normalizeUrl(value.trim())
      if (!url) {
        ElMessage.warning('链接地址不合法')
        return
      }
      editor.value?.chain().focus().setLink({ href: url }).run()
    })
    .catch(() => {})
}

function triggerImageUpload() {
  if (imageUploading.value) {
    return
  }
  fileInputRef.value?.click()
}

async function handleImageUpload(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !editor.value) return

  if (!file.type.startsWith('image/')) {
    ElMessage.warning('只能上传图片文件')
    input.value = ''
    return
  }

  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    input.value = ''
    return
  }

  imageUploading.value = true
  try {
    const metadata = await uploadStorageFile(file, 'PUBLIC')
    if (metadata.url) {
      const alt = metadata.originalName || file.name
      editor.value.chain().focus().setImage({ src: metadata.url, alt }).run()
      ElMessage.success('图片已插入')
    } else {
      ElMessage.warning('图片上传成功但未返回 URL')
    }
  } catch {
    ElMessage.error('图片上传失败')
  } finally {
    imageUploading.value = false
    input.value = ''
  }
}

function insertTable() {
  editor.value?.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()
}

function toggleFullscreen() {
  isFullscreen.value = !isFullscreen.value
  document.body.classList.toggle('rich-editor-fullscreen-open', isFullscreen.value)
  emit('fullscreen', isFullscreen.value)
}

function normalizeUrl(value: string) {
  const trimmed = value.trim()
  if (/^(https?:|mailto:|tel:|\/|#)/i.test(trimmed)) return trimmed
  return ''
}
</script>

<style scoped lang="scss">
.rich-editor {
  display: grid;
  gap: 12px;
  width: 100%;
  padding: 12px;
  border: 1px solid #e6ebf2;
  border-radius: 16px;
  background: linear-gradient(180deg, #fbfdff 0%, #f7f9fc 100%);
}

.rich-editor__topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  > div:first-child {
    display: grid;
    gap: 2px;
  }

  strong {
    color: #1f2937;
    font-size: 15px;
  }
}

.rich-editor__stats {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;

  span {
    padding: 3px 8px;
    border-radius: 999px;
    background: #eef4ff;
    color: #315a9c;
    font-size: 12px;
  }
}

.rich-editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.04);
}

.toolbar-select {
  width: 104px;
}

.color-tool {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 24px;
  padding: 0 8px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  color: #606b7a;
  font-size: 12px;

  input {
    width: 22px;
    height: 18px;
    padding: 0;
    border: 0;
    background: transparent;
    cursor: pointer;
  }
}

.rich-editor__workspace {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(280px, 0.9fr);
  gap: 12px;
}

.rich-editor__pane {
  min-width: 0;
  overflow: hidden;
  border: 1px solid #e6ebf2;
  border-radius: 14px;
  background: #fff;
}

.rich-editor__pane-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid #eef1f6;
  background: #fafbfe;
  color: #475569;
  font-size: 13px;
  font-weight: 600;
}

.rich-editor__surface {
  min-height: 360px;
  max-height: 520px;
  overflow: auto;
  padding: 18px;
  background: #fff;
  color: #1f2937;
  outline: none;

  :deep(.ProseMirror) {
    min-height: 360px;
    outline: none;

    p.is-editor-empty:first-child::before {
      content: attr(data-placeholder);
      float: left;
      color: #a0a8b5;
      pointer-events: none;
      height: 0;
    }

    h1 {
      margin: 16px 0 10px;
      color: #111827;
      font-size: 26px;
      line-height: 1.35;
    }

    h2 {
      margin: 16px 0 10px;
      color: #111827;
      font-size: 22px;
      line-height: 1.35;
    }

    h3 {
      margin: 16px 0 10px;
      color: #111827;
      font-size: 18px;
      line-height: 1.35;
    }

    p {
      margin: 0 0 10px;
    }

    blockquote {
      margin: 12px 0;
      padding: 10px 14px;
      border-left: 4px solid #7aa7ff;
      border-radius: 8px;
      background: #f4f7ff;
      color: #475569;
    }

    pre {
      overflow: auto;
      margin: 12px 0;
      padding: 12px;
      border-radius: 10px;
      background: #111827;
      color: #e5e7eb;
    }

    ul,
    ol {
      margin: 0 0 10px 22px;
      padding: 0;
    }

    a {
      color: #1677ff;
      text-decoration: none;

      &:hover {
        text-decoration: underline;
      }
    }

    hr {
      height: 1px;
      margin: 18px 0;
      border: 0;
      background: #e5e7eb;
    }

    img {
      max-width: 100%;
      margin: 8px 0;
      border-radius: 10px;
      box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
    }

    table {
      width: 100%;
      border-collapse: collapse;
      margin: 12px 0;
      border-radius: 8px;
      overflow: hidden;
      border: 1px solid #e5e7eb;
    }

    th,
    td {
      padding: 8px 12px;
      border: 1px solid #e5e7eb;
      text-align: left;
    }

    th {
      background: #f8fafc;
      font-weight: 600;
    }

    tr:nth-child(even) {
      background: #fafbfe;
    }
  }
}

.rich-editor__source :deep(.el-collapse-item__header) {
  height: 36px;
  padding: 0 12px;
  border-radius: 10px;
  background: #fff;
  font-size: 13px;
}

.notice-preview-card {
  min-height: 360px;
  max-height: 520px;
  overflow: auto;
  margin: 0;
  padding: 22px;
  background: #fff;

  h2 {
    margin: 0 0 8px;
    color: #111827;
    font-size: 22px;
    line-height: 1.35;
  }
}

.notice-preview-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 18px;
  color: #8a94a6;
  font-size: 12px;
}

.notice-rich-content {
  color: #1f2937;
  font-size: 14px;
  line-height: 1.75;
  word-break: break-word;

  :deep(h1) {
    margin: 16px 0 10px;
    color: #111827;
    font-size: 26px;
  }

  :deep(h2) {
    margin: 16px 0 10px;
    color: #111827;
    font-size: 22px;
  }

  :deep(h3) {
    margin: 16px 0 10px;
    color: #111827;
    font-size: 18px;
  }

  :deep(p) {
    margin: 0 0 10px;
  }

  :deep(blockquote) {
    margin: 12px 0;
    padding: 10px 14px;
    border-left: 4px solid #7aa7ff;
    border-radius: 8px;
    background: #f4f7ff;
    color: #475569;
  }

  :deep(pre) {
    overflow: auto;
    margin: 12px 0;
    padding: 12px;
    border-radius: 10px;
    background: #111827;
    color: #e5e7eb;
  }

  :deep(ul),
  :deep(ol) {
    margin: 0 0 10px 22px;
    padding: 0;
  }

  :deep(a) {
    color: #1677ff;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }

  :deep(hr) {
    height: 1px;
    margin: 18px 0;
    border: 0;
    background: #e5e7eb;
  }

  :deep(img) {
    max-width: 100%;
    margin: 8px 0;
    border-radius: 10px;
    box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
  }

  :deep(table) {
    width: 100%;
    border-collapse: collapse;
    margin: 12px 0;
    border-radius: 8px;
    overflow: hidden;
    border: 1px solid #e5e7eb;
  }

  :deep(th),
  :deep(td) {
    padding: 8px 12px;
    border: 1px solid #e5e7eb;
    text-align: left;
  }

  :deep(th) {
    background: #f8fafc;
    font-weight: 600;
  }

  :deep(tr:nth-child(even)) {
    background: #fafbfe;
  }
}

.rich-editor--fullscreen {
  position: fixed;
  inset: 16px;
  z-index: 3000;
  overflow: auto;
  box-shadow: 0 24px 72px rgba(15, 23, 42, 0.28);

  .rich-editor__workspace {
    min-height: calc(100vh - 230px);
  }

  .rich-editor__surface,
  .notice-preview-card {
    max-height: none;
  }
}

@media (max-width: 900px) {
  .rich-editor__workspace {
    grid-template-columns: 1fr;
  }
}
</style>