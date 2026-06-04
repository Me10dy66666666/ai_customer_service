<template>
  <div class="se-root" :class="{ 'se-editing': editable }" @click.stop>
    <editor-content :editor="editor" class="se-content" />
  </div>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { Table } from '@tiptap/extension-table'
import { TableRow } from '@tiptap/extension-table-row'
import { TableCell } from '@tiptap/extension-table-cell'
import { TableHeader } from '@tiptap/extension-table-header'
import { marked } from 'marked'

const props = defineProps({
  content: { type: String, default: '' },
  editable: { type: Boolean, default: false },
  active: { type: Boolean, default: false }
})

const emit = defineEmits(['update:content', 'focus', 'blur'])

const markdownToHtml = (md) => {
  if (!md) return ''
  return marked.parse(md)
}

const editor = useEditor({
  content: markdownToHtml(props.content),
  editable: props.editable,
  extensions: [
    StarterKit.configure({
      heading: { levels: [1, 2, 3, 4, 5, 6] }
    }),
    Table.configure({ resizable: false }),
    TableRow,
    TableCell,
    TableHeader
  ],
  onUpdate({ editor }) {
    const html = editor.getHTML()
    const md = htmlToMarkdown(html)
    emit('update:content', md)
  },
  onFocus() {
    emit('focus')
  },
  onBlur() {
    emit('blur')
  }
})

watch(() => props.content, (newContent) => {
  if (!editor.value) return
  const currentMd = htmlToMarkdown(editor.value.getHTML())
  if (newContent !== currentMd) {
    editor.value.commands.setContent(markdownToHtml(newContent), false)
  }
})

watch(() => props.editable, (newEditable) => {
  if (!editor.value) return
  editor.value.setEditable(newEditable)
})

watch(() => props.active, (newActive) => {
  if (!editor.value) return
  if (newActive) editor.value.commands.focus()
})

onBeforeUnmount(() => {
  if (editor.value) editor.value.destroy()
})

function htmlToMarkdown(html) {
  if (!html) return ''
  let md = html
  md = md.replace(/<h1>(.*?)<\/h1>/gi, (_, t) => '\n# ' + stripTags(t) + '\n')
  md = md.replace(/<h2>(.*?)<\/h2>/gi, (_, t) => '\n## ' + stripTags(t) + '\n')
  md = md.replace(/<h3>(.*?)<\/h3>/gi, (_, t) => '\n### ' + stripTags(t) + '\n')
  md = md.replace(/<h4>(.*?)<\/h4>/gi, (_, t) => '\n#### ' + stripTags(t) + '\n')
  md = md.replace(/<h5>(.*?)<\/h5>/gi, (_, t) => '\n##### ' + stripTags(t) + '\n')
  md = md.replace(/<h6>(.*?)<\/h6>/gi, (_, t) => '\n###### ' + stripTags(t) + '\n')
  md = md.replace(/<p>(.*?)<\/p>/gi, (_, t) => '\n' + preserveInline(t) + '\n')
  md = md.replace(/<strong>(.*?)<\/strong>/gi, '**$1**')
  md = md.replace(/<b>(.*?)<\/b>/gi, '**$1**')
  md = md.replace(/<em>(.*?)<\/em>/gi, '*$1*')
  md = md.replace(/<i>(.*?)<\/i>/gi, '*$1*')
  md = md.replace(/<code>(.*?)<\/code>/gi, '`$1`')
  md = md.replace(/<blockquote>(.*?)<\/blockquote>/gi, (_, t) => '\n> ' + stripTags(t).replace(/\n/g, '\n> ') + '\n')
  md = md.replace(/<ul>(.*?)<\/ul>/gi, (_, t) => t.replace(/<li>(.*?)<\/li>/gi, (_, item) => '\n- ' + stripTags(item)))
  md = md.replace(/<ol.*?>(.*?)<\/ol>/gi, (_, t) => {
    let idx = 0
    return t.replace(/<li>(.*?)<\/li>/gi, (_, item) => '\n' + (++idx) + '. ' + stripTags(item))
  })
  md = md.replace(/<table>(.*?)<\/table>/gi, (_, t) => '\n' + t + '\n')
  md = md.replace(/<thead>(.*?)<\/thead>/gi, '$1')
  md = md.replace(/<tbody>(.*?)<\/tbody>/gi, '$1')
  md = md.replace(/<tr>(.*?)<\/tr>/gi, (_, cells) => '|' + cells.replace(/<t[hd].*?>(.*?)<\/t[hd]>/gi, ' $1 |') + '\n')
  md = md.replace(/<pre><code>(.*?)<\/code><\/pre>/gi, (_, t) => '\n```\n' + t + '\n```\n')
  md = md.replace(/<br\s*\/?>/gi, '\n')
  md = md.replace(/\n{3,}/g, '\n\n')
  return md.trim()
}

function stripTags(html) {
  return html.replace(/<[^>]*>/g, '')
}

function preserveInline(html) {
  return html.replace(/<strong>(.*?)<\/strong>/gi, '**$1**')
    .replace(/<b>(.*?)<\/b>/gi, '**$1**')
    .replace(/<em>(.*?)<\/em>/gi, '*$1*')
    .replace(/<i>(.*?)<\/i>/gi, '*$1*')
    .replace(/<code>(.*?)<\/code>/gi, '`$1`')
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/<[^>]*>/g, '')
}
</script>

<style scoped>
.se-root { border: 1px solid transparent; border-radius: var(--radius-md); transition: border-color var(--dur-fast); }
.se-root.se-editing { border-color: var(--brand); }
.se-content { min-height: 20px; font-size: var(--text-sm); line-height: 1.8; color: var(--ink); }
.se-content :deep(.ProseMirror) { outline: none; min-height: 20px; }
.se-content :deep(.ProseMirror p) { margin: 0.2rem 0; }
.se-content :deep(.ProseMirror h1) { font-size: 1.3rem; font-weight: 700; margin: 0.3rem 0; }
.se-content :deep(.ProseMirror h2) { font-size: 1.15rem; font-weight: 600; margin: 0.25rem 0; border-bottom: 1px solid var(--border-light); }
.se-content :deep(.ProseMirror h3) { font-size: 1.05rem; font-weight: 600; margin: 0.2rem 0; }
.se-content :deep(.ProseMirror h4), .se-content :deep(.ProseMirror h5), .se-content :deep(.ProseMirror h6) { font-size: 0.95rem; font-weight: 600; margin: 0.15rem 0; }
.se-content :deep(.ProseMirror ul), .se-content :deep(.ProseMirror ol) { padding-left: 1.2rem; margin: 0.2rem 0; }
.se-content :deep(.ProseMirror li) { margin: 0.1rem 0; }
.se-content :deep(.ProseMirror code) { font-family: monospace; background: var(--surface); padding: 0 0.2rem; border-radius: 3px; font-size: 0.9em; }
.se-content :deep(.ProseMirror blockquote) { border-left: 2px solid var(--brand); padding-left: 0.5rem; margin: 0.2rem 0; color: var(--ink-soft); }
.se-content :deep(.ProseMirror table) { width: 100%; border-collapse: collapse; margin: 0.3rem 0; font-size: var(--text-xs); }
.se-content :deep(.ProseMirror th), .se-content :deep(.ProseMirror td) { border: 1px solid var(--border); padding: 0.2rem 0.4rem; text-align: left; }
.se-content :deep(.ProseMirror th) { background: var(--surface); font-weight: 600; }
.se-content :deep(.ProseMirror strong) { font-weight: 600; }
.se-content :deep(.ProseMirror em) { font-style: italic; }
.se-content :deep(.ProseMirror pre) { background: #f5f5f5; padding: 0.5rem; border-radius: 4px; overflow-x: auto; }
.se-content :deep(.ProseMirror pre code) { background: transparent; padding: 0; }
.se-content :deep(p.is-editor-empty:first-child::before) { color: var(--ink-soft); content: attr(data-placeholder); float: left; height: 0; pointer-events: none; }
</style>
