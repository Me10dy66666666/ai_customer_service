<template>
  <div class="kb-page">
    <div class="kb-layout">
      <aside v-if="isAdminOrKBAdmin" class="kb-cat-panel" :class="{ collapsed: catCollapsed }">
        <div class="kb-cat-header">
          <span class="kb-cat-title">分类标签</span>
          <button class="kb-cat-toggle" @click="catCollapsed = !catCollapsed">
            {{ catCollapsed ? '▶' : '▼' }}
          </button>
        </div>
        <div v-if="!catCollapsed" class="kb-cat-list">
          <div class="kb-cat-add-row">
            <input v-model="newCategoryName" placeholder="新分类名" class="kb-cat-inp" @keydown.enter="createCategory" />
            <button class="kb-cat-add-btn" @click="createCategory">添加</button>
          </div>
          <div class="kb-cat-item" :class="{ active: searchCategory === '' }" @click="filterByCategory('')">
            全部分类 ({{ totalAllDocs }})
          </div>
          <div v-for="cat in allCategories" :key="cat.name" class="kb-cat-item"
               :class="{ active: searchCategory === cat.name }"
               @click="filterByCategory(cat.name)">
            <span>{{ toDisplayCategory(cat.name) }} ({{ cat.count }})</span>
            <span class="kb-cat-del" @click.stop="deleteCategoryConfirm(cat.name)">&times;</span>
          </div>
        </div>
      </aside>

      <div class="kb-main" :class="{ 'kb-main-centered': isAdminOrKBAdmin && catCollapsed }">
        <div class="kb-search">
          <input v-model="searchKeyword" @keydown.enter="doSearch" placeholder="搜索知识库..." class="kb-search-inp" />
          <div class="kr-cat-dropdown">
            <button class="kr-cat-selected" @click.stop="searchCatOpen = !searchCatOpen">
              {{ searchCategory ? toDisplayCategory(searchCategory) : '全部分类' }} ▼
            </button>
            <div v-if="searchCatOpen" class="kr-cat-menu">
              <div class="kr-cat-menu-item" :class="{ active: searchCategory === '' }"
                   @click="searchCategory = ''; searchCatOpen = false; doSearch()">全部分类</div>
              <div v-for="cat in allCategories" :key="cat.name" class="kr-cat-menu-item"
                   :class="{ active: searchCategory === cat.name }"
                   @click="searchCategory = cat.name; searchCatOpen = false; doSearch()">
                {{ toDisplayCategory(cat.name) }}
              </div>
            </div>
          </div>
          <button class="kr-btn kr-btn-primary" @click="doSearch">搜索</button>
        </div>

        <div v-if="isAdminOrKBAdmin" class="kb-toolbar">
          <button class="kr-btn" :class="{ 'kr-btn-active': isEditMode }" @click="toggleEditMode">
            {{ isEditMode ? '退出编辑' : '编辑' }}
          </button>
          <button class="kr-btn" @click="onReindexEs">重建索引</button>
        </div>

        <div v-if="selectedDoc" class="kb-detail">
          <button class="kr-btn" @click="closeDetail">&larr; 返回列表</button>
          <h2 class="kb-doc-title">{{ selectedDoc.title }}</h2>
          <div v-if="selectedDoc.difySyncStatus === 'FAILED'" class="kb-sync-warn">
            ⚠ 此文档AI知识库同步失败，可能影响AI回答准确性
          </div>
          <div v-if="hasPdfPreview" class="kb-pdf-view"><iframe :src="pdfBlobUrl" class="kb-pdf-frame" /></div>
          <div v-else-if="isNativeImage" class="kb-image-view"><img :src="previewUrl" class="kb-preview-image" /></div>
          <div v-else class="kb-markdown-fallback">
            <div class="kb-doc-layout">
              <nav v-if="tocItems.length > 0" class="kb-toc">
                <div v-for="item in tocItems" :key="item.anchor" class="kb-toc-item" :class="'kb-toc-l' + item.level"
                     :style="{ paddingLeft: (item.level - 1) * 16 + 'px' }" :data-active="selectedToc === item.anchor"
                     @click="scrollToToc(item.anchor)">{{ item.title }}</div>
              </nav>
              <div class="kb-doc-body kb-markdown" ref="docBody" v-html="renderedContent"></div>
            </div>
          </div>
        </div>

        <div v-else class="kb-list">
          <p v-if="docList.length === 0" class="kr-empty">
            {{ searchKeyword ? '未找到匹配文档' : '暂无已发布文档' }}
          </p>
          <div v-for="doc in docList" :key="doc.id" class="kr-card kb-card"
               :class="{ 'kb-card-selected': selectedIds.has(doc.id) }" @click="onCardClick(doc)">
            <div class="kr-card-info">
              <span v-if="isEditMode" class="kb-checkbox" :class="{ checked: selectedIds.has(doc.id) }" @click.stop="toggleSelect(doc.id)">
                <span v-if="selectedIds.has(doc.id)">✓</span>
              </span>
              <strong>{{ doc.title }}</strong>
              <div v-if="isAdminOrKBAdmin" class="kb-tag-dropdown" @click.stop @mouseleave="tagMenuOpen = null">
                <span class="kr-tag kb-tag-clickable" @click="toggleTagMenu(doc.id)">
                  {{ toDisplayCategory(doc.category) || '未分类' }} ▼
                </span>
                <div v-if="tagMenuOpen === doc.id" class="kb-tag-menu" @click.stop>
                  <div class="kb-tag-menu-section">
                    <span class="kb-tag-menu-label">分类</span>
                    <div v-for="cat in categoryOptions" :key="cat" class="kb-tag-menu-item"
                         :class="{ active: doc.category === cat }" @click="changeCategory(doc, cat)">
                      {{ toDisplayCategory(cat) }}
                    </div>
                  </div>
                  <div class="kb-tag-menu-section">
                    <div v-for="tag in parseTags(doc.tags)" :key="tag" class="kb-tag-menu-tag">
                      {{ tag }} <span class="kb-tag-remove" @click="removeTag(doc, tag)">&times;</span>
                    </div>
                  </div>
                </div>
              </div>
              <span v-else class="kr-tag">{{ toDisplayCategory(doc.category) || '未分类' }}</span>
              <template v-if="doc.tags && !isAdminOrKBAdmin">
                <span v-for="tag in parseTags(doc.tags)" :key="tag" class="kr-tag">{{ tag }}</span>
              </template>
              <template v-if="doc.tags && isAdminOrKBAdmin && tagMenuOpen !== doc.id">
                <span v-for="tag in parseTags(doc.tags)" :key="tag" class="kr-tag">{{ tag }}</span>
              </template>
              <span class="kr-tag" v-if="doc.fileType">{{ doc.fileType }}</span>
              <span v-if="doc.difySyncStatus === 'FAILED'" class="kr-tag kr-tag-dify kr-tag-danger">SYNC_FAILED</span>
              <span class="kr-date">v{{ doc.version }}</span>
            </div>
            <span class="kr-card-actions" v-if="isEditMode" @click.stop>
              <button class="kr-btn" @click="openDiff(doc)">版本对比</button>
              <button class="kr-btn" @click="archiveDoc(doc.id)">归档</button>
              <button v-if="doc.difySyncStatus === 'FAILED'" class="kr-btn kr-btn-warn" @click="retrySync(doc.id)">重试同步</button>
              <button class="kr-btn kr-btn-danger" @click="deleteDocument(doc.id)">删除</button>
              <label class="kb-toggle-switch" @click.stop.prevent="toggleEnabled(doc)">
                <input type="checkbox" :checked="doc.enabled" tabindex="-1" />
                <span class="kb-toggle-track">
                  <span class="kb-toggle-thumb"></span>
                </span>
                <span class="kb-toggle-label-text">{{ doc.enabled ? '已启用' : '已禁用' }}</span>
              </label>
            </span>
            <span v-else class="kb-arrow">&rarr;</span>
          </div>
          <div class="kb-pager" v-if="totalDocs > pageSize">
            <button class="kr-btn" :disabled="currentPage <= 1" @click="changePage(currentPage - 1)">上一页</button>
            <span>{{ currentPage }} / {{ Math.ceil(totalDocs / pageSize) }}</span>
            <button class="kr-btn" :disabled="currentPage >= Math.ceil(totalDocs / pageSize)" @click="changePage(currentPage + 1)">下一页</button>
          </div>
        </div>

        <div v-if="isEditMode && selectedIds.size > 0" class="kb-batch-bar">
          <span class="kb-batch-info">已选择 {{ selectedIds.size }} 项</span>
          <button class="kr-btn" @click="batchArchiveDocs">批量归档</button>
          <button class="kr-btn kr-btn-danger" @click="batchDeleteDocs">批量删除</button>
          <button class="kr-btn" @click="clearSelection">取消选择</button>
        </div>
      </div>
    </div>
    <KnowledgeDiff v-if="showDiff" :documentId="diffDocId" :documentTitle="diffDocTitle" @close="showDiff = false" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import * as knowledgeService from '@/domains/knowledge/knowledgeService'
import { toDisplayCategory } from '@/domains/knowledge/categoryConstants'
import { useAuth } from '@/shared/composables/useAuth'
import { useCategoryStore } from '@/shared/stores/categoryStore'
import KnowledgeDiff from '@/domains/knowledge/KnowledgeDiff.vue'
import http from '@/core/axios'

const IMAGE_EXTENSIONS = ['png', 'jpg', 'jpeg', 'bmp', 'gif', 'webp', 'tiff']
const PDF_EXTENSIONS = ['pdf']

const { isAdmin, isKBAdmin } = useAuth()
const isAdminOrKBAdmin = computed(() => isAdmin.value || isKBAdmin.value)
const categoryStore = useCategoryStore()

const allCategories = ref([])
const categoryOptions = ref([])

const searchKeyword = ref('')
const searchCategory = ref('')
const docList = ref([])
const totalDocs = ref(0)
const currentPage = ref(1)
const pageSize = 20
const selectedDoc = ref(null)
const selectedToc = ref(null)
const pdfBlobUrl = ref('')
let currentPdfBlobUrl = null

const isEditMode = ref(false)
const selectedIds = ref(new Set())
const catCollapsed = ref(false)
const showDiff = ref(false)
const diffDocId = ref(null)
const diffDocTitle = ref('')
const tagMenuOpen = ref(null)
const searchCatOpen = ref(false)
const newCategoryName = ref('')

const renderer = new marked.Renderer()
let headingIndex = 0
renderer.heading = function({ text, depth }) {
  headingIndex++
  const id = 'section-' + headingIndex
  return `<h${depth} id="${id}">${text}</h${depth}>`
}
marked.setOptions({ renderer, headerIds: false })

watch(isEditMode, (val) => {
  if (!val) {
    selectedIds.value = new Set()
    tagMenuOpen.value = null
  }
})

watch(searchCategory, () => {
  if (isEditMode.value && selectedIds.value.size > 0) clearSelection()
})

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('mouseout', onDocumentMouseOut)
  doSearch()
})

onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('mouseout', onDocumentMouseOut)
  releasePdfBlob()
})

function onDocumentClick() {
  if (tagMenuOpen.value !== null) tagMenuOpen.value = null
  if (searchCatOpen.value) searchCatOpen.value = false
}

function onDocumentMouseOut(e) {
  if (tagMenuOpen.value === null) return
  const dropdown = e.target.closest('.kb-tag-dropdown')
  if (dropdown && !dropdown.contains(e.relatedTarget)) {
    tagMenuOpen.value = null
  }
}

async function doSearch() {
  try {
    const res = await knowledgeService.searchKnowledge(searchKeyword.value, currentPage.value, pageSize, searchCategory.value)
    if (res.data.code === 200) {
      docList.value = res.data.data.list || []
      totalDocs.value = res.data.data.total || 0
      await refreshCategoryCounts()
    }
  } catch {
    docList.value = []
    totalDocs.value = 0
    ElMessage.error('查询失败')
  }
}

async function refreshCategoryCounts() {
  try {
    const res = await knowledgeService.getCategoryStats()
    if (res.data.code === 200) {
      const stats = res.data.data || []
      allCategories.value = stats.map(s => ({ name: s.name || '未分类', count: s.count || 0 }))
      categoryOptions.value = allCategories.value.map(c => c.name)
    }
  } catch {}
}

function filterByCategory(cat) {
  searchCategory.value = cat
  currentPage.value = 1
  doSearch()
}

function changePage(page) {
  currentPage.value = page
  doSearch()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function viewDetail(documentId) {
  try {
    const res = await knowledgeService.getKnowledgeDetail(documentId)
    if (res.data.code === 200) {
      selectedDoc.value = res.data.data
      headingIndex = 0
      await loadPdfBlob(documentId)
    }
  } catch { ElMessage.error('加载文档详情失败') }
}

function closeDetail() {
  releasePdfBlob()
  selectedDoc.value = null
  selectedToc.value = null
}

function onCardClick(doc) {
  if (isEditMode.value) { toggleSelect(doc.id) } else { viewDetail(doc.id) }
}

function toggleSelect(docId) {
  const next = new Set(selectedIds.value)
  if (next.has(docId)) next.delete(docId); else next.add(docId)
  selectedIds.value = next
}

function clearSelection() { selectedIds.value = new Set() }
function toggleEditMode() { isEditMode.value = !isEditMode.value }

function releasePdfBlob() {
  if (currentPdfBlobUrl) { URL.revokeObjectURL(currentPdfBlobUrl); currentPdfBlobUrl = null; pdfBlobUrl.value = '' }
}

async function loadPdfBlob(documentId) {
  releasePdfBlob()
  if (!hasPdfPreview.value) return
  try {
    const url = knowledgeService.getPreviewFileUrl(documentId)
    const res = await http.get(url, { responseType: 'blob' })
    const contentType = res.headers['content-type'] || 'application/pdf'
    const blob = new Blob([res.data], { type: contentType })
    currentPdfBlobUrl = URL.createObjectURL(blob)
    pdfBlobUrl.value = currentPdfBlobUrl
  } catch {
    pdfBlobUrl.value = ''
  }
}

const previewUrl = computed(() => selectedDoc.value?.id ? knowledgeService.getPreviewFileUrl(selectedDoc.value.id) : '')
const fileTypeLower = computed(() => (selectedDoc.value?.fileType || '').toLowerCase())
const hasPdfPreview = computed(() => {
  if (!selectedDoc.value) return false
  if (selectedDoc.value.previewPdfPath) return true
  return PDF_EXTENSIONS.includes(fileTypeLower.value)
})
const isNativeImage = computed(() => IMAGE_EXTENSIONS.includes(fileTypeLower.value) && !hasPdfPreview.value)
const totalAllDocs = computed(() => allCategories.value.reduce((sum, c) => sum + (c.count || 0), 0))
const tocItems = computed(() => {
  if (!selectedDoc.value?.tocJson) return []
  try { return JSON.parse(selectedDoc.value.tocJson) } catch { return [] }
})
const renderedContent = computed(() => {
  if (!selectedDoc.value?.content) return ''
  return DOMPurify.sanitize(marked.parse(selectedDoc.value.content))
})


function scrollToToc(anchor) {
  selectedToc.value = anchor
  const el = document.getElementById(anchor)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function parseTags(tags) {
  if (!tags) return []
  if (Array.isArray(tags)) return tags
  return tags.split(',').map(t => t.trim()).filter(Boolean)
}

function toggleTagMenu(docId) {
  tagMenuOpen.value = tagMenuOpen.value === docId ? null : docId
}

async function changeCategory(doc, cat) {
  try {
    await categoryStore.addCategory(cat)
  } catch { /* 分类可能已存在，忽略 */ }
  const previousCategory = doc.category
  doc.category = cat
  tagMenuOpen.value = null
  try {
    await knowledgeService.httpPut(`/api/knowledge/${doc.id}/category`, { category: cat })
    await categoryStore.fetchCategoryNames()
    await doSearch()
    ElMessage.success('分类已更新')
  } catch {
    doc.category = previousCategory
    ElMessage.error('更新分类失败')
  }
}

function removeTag(doc, tag) {
  const tags = parseTags(doc.tags)
  const filtered = tags.filter(t => t !== tag)
  const tagStr = filtered.join(',')
  knowledgeService.httpPut(`/api/knowledge/${doc.id}/tags`, { tags: tagStr }).then(() => {
    doc.tags = tagStr
    ElMessage.success('标签已移除')
  }).catch(() => ElMessage.error('移除标签失败'))
}

async function createCategory() {
  const name = newCategoryName.value.trim()
  if (!name) return
  try {
    await categoryStore.addCategory(name)
    newCategoryName.value = ''
    await refreshCategoryCounts()
    ElMessage.success('分类已添加')
  } catch (err) {
    const msg = err?.response?.data?.message
    ElMessage.error(msg || '添加分类失败')
  }
}

async function deleteCategoryConfirm(catName) {
  try {
    await ElMessageBox.confirm(`删除分类"${catName}"后，该分类下的文档将变为未分类。`, '确认删除分类', {
      confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }
  try {
    await categoryStore.removeCategory(catName)
    ElMessage.success('分类已删除')
    doSearch()
  } catch { ElMessage.error('删除分类失败') }
}

async function toggleEnabled(doc) {
  const newEnabled = !doc.enabled
  try {
    await knowledgeService.toggleDocumentEnabled(doc.id, newEnabled)
    doc.enabled = newEnabled
    ElMessage.success(newEnabled ? '已启用' : '已禁用')
  } catch { ElMessage.error('操作失败') }
}

async function archiveDoc(documentId) {
  try {
    await ElMessageBox.confirm('归档后该文档将从列表中移除，但不会删除数据。', '确认归档文档', {
      confirmButtonText: '确认归档', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }
  try { await knowledgeService.archiveDocument(documentId, 'MANUAL'); ElMessage.success('已归档'); await doSearch() }
  catch { ElMessage.error('归档失败') }
}

async function deleteDocument(documentId) {
  try {
    await ElMessageBox.confirm('删除后将清除该文档的所有数据，且不可恢复。', '确认删除文档', {
      confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'error'
    })
  } catch { return }
  try { await knowledgeService.deleteKnowledgeDocument(documentId); ElMessage.success('已删除'); await doSearch() }
  catch { ElMessage.error('删除失败') }
}

async function batchArchiveDocs() {
  try {
    await ElMessageBox.confirm(`确认归档 ${selectedIds.value.size} 个文档？`, '批量归档', {
      confirmButtonText: '确认归档', cancelButtonText: '取消', type: 'warning'
    })
  } catch { return }
  try {
    const ids = Array.from(selectedIds.value)
    const res = await knowledgeService.batchArchive(ids)
    ElMessage.success(`已归档 ${res.data.data.successCount} 个文档`)
    clearSelection(); await doSearch()
  } catch { ElMessage.error('批量归档失败') }
}

async function batchDeleteDocs() {
  try {
    await ElMessageBox.confirm(`确认删除 ${selectedIds.value.size} 个文档？此操作不可恢复。`, '批量删除', {
      confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'error'
    })
  } catch { return }
  try {
    const ids = Array.from(selectedIds.value)
    const res = await knowledgeService.batchDeleteDocuments(ids)
    ElMessage.success(`已删除 ${res.data.data.successCount} 个文档`)
    clearSelection(); await doSearch()
  } catch { ElMessage.error('批量删除失败') }
}

async function retrySync(documentId) {
  try { await knowledgeService.retryDifySync(documentId); ElMessage.success('已重新同步'); await doSearch() }
  catch { ElMessage.error('重试失败') }
}

async function onReindexEs() {
  try {
    const res = await knowledgeService.reindexAllToEs()
    if (res.data.code === 200) {
      const d = res.data.data
      ElMessage.success(`已重建 ${d.indexedCount} 个文档的ES索引，更新 ${d.tocUpdatedCount} 个目录，缓存已清除`)
      await doSearch()
    }
  } catch { ElMessage.error('重建索引失败') }
}

function openDiff(doc) { diffDocId.value = doc.id; diffDocTitle.value = doc.title; showDiff.value = true }
</script>

<style scoped>
.kb-page { max-width: 100%; padding: var(--page-pad-y) var(--page-pad-x); }
.kb-layout { display: flex; gap: var(--s-5); align-items: flex-start; }

.kb-cat-panel {
  width: 208px; flex-shrink: 0;
  background: var(--surface); border-radius: var(--radius-lg); border: 1px solid var(--border-light);
  overflow: hidden; transition: width var(--dur-normal) var(--ease-soft);
}
.kb-cat-panel.collapsed { width: 48px; }
.kb-cat-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--s-4); font-weight: var(--weight-semibold);
  color: var(--ink); border-bottom: 1px solid var(--border-light);
}
.kb-cat-title { font-family: var(--font-heading); font-size: var(--text-md); }
.kb-cat-toggle { background: none; border: none; cursor: pointer; font-size: var(--text-xs); color: var(--ink-soft); padding: 0; }
.kb-cat-list { padding: var(--s-2); }
.kb-cat-add-row { display: flex; gap: var(--s-2); padding: var(--s-2) var(--s-1); margin-bottom: var(--s-2); }
.kb-cat-inp {
  flex: 1; min-width: 0; padding: var(--s-2) var(--s-3); border: 1.5px solid var(--border); border-radius: var(--radius-md);
  font-size: var(--text-xs); font-family: var(--font-body); background: var(--base);
  transition: border-color var(--dur-fast) var(--ease-soft);
}
.kb-cat-inp:focus { border-color: var(--brand); outline: none; }
.kb-cat-add-btn {
  padding: var(--s-2) var(--s-4); border: none; border-radius: var(--radius-md);
  background: var(--brand); color: #fff; cursor: pointer; font-size: var(--text-xs);
  font-family: var(--font-body); font-weight: var(--weight-medium);
  transition: background var(--dur-fast) var(--ease-soft);
  white-space: nowrap; flex-shrink: 0;
}
.kb-cat-add-btn:hover { background: var(--brand-deep); }
.kb-cat-item {
  padding: var(--s-2) var(--s-3); font-size: var(--text-sm); color: var(--ink-soft);
  cursor: pointer; border-radius: var(--radius-md); display: flex; align-items: center; justify-content: space-between;
}
.kb-cat-item:hover { background: var(--brand-pale); color: var(--ink); }
.kb-cat-item.active { background: var(--brand-soft); color: var(--brand); font-weight: var(--weight-medium); }
.kb-cat-del { color: var(--ink-soft); font-size: var(--text-lg); line-height: 1; margin-left: auto; }
.kb-cat-del:hover { color: #cf1322; }

.kb-main { flex: 1; min-width: 0; }
.kb-main-centered { max-width: 960px; margin: 0 auto; transition: max-width var(--dur-normal) var(--ease-soft); }
.kb-search { display: flex; gap: var(--s-2); margin-bottom: var(--s-4); }
.kb-search-inp { flex: 1; padding: var(--s-3) var(--s-4); border: 1px solid var(--border); border-radius: var(--radius-md); font-size: var(--text-sm); font-family: var(--font-body); }
.kb-search-sel { padding: var(--s-3) var(--s-4); border: 1px solid var(--border); border-radius: var(--radius-md); font-size: var(--text-sm); font-family: var(--font-body); background: #fff; min-width: 140px; }
.kb-toolbar { display: flex; gap: var(--s-2); margin-bottom: var(--s-4); }

.kb-detail { }
.kb-doc-title { font-family: var(--font-heading); font-size: var(--text-2xl); color: var(--ink); margin: var(--s-4) 0; }
.kb-sync-warn { background: #fff7e6; color: #d46b08; padding: var(--s-3); border-radius: var(--radius-md); font-size: var(--text-sm); margin-bottom: var(--s-4); }
.kb-pdf-view { height: calc(100vh - 220px); border-radius: var(--radius-md); overflow: hidden; }
.kb-pdf-frame { width: 100%; height: 100%; border: none; }
.kb-image-view { display: flex; justify-content: center; background: #f5f5f5; border-radius: var(--radius-md); padding: var(--s-6); min-height: 400px; }
.kb-preview-image { max-width: 100%; max-height: 80vh; object-fit: contain; }

.kb-doc-layout { display: flex; gap: var(--s-6); }
.kb-toc { width: 220px; flex-shrink: 0; position: sticky; top: 0; align-self: flex-start; max-height: calc(100vh - 200px); overflow-y: auto; padding: var(--s-3); background: var(--surface); border-radius: var(--radius-md); border: 1px solid var(--border-light); }
.kb-toc-item { padding: var(--s-1) 0; font-size: var(--text-sm); color: var(--ink-soft); cursor: pointer; line-height: 1.4; }
.kb-toc-item:hover, .kb-toc-item[data-active="true"] { color: var(--brand); font-weight: var(--weight-medium); }
.kb-toc-l1 { font-size: var(--text-sm); }
.kb-toc-l2 { font-size: var(--text-xs); }
.kb-toc-l3, .kb-toc-l4, .kb-toc-l5, .kb-toc-l6 { font-size: var(--text-2xs); color: var(--ink-soft); }
.kb-doc-body { flex: 1; min-width: 0; line-height: 1.8; color: var(--ink); }

.kb-markdown :deep(h1) { font-family: var(--font-heading); font-size: 2rem; font-weight: 700; margin: 1.5rem 0 .75rem; color: var(--ink); }
.kb-markdown :deep(h2) { font-family: var(--font-heading); font-size: 1.5rem; font-weight: 600; margin: 1.25rem 0 .5rem; border-bottom: 1px solid var(--border-light); padding-bottom: .25rem; color: var(--ink); }
.kb-markdown :deep(h3) { font-family: var(--font-heading); font-size: 1.2rem; font-weight: 600; margin: 1rem 0 .5rem; color: var(--ink); }
.kb-markdown :deep(h4) { font-family: var(--font-heading); font-size: 1.05rem; font-weight: 600; margin: .75rem 0 .5rem; color: var(--ink); }
.kb-markdown :deep(p) { line-height: 1.8; margin: .5rem 0; }
.kb-markdown :deep(ul), .kb-markdown :deep(ol) { padding-left: 1.5rem; margin: .5rem 0; }
.kb-markdown :deep(li) { margin: .25rem 0; }
.kb-markdown :deep(code) { font-family: var(--font-mono, 'JetBrains Mono', monospace); background: var(--surface); padding: .125rem .375rem; border-radius: 4px; font-size: .9em; }
.kb-markdown :deep(pre) { background: #1e1e2e; color: #cdd6f4; padding: 1rem; border-radius: 8px; overflow-x: auto; margin: .75rem 0; }
.kb-markdown :deep(pre code) { background: transparent; padding: 0; color: inherit; }
.kb-markdown :deep(blockquote) { border-left: 3px solid var(--brand); padding-left: 1rem; color: var(--ink-soft); margin: .75rem 0; }
.kb-markdown :deep(table) { width: 100%; border-collapse: collapse; margin: 1rem 0; }
.kb-markdown :deep(th), .kb-markdown :deep(td) { border: 1px solid var(--border); padding: .5rem .75rem; text-align: left; }
.kb-markdown :deep(th) { background: var(--surface); font-weight: 600; }
.kb-markdown :deep(a) { color: var(--brand); text-decoration: underline; }
.kb-markdown :deep(strong) { font-weight: 600; }
.kb-markdown :deep(em) { font-style: italic; }

.kb-list { }
.kb-card { cursor: pointer; transition: border-color var(--dur-fast), background var(--dur-fast); }
.kb-card:not(.kb-card-selected):hover { border-color: var(--brand); }
.kb-card-selected { border-color: var(--brand); background: var(--brand-soft); }
.kb-arrow { color: var(--ink-soft); font-size: var(--text-lg); }

.kr-card-actions { display: inline-flex; gap: var(--s-4); align-items: center; flex-shrink: 0; }

.kb-checkbox {
  width: 20px; height: 20px; border: 2px solid var(--border); border-radius: 4px;
  display: flex; align-items: center; justify-content: center; font-size: var(--text-xs);
  color: #fff; background: var(--surface); flex-shrink: 0; transition: all var(--dur-fast);
}
.kb-checkbox.checked { background: var(--brand); border-color: var(--brand); }

.kb-tag-dropdown { position: relative; display: inline-block; }
.kb-tag-clickable { cursor: pointer; }
.kb-tag-clickable:hover { background: var(--brand-soft); color: var(--brand); }
.kb-tag-menu {
  position: absolute; top: 100%; left: 0; z-index: 50;
  background: var(--base); border: 1px solid var(--border); border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md); padding: var(--s-3); min-width: 220px; max-height: 320px; overflow-y: auto;
}
.kb-tag-menu-section { margin-bottom: var(--s-3); }
.kb-tag-menu-section:last-child { margin-bottom: 0; }
.kb-tag-menu-label { font-size: var(--text-xs); color: var(--ink-soft); font-weight: var(--weight-semibold); margin-bottom: var(--s-1); display: block; }
.kb-tag-menu-item { padding: var(--s-1) var(--s-2); font-size: var(--text-sm); cursor: pointer; border-radius: var(--radius-md); }
.kb-tag-menu-item:hover { background: var(--brand-pale); }
.kb-tag-menu-item.active { color: var(--brand); font-weight: var(--weight-medium); }
.kb-tag-menu-tag { display: inline-flex; align-items: center; gap: 4px; padding: 2px 8px; background: var(--base-alt); border-radius: var(--radius-full); font-size: var(--text-xs); margin: 2px; }
.kb-tag-remove { color: var(--ink-soft); cursor: pointer; font-size: var(--text-sm); }
.kb-tag-remove:hover { color: #cf1322; }

.kb-batch-bar {
  position: sticky; bottom: 0; z-index: 20;
  display: flex; align-items: center; gap: var(--s-3); padding: var(--s-4) var(--s-5);
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg);
  box-shadow: 0 -2px 12px rgba(0,0,0,0.06); margin-top: var(--s-5);
}
.kb-batch-info { font-size: var(--text-sm); font-weight: var(--weight-medium); color: var(--ink); }
.kb-pager { display: flex; align-items: center; justify-content: center; gap: var(--s-4); margin-top: var(--s-5); font-size: var(--text-sm); color: var(--ink-soft); }

.kr-btn {
  padding: var(--s-2) var(--s-5); border: none; border-radius: var(--radius-md);
  background: var(--base-alt); cursor: pointer; font-size: var(--text-sm); font-family: var(--font-body);
  font-weight: var(--weight-medium); color: var(--ink-soft);
  transition: all var(--dur-fast) var(--ease-soft);
}
.kr-btn:hover { background: var(--border); color: var(--ink); }
.kr-btn-primary { background: var(--brand); color: #fff; }
.kr-btn-primary:hover { background: var(--brand-deep); }
.kr-btn-danger { background: var(--danger-soft); color: var(--danger); }
.kr-btn-danger:hover { background: #ffd8d2; }
.kr-btn-warn { background: var(--warning-soft); color: var(--warning); }
.kr-btn-warn:hover { background: #ffe7ba; }
.kr-btn-active { background: var(--brand); color: #fff; }
.kr-btn-active:hover { background: var(--brand-deep); }
.kr-btn-sm { padding: var(--s-1) var(--s-3); font-size: var(--text-xs); }
.kr-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.kr-btn:disabled:hover { background: var(--base-alt); }
.kr-btn-primary:disabled:hover { background: var(--brand); }
.kr-empty { color: var(--ink-soft); text-align: center; padding: var(--s-10); }
.kr-card {
  display: flex; align-items: center; justify-content: space-between; padding: var(--s-4); margin-bottom: var(--s-2);
  background: var(--surface); border-radius: var(--radius-md); border: 1px solid var(--border-light);
}
.kr-card-info { display: flex; align-items: center; gap: var(--s-3); flex-wrap: wrap; }
.kr-tag {
  font-size: var(--text-3xs); padding: 2px 8px; border-radius: var(--radius-full);
  background: var(--base-alt); color: var(--ink-soft); white-space: nowrap;
}
.kr-tag-dify { background: var(--base-alt); }
.kr-tag-danger { background: #fff1f0; color: #cf1322; }
.kr-date { font-size: var(--text-xs); color: var(--ink-soft); white-space: nowrap; }

.kb-toggle-switch { display: inline-flex; align-items: center; gap: var(--s-2); cursor: pointer; user-select: none; }
.kb-toggle-switch input { position: absolute; opacity: 0; width: 0; height: 0; }
.kb-toggle-track {
  position: relative; width: 40px; height: 22px; background: #bfbfbf; border-radius: 11px;
  transition: background var(--dur-fast); flex-shrink: 0;
}
.kb-toggle-switch input:checked + .kb-toggle-track { background: #52c41a; }
.kb-toggle-thumb {
  position: absolute; top: 3px; left: 3px; width: 16px; height: 16px;
  background: #fff; border-radius: 50%; transition: left var(--dur-fast);
  box-shadow: 0 1px 3px rgba(0,0,0,0.15);
}
.kb-toggle-switch input:checked + .kb-toggle-track .kb-toggle-thumb { left: 21px; }
.kb-toggle-label-text { font-size: var(--text-xs); color: var(--ink-soft); white-space: nowrap; }
.kb-toggle-switch input:checked ~ .kb-toggle-label-text { color: #389e0d; }

/* ── 分类下拉菜单 ── */
.kr-cat-dropdown { position: relative; display: inline-block; }
.kr-cat-selected {
  padding: var(--s-2) var(--s-4); border: 1.5px solid var(--border); border-radius: var(--radius-md);
  font-size: var(--text-sm); font-family: var(--font-body); background: var(--surface); cursor: pointer; color: var(--ink);
  transition: border-color var(--dur-fast) var(--ease-soft);
}
.kr-cat-selected:hover { border-color: var(--brand); }
.kr-cat-menu {
  position: absolute; top: 100%; left: 0; z-index: 50;
  background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg);
  box-shadow: 0 4px 16px oklch(0.25 0.01 250 / 0.10); padding: var(--s-2); min-width: 200px; max-height: 280px; overflow-y: auto;
}
.kr-cat-menu-item {
  padding: var(--s-2) var(--s-3); font-size: var(--text-sm); cursor: pointer; border-radius: var(--radius-md);
  display: flex; align-items: center; justify-content: space-between;
}
.kr-cat-menu-item:hover { background: var(--brand-pale); }
.kr-cat-menu-item.active { color: var(--brand); font-weight: var(--weight-medium); }
</style>
