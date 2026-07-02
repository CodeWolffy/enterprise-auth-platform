# @vben-core/editor-ui

基于 TipTap 的富文本编辑器组件包。

## 特性

- 🎨 基于 TipTap v2 - 现代化的富文本编辑器
- 📦 开箱即用，支持 v-model 双向绑定
- 🛠 支持自定义工具栏和扩展
- 💪 完整的 TypeScript 支持
- 🎯 适配 Vben Admin 架构
- ✨ 丰富的格式支持（标题、列表、表格、图片、链接等）

## 安装

依赖已在包中配置，无需额外安装。

## 使用

### 基础用法

```vue
<script setup lang="ts">
import { ref } from 'vue';
import { TiptapEditor } from '@vben-core/editor-ui';

const content = ref('<p>初始内容</p>');
</script>

<template>
  <TiptapEditor
    v-model="content"
    placeholder="请输入内容"
    height="500px"
  />
</template>
```

### 高级配置

```vue
<script setup lang="ts">
import { ref } from 'vue';
import { TiptapEditor } from '@vben-core/editor-ui';
import type { Editor } from '@vben-core/editor-ui';

const content = ref('');
const editorRef = ref();

// 编辑器创建完成回调
function handleCreated(editor: Editor) {
  console.log('编辑器创建完成', editor);
}

// 编辑器内容改变回调
function handleChange(editor: Editor) {
  console.log('内容改变', editor.getHTML());
}

// 插入图片
function insertImage() {
  const url = 'https://example.com/image.jpg';
  editorRef.value?.insertImage(url, '图片描述');
}

// 插入链接
function insertLink() {
  const url = 'https://example.com';
  editorRef.value?.setLink(url);
}
</script>

<template>
  <div>
    <TiptapEditor
      ref="editorRef"
      v-model="content"
      placeholder="请输入内容"
      height="400px"
      :show-toolbar="true"
      @created="handleCreated"
      @change="handleChange"
    />
    
    <button @click="insertImage">插入图片</button>
    <button @click="insertLink">插入链接</button>
  </div>
</template>
```

### 禁用编辑器

```vue
<template>
  <TiptapEditor
    v-model="content"
    :disabled="true"
    placeholder="只读模式"
  />
</template>
```

### 隐藏工具栏

```vue
<template>
  <TiptapEditor
    v-model="content"
    :show-toolbar="false"
  />
</template>
```

## API

### Props

| 属性 | 说明 | 类型 | 默认值 |
| --- | --- | --- | --- |
| modelValue | 编辑器内容（HTML） | `string` | `''` |
| disabled | 是否禁用 | `boolean` | `false` |
| placeholder | 占位符文本 | `string` | `'请输入内容...'` |
| height | 编辑器高度 | `string` | `'400px'` |
| width | 编辑器宽度 | `string` | `'100%'` |
| showToolbar | 是否显示工具栏 | `boolean` | `true` |

### Events

| 事件名 | 说明 | 参数 |
| --- | --- | --- |
| update:modelValue | 内容更新 | `(value: string)` |
| change | 内容改变 | `(editor: Editor)` |
| created | 编辑器创建完成 | `(editor: Editor)` |

### Expose Methods

| 方法名 | 说明 | 参数 | 返回值 |
| --- | --- | --- | --- |
| getEditor | 获取编辑器实例 | - | `Editor \| undefined` |
| insertImage | 插入图片 | `(url: string, alt?: string)` | `void` |
| setLink | 设置链接 | `(url: string)` | `void` |
| unsetLink | 移除链接 | - | `void` |
| insertTable | 插入表格 | `(rows?: number, cols?: number)` | `void` |
| setBlockType | 设置块类型 | `(type: string)` | `void` |

## 支持的格式

- **文本格式**: 粗体、斜体、下划线、删除线
- **标题**: H1, H2, H3
- **对齐**: 左对齐、居中、右对齐
- **列表**: 无序列表、有序列表
- **块元素**: 引用、代码块
- **内联元素**: 链接、颜色、高亮
- **媒体**: 图片
- **表格**: 可调整大小的表格
- **历史**: 撤销、重做

## TipTap 扩展

该组件内置以下 TipTap 扩展：

- `StarterKit` - 基础功能集
- `Underline` - 下划线
- `Image` - 图片
- `Link` - 链接
- `Table` - 表格
- `Color` - 文字颜色
- `TextStyle` - 文本样式
- `Highlight` - 高亮
- `TextAlign` - 文本对齐
- `Placeholder` - 占位符

## 自定义样式

可以通过 CSS 变量自定义样式：

```css
.tiptap-editor {
  --editor-border-color: #e5e7eb;
  --editor-bg-color: #fff;
  --toolbar-bg-color: #f9fafb;
}
```

## 注意事项

1. 编辑器内容为 HTML 格式，需要注意 XSS 安全
2. 建议对用户输入的内容进行 HTML 清洗
3. 图片 URL 需要是有效的可访问地址
4. 表格支持拖拽调整列宽

## 与 Legacy 项目对比

| 特性 | WangEditor | TipTap |
|-----|-----------|---------|
| 框架 | 独立 | Vue 3 优化 |
| 扩展性 | 中等 | 优秀 |
| TypeScript | 部分 | 完整 |
| 包体积 | 较大 | 适中 |
| 社区活跃度 | 中等 | 高 |
| 文档质量 | 良好 | 优秀 |

## 更多资源

- [TipTap 官方文档](https://tiptap.dev/)
- [TipTap Vue 3 集成](https://tiptap.dev/installation/vue3)
- [TipTap 扩展列表](https://tiptap.dev/extensions)