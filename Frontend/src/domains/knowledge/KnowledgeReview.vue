<template>
  <div class="kr-page">
    <div class="kr-header">
      <h2 class="kr-title">知识审核</h2>
      <div class="kr-tabs">
        <button :class="{ active: activeTab === 'pending' }" @click="activeTab = 'pending'">待审核</button>
        <button :class="{ active: activeTab === 'upload' }" @click="activeTab = 'upload'">上传新文档</button>
      </div>
    </div>

    <div v-if="activeTab === 'upload'" class="kr-upload">
      <div class="upload-zone" @dragover.prevent @drop.prevent="handleDrop">
        <input ref="fileInput" type="file" @change="handleFileSelect" accept=".jpg,.jpeg,.png,.bmp,.pdf,.docx,.xlsx,.txt,.md" hidden />
        <p>拖拽文件到此处 或 <button class="kr-btn" @click="$refs.fileInput.click()">选择文件</button></p>
        <p v-if="selectedFile" class="kr-file-name">{{ selectedFile.name }} ({{ formatSize(selectedFile.size) }})</p>
        <div class="kr-cat">
          <span>分类：</span>
          <div class="kr-cat-dropdown">
            <button class="kr-cat-selected" @click.stop="uploadCatOpen = !uploadCatOpen">
              {{ toDisplayCategory(uploadCategory) || uploadCategory }} ▼
            </button>
            <div v-if="uploadCatOpen" class="kr-cat-menu">
              <div v-for="cat in uploadCategories" :key="cat" class="kr-cat-menu-item"
                   :class="{ active: uploadCategory === cat }"
                   @click="uploadCategory = cat; uploadCatOpen = false">
                {{ toDisplayCategory(cat) }}
                <span class="kr-cat-menu-del" @click.stop="deleteUploadCategory(cat)">&times;</span>
              </div>
            </div>
          </div>
          <div class="kr-cat-add-row">
            <input v-model="newUploadCategoryName" placeholder="新分类名" class="kr-cat-inp" @keydown.enter="createUploadCategory" />
            <button class="kr-btn kr-btn-sm" @click="createUploadCategory">添加</button>
          </div>
        </div>
        <button class="kr-btn kr-btn-primary" :disabled="!selectedFile || uploading" @click="doUpload">
          {{ uploading ? `上传中 ${uploadProgress}%` : '上传并OCR识别' }}
        </button>
        <progress v-if="uploading" :value="uploadProgress" max="100" class="kr-progress" />
      </div>
    </div>

    <div v-if="activeTab === 'pending'" class="kr-list">
      <div class="ck-search" style="margin-bottom: var(--s-4);">
        <input v-model="pendingKeyword" @keydown.enter="doPendingSearch" placeholder="搜索待审核文档..." class="ck-search-inp" />
        <div class="kr-cat-dropdown">
          <button class="kr-cat-selected" @click.stop="pendingCatOpen = !pendingCatOpen">
            {{ pendingCategory ? toDisplayCategory(pendingCategory) : '全部分类' }} ▼
          </button>
          <div v-if="pendingCatOpen" class="kr-cat-menu">
            <div class="kr-cat-menu-item" :class="{ active: pendingCategory === '' }" @click="pendingCategory = ''; pendingCatOpen = false; doPendingSearch()">
              全部分类
            </div>
            <div v-for="cat in pendingCategories" :key="cat" class="kr-cat-menu-item" :class="{ active: pendingCategory === cat }"
                 @click="pendingCategory = cat; pendingCatOpen = false; doPendingSearch()">
              {{ toDisplayCategory(cat) }}
            </div>
          </div>
        </div>
        <button class="kr-btn kr-btn-primary" @click="doPendingSearch">搜索</button>
      </div>
      <p v-if="pendingList.length === 0" class="kr-empty">暂无待审核文档</p>
      <div v-for="doc in pendingList" :key="doc.id" class="kr-card">
        <div class="kr-card-info">
          <strong>{{ doc.title }}</strong>
          <span class="kr-tag">{{ toDisplayCategory(doc.category) || '未分类' }}</span>
          <span class="kr-tag">{{ doc.fileType }}</span>
          <span class="kr-tag kr-tag-status">待审核</span>
          <span class="kr-date">{{ doc.createdAt }}</span>
        </div>
        <button class="kr-btn kr-btn-primary" @click="enterReview(doc.id)">进入审核</button>
      </div>
    </div>

    <div v-if="reviewing" class="kr-review-overlay" @keydown="handleShortcut" tabindex="0" ref="overlayEl">
      <div class="kr-review">
        <div class="kr-review-bar">
          <h3>审核：{{ reviewDoc?.title }}</h3>
          <div class="kr-bar-group">
            <button class="kr-btn" @click="closeReview">关闭</button>
            <button class="kr-btn" @click="saveDraft">暂存</button>
          </div>
          <div class="kr-bar-sep"></div>
          <div class="kr-bar-group">
            <button class="kr-btn" @click="confirmAllPending" :disabled="pendingCount === 0">一键确认待定</button>
            <button class="kr-btn" @click="navigateUncertain" v-if="uncertainCount > 0">跳转存疑</button>
          </div>
          <div class="kr-bar-sep"></div>
          <div class="kr-bar-group">
            <button class="kr-btn kr-btn-danger" @click="doReject">退回</button>
            <button class="kr-btn kr-btn-primary" @click="openSubmitDialog">提交审核</button>
          </div>
        </div>
        <div class="kr-review-body">
          <div class="kr-split">
            <div class="kr-preview-pane" ref="previewPane">
              <!-- PDF/Office 多页预览：浏览器内置 PDF 查看器 -->
              <div v-if="previewType === 'pdf'" class="kr-pdf-container">
                <iframe :src="fileBlobUrl" class="kr-pdf-frame" />
              </div>
              <!-- 图片预览：img + Canvas OCR 框 -->
              <div v-else-if="previewType === 'image'" class="kr-image-container" ref="imageContainer">
                <img :src="fileBlobUrl" ref="previewImage" @load="onImageLoad" class="kr-preview-image" />
                <canvas ref="overlayCanvas" class="kr-overlay-canvas" @click="onCanvasClick" />
              </div>
              <!-- 回退：纯文本 -->
              <div v-else class="kr-text-preview">
                <div class="kr-text-content">{{ ocrFullText }}</div>
              </div>
            </div>
            <div class="kr-seg-pane">
              <div class="kr-seg-list" ref="segList">
                <div v-for="seg in reviewSegments" :key="seg.id"
                  class="kr-seg" :class="segClass(seg)"
                  :ref="el => setSegRef(seg.id, el)"
                  @click="focusSeg(seg)"
                  @mouseenter="highlightArea(seg)"
                  @mouseleave="clearHighlight">
                  <div class="kr-seg-head">
                    <span class="kr-seg-idx">段{{ seg.segmentIndex }}</span>
                    <span class="kr-seg-conf" :class="seg.confidence >= 0.85 ? 'kr-conf-high' : 'kr-conf-low'">
                      置信度 {{ (seg.confidence * 100).toFixed(0) }}%
                    </span>
                    <span class="kr-seg-status">{{ statusLabel(seg.status) }}</span>
                  </div>
                  <div class="kr-seg-body">
                    <SegmentEditor
                      :content="seg.reviewedText || seg.ocrText || ''"
                      :editable="editableStates.includes(seg.status)"
                      :active="seg._active && editableStates.includes(seg.status)"
                      @update:content="seg.reviewedText = $event"
                      @focus="onSegEditorFocus(seg)"
                    />
                    <div v-if="seg.status === 'CONFIRMED'" class="kr-seg-reviewed">
                      <span class="kr-reviewed-label kr-confirmed-label">已确认</span>
                      <button class="kr-btn kr-btn-sm kr-btn-undo" @click="undoSegment(seg)">撤销确认</button>
                    </div>
                    <div v-if="editableStates.includes(seg.status)" class="kr-seg-actions">
                      <button class="kr-btn kr-btn-sm" @click="confirmSegment(seg)">确认</button>
                      <button class="kr-btn kr-btn-sm" @click="skipSegment(seg)">跳过</button>
                    </div>
                    <div v-if="seg.status === 'REVIEWED'" class="kr-seg-reviewed">
                      <span class="kr-reviewed-label">已修正为：</span>{{ seg.reviewedText }}
                      <button class="kr-btn kr-btn-sm kr-btn-undo" @click="undoSegment(seg)">撤销</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="kr-review-footer">
          <div class="kr-footer-stats">
            已确认 {{ confirmedCount }} | 已修正 {{ reviewedCount }} | ⚠ 存疑待处理 {{ uncertainCount }} | 已跳过 {{ skippedCount }} | 待定 {{ pendingCount }}
          </div>
          <div class="kr-footer-actions">
            <button class="kr-btn" @click="saveDraft">暂存 (Ctrl+S)</button>
            <button class="kr-btn kr-btn-primary" @click="openSubmitDialog">提交审核 (Ctrl+Shift+Enter)</button>
          </div>
          <div class="kr-footer-shortcuts">
            快捷键：J/↓ 下一段 | K/↑ 上一段 | Enter 确认 | Ctrl+Enter 提交修正 | Shift+Enter 跳过 | U 撤销 | N 跳存疑
          </div>
        </div>
      </div>

      <div v-if="submitDialogVisible" class="kr-dialog-overlay" @click.self="submitDialogVisible = false">
        <div class="kr-dialog">
          <h3>提交审核确认</h3>
          <div class="kr-dialog-stats">
            <div class="kr-dialog-stat kr-dstat-confirmed">已确认：{{ confirmedCount }} 段</div>
            <div class="kr-dialog-stat kr-dstat-reviewed">已修正：{{ reviewedCount }} 段</div>
            <div class="kr-dialog-stat kr-dstat-uncertain">⚠ 存疑未处理：<strong>{{ uncertainCount }}</strong> 段</div>
            <div class="kr-dialog-stat kr-dstat-skipped">已跳过：{{ skippedCount }} 段</div>
          </div>
          <p v-if="uncertainCount > 0" class="kr-dialog-warn">
            还有 <strong>{{ uncertainCount }}</strong> 段存疑未处理，提交后将使用 OCR 原文。
          </p>
          <p class="kr-dialog-hint">提交后将写入本地知识库并同步上传至 AI 知识库。</p>
          <div class="kr-dialog-actions">
            <button class="kr-btn" @click="submitDialogVisible = false">返回继续处理</button>
            <button class="kr-btn kr-btn-primary" @click="doSubmitReview">继续提交</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SegmentEditor from '@/domains/knowledge/SegmentEditor.vue'
import http from '@/core/axios'
import * as knowledgeService from '@/domains/knowledge/knowledgeService'
import { toDisplayCategory } from '@/domains/knowledge/categoryConstants'
import { useCategoryStore } from '@/shared/stores/categoryStore'
import { useAuthStore } from '@/shared/stores/authStore'

const activeTab = ref('pending')
const selectedFile = ref(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadCategory = ref('')
const uploadCatOpen = ref(false)
const newUploadCategoryName = ref('')
const uploadCategories = ref([])
const categoryStore = useCategoryStore()
const pendingList = ref([])
const reviewing = ref(false)
const reviewDoc = ref(null)
const reviewSegments = ref([])
const segRefs = {}
const submitDialogVisible = ref(false)

const pendingKeyword = ref('')
const pendingCategory = ref('')
const pendingCategories = ref([])
const pendingCatOpen = ref(false)

const editableStates = ['PENDING', 'UNCERTAIN']

const previewImage = ref(null)
const overlayCanvas = ref(null)
const imageContainer = ref(null)
const segList = ref(null)
const overlayEl = ref(null)

let autoSaveTimer = null
let highlightedSegment = null

const confirmedCount = computed(() => reviewSegments.value.filter(s => s.status === 'CONFIRMED').length)
const reviewedCount = computed(() => reviewSegments.value.filter(s => s.status === 'REVIEWED').length)
const uncertainCount = computed(() => reviewSegments.value.filter(s => s.status === 'UNCERTAIN').length)
const skippedCount = computed(() => reviewSegments.value.filter(s => s.status === 'SKIPPED').length)
const pendingCount = computed(() => reviewSegments.value.filter(s => s.status === 'PENDING').length)

function onSegEditorFocus(seg) {
  reviewSegments.value.forEach(s => s._active = false)
  seg._active = true
  drawOverlay(seg)
}

const fileBlobUrl = ref('')
const previewType = ref('none') // 'pdf' | 'image' | 'none'

async function loadPreviewBlob() {
  const oldUrl = fileBlobUrl.value
  if (oldUrl) {
    URL.revokeObjectURL(oldUrl)
    fileBlobUrl.value = ''
  }
  previewType.value = 'none'
  if (!reviewDoc.value?.id) return
  try {
    const res = await http.get(`/api/knowledge/file/${reviewDoc.value.id}`, {
      responseType: 'blob'
    })
    const contentType = res.headers['content-type'] || ''
    const blob = new Blob([res.data], { type: contentType })

    fileBlobUrl.value = URL.createObjectURL(blob)
    if (contentType.startsWith('image/')) {
      previewType.value = 'image'
    } else if (contentType.includes('pdf')) {
      previewType.value = 'pdf'
    }
    await nextTick()
    const img = previewImage.value
    if (img) {
      img.onload = () => onImageLoad()
    }
  } catch (err) {
    console.error('[KnowledgeReview] loadPreviewBlob failed', err);
    ElMessage.error('加载预览文件失败')
  }
}

const isImageFile = computed(() => {
  const ft = (reviewDoc.value?.fileType || '').toLowerCase()
  return ['jpg', 'jpeg', 'png', 'bmp', 'gif', 'webp', 'tiff'].includes(ft)
})

watch(reviewing, (isReviewing) => {
  if (isReviewing) {
  }
})

const ocrFullText = computed(() => {
  return reviewSegments.value.map(s => s.ocrText).join('\n\n')
})

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  loadPending()
  loadPendingCategories()
})

onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
  clearAutoSave()
})

function onDocumentClick() {
  if (uploadCatOpen.value) uploadCatOpen.value = false
  if (pendingCatOpen.value) pendingCatOpen.value = false
}

async function loadPending() {
  try {
    const res = await knowledgeService.getPendingReview(pendingKeyword.value, pendingCategory.value)
    if (res.data.code === 200) pendingList.value = res.data.data || []
  } catch { ElMessage.error('加载待审核列表失败') }
}

async function loadPendingCategories() {
  await categoryStore.fetchCategoryNames()
  uploadCategories.value = [...categoryStore.categoryNames]
  pendingCategories.value = [...categoryStore.categoryNames]
  if (!uploadCategory.value && uploadCategories.value.length > 0) {
    uploadCategory.value = uploadCategories.value[0]
  }
}

function doPendingSearch() {
  loadPending()
}

async function createUploadCategory() {
  const name = newUploadCategoryName.value.trim()
  if (!name) return
  try {
    await categoryStore.addCategory(name)
    uploadCategories.value = [...categoryStore.categoryNames]
    pendingCategories.value = [...categoryStore.categoryNames]
    uploadCategory.value = name
    newUploadCategoryName.value = ''
    uploadCatOpen.value = false
    ElMessage.success('分类已添加')
  } catch (err) {
    const msg = err?.response?.data?.message
    ElMessage.error(msg || '添加分类失败')
  }
}

async function deleteUploadCategory(cat) {
  if (uploadCategories.value.length <= 1) { ElMessage.warning('至少保留一个分类'); return }
  try {
    await categoryStore.removeCategory(cat)
    uploadCategories.value = uploadCategories.value.filter(c => c !== cat)
    pendingCategories.value = pendingCategories.value.filter(c => c !== cat)
    if (uploadCategory.value === cat) uploadCategory.value = uploadCategories.value[0] || ''
    ElMessage.success('分类已删除')
  } catch { ElMessage.error('删除分类失败') }
}

function handleFileSelect(e) {
  const f = e.target.files?.[0]
  if (f) selectedFile.value = f
}

function handleDrop(e) {
  const f = e.dataTransfer.files?.[0]
  if (f) selectedFile.value = f
}

function formatSize(bytes) {
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function doUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  uploadProgress.value = 0
  const progressSim = setInterval(() => {
    if (uploadProgress.value < 90) uploadProgress.value += 5
  }, 200)
  try {
    const fd = new FormData()
    fd.append('file', selectedFile.value)
    fd.append('category', uploadCategory.value)
    const res = await knowledgeService.uploadAndOcr(fd)
    clearInterval(progressSim)
    uploadProgress.value = 100
    if (res.data.code === 200) {
      ElMessage.success('上传成功，正在跳转到审核页面')
      selectedFile.value = null
      const result = res.data.data
      await loadPending()
      if (result.documentId) {
        await enterReview(result.documentId)
      } else {
        activeTab.value = 'pending'
      }
    }
  } catch {
    clearInterval(progressSim)
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
    uploadProgress.value = 0
  }
}

async function enterReview(documentId) {
  try {
    const res = await knowledgeService.getReviewData(documentId)
    if (res.data.code === 200) {
      reviewDoc.value = res.data.data.document

      reviewSegments.value = (res.data.data.segments || []).map(seg => ({
        ...seg,
        reviewedText: seg.reviewedText || '',
        _active: false
      }))
      await loadPreviewBlob()
      reviewing.value = true
      await nextTick()
      restoreDraft()
      startAutoSave()
      await nextTick(() => {
        overlayEl.value?.focus()
        drawOverlay()
      })
    }
  } catch { ElMessage.error('加载审核数据失败') }
}

function setSegRef(id, el) {
  if (el) segRefs[id] = el
}

function segClass(seg) {
  return {
    'kr-seg-confirmed': seg.status === 'CONFIRMED',
    'kr-seg-reviewed': seg.status === 'REVIEWED',
    'kr-seg-uncertain': seg.status === 'UNCERTAIN',
    'kr-seg-skipped': seg.status === 'SKIPPED',
    'kr-seg-pending': seg.status === 'PENDING',
    'kr-seg-active': seg._active
  }
}

function statusLabel(status) {
  return { CONFIRMED: '已确认', REVIEWED: '已修正', UNCERTAIN: '存疑', SKIPPED: '已跳过', PENDING: '待定' }[status] || status
}

function focusSeg(seg) {
  reviewSegments.value.forEach(s => s._active = false)
  seg._active = true
  drawOverlay(seg)
  const el = segRefs[seg.id]
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function confirmSegment(seg) {
  seg.status = 'REVIEWED'
  seg._active = false
  drawOverlay()
  navigateNext()
}

function skipSegment(seg) {
  seg.status = 'SKIPPED'
  seg._active = false
  drawOverlay()
  navigateNext()
}

function undoSegment(seg) {
  if (seg.status === 'REVIEWED' || seg.status === 'CONFIRMED') {
    seg.status = 'UNCERTAIN'
    seg.reviewedText = ''
    drawOverlay(seg)
  }
}

function confirmAllPending() {
  const countBefore = pendingCount.value
  reviewSegments.value.forEach(s => {
    if (s.status === 'PENDING') s.status = 'CONFIRMED'
  })
  drawOverlay()
  ElMessage.success(`已一键确认 ${countBefore} 段待定内容`)
}

async function doReject() {
  try {
    await ElMessageBox.confirm(
      '退回后文档将归档，可在数据面板的审核记录中查看。确认退回？',
      '确认退回文档',
      { confirmButtonText: '确认退回', cancelButtonText: '取消', type: 'warning' }
    )
  } catch { return }
  try {
    const authStore = useAuthStore()
    await knowledgeService.rejectDocument(reviewDoc.value.id, authStore.username || 'KB_ADMIN')
    ElMessage.success('已退回，文档已归档')
    clearAutoSave()
    localStorage.removeItem('review-draft-' + reviewDoc.value.id)
    reviewing.value = false
    reviewDoc.value = null
    reviewSegments.value = []
    loadPending()
  } catch { ElMessage.error('退回失败') }
}

function navigateNext() {
  const currentIdx = reviewSegments.value.findIndex(s => s._active)
  const nextIdx = Math.min((currentIdx === -1 ? 0 : currentIdx) + 1, reviewSegments.value.length - 1)
  if (nextIdx >= 0 && nextIdx < reviewSegments.value.length) {
    focusSeg(reviewSegments.value[nextIdx])
  }
}

function navigatePrev() {
  const currentIdx = reviewSegments.value.findIndex(s => s._active)
  const prevIdx = Math.max((currentIdx === -1 ? 0 : currentIdx) - 1, 0)
  if (prevIdx >= 0 && prevIdx < reviewSegments.value.length) {
    focusSeg(reviewSegments.value[prevIdx])
  }
}

function navigateUncertain() {
  const uncertain = reviewSegments.value.find(s => s.status === 'UNCERTAIN')
  if (uncertain) focusSeg(uncertain)
}

function closeReview() {
  clearAutoSave()
  if (fileBlobUrl.value) {
    URL.revokeObjectURL(fileBlobUrl.value)
    fileBlobUrl.value = ''
  }
  reviewing.value = false
  reviewDoc.value = null
  reviewSegments.value = []
}

function saveDraft() {
  if (!reviewDoc.value?.id) return
  const draft = {
    segments: reviewSegments.value.map(s => ({
      id: s.id, status: s.status, reviewedText: s.reviewedText
    })),
    timestamp: Date.now()
  }
  localStorage.setItem('review-draft-' + reviewDoc.value.id, JSON.stringify(draft))
  ElMessage.success('已暂存草稿')
}

function restoreDraft() {
  if (!reviewDoc.value?.id) return
  const raw = localStorage.getItem('review-draft-' + reviewDoc.value.id)
  if (!raw) return
  try {
    const draft = JSON.parse(raw)
    if (draft.segments) {
      draft.segments.forEach(ds => {
        const seg = reviewSegments.value.find(s => s.id === ds.id)
        if (seg) {
          seg.status = ds.status
          seg.reviewedText = ds.reviewedText || ''
        }
      })
      ElMessage.info('已恢复上次审核进度')
    }
  } catch { /* ignore */ }
}

function startAutoSave() {
  clearAutoSave()
  autoSaveTimer = setInterval(() => {
    saveDraft()
  }, 30000)
}

function clearAutoSave() {
  if (autoSaveTimer) {
    clearInterval(autoSaveTimer)
    autoSaveTimer = null
  }
}

function openSubmitDialog() {
  submitDialogVisible.value = true
}

async function doSubmitReview() {
  try {
    const segments = reviewSegments.value.map(s => ({
      id: s.id, status: s.status, reviewedText: s.reviewedText || s.ocrText, ocrText: s.ocrText
    }))
    const authStore = useAuthStore()
    const res = await knowledgeService.submitReview(reviewDoc.value.id, segments, authStore.username || 'KB_ADMIN')
    if (res.data.code === 200) {
      ElMessage.success('审核通过，已发布')
      clearAutoSave()
      localStorage.removeItem('review-draft-' + reviewDoc.value.id)
      submitDialogVisible.value = false
      reviewing.value = false
      loadPending()
    }
  } catch { ElMessage.error('提交失败') }
}

function onImageLoad() {
  drawOverlay()
}

function drawOverlay(activeSeg) {
  const canvas = overlayCanvas.value
  const img = previewImage.value
  const container = imageContainer.value
  if (!canvas || !img || !container) return
  const rect = img.getBoundingClientRect()
  const containerRect = container.getBoundingClientRect()
  canvas.width = rect.width
  canvas.height = rect.height
  canvas.style.width = rect.width + 'px'
  canvas.style.height = rect.height + 'px'
  canvas.style.left = (rect.left - containerRect.left) + 'px'
  canvas.style.top = (rect.top - containerRect.top) + 'px'
  const ctx = canvas.getContext('2d')
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  const imgNaturalW = img.naturalWidth
  const imgNaturalH = img.naturalHeight
  const scaleX = rect.width / imgNaturalW
  const scaleY = rect.height / imgNaturalH
  reviewSegments.value.forEach(seg => {
    let box
    try { box = JSON.parse(seg.boundingBox || '{}') } catch { box = {} }
    if (!box.w || !box.h) return
    if (seg.status === 'CONFIRMED' || seg.status === 'REVIEWED') return
    const x = box.x * scaleX
    const y = box.y * scaleY
    const w = box.w * scaleX
    const h = box.h * scaleY
    const isActive = activeSeg && activeSeg.id === seg.id
    ctx.save()
    if (seg.status === 'PENDING') {
      const conf = seg.confidence || 0.5
      const r = conf < 0.5 ? 220 : conf < 0.7 ? 240 : conf < 0.85 ? 160 : 100
      const g = conf < 0.5 ? 50 : conf < 0.7 ? 140 : conf < 0.85 ? 200 : 220
      const b = conf < 0.5 ? 50 : conf < 0.7 ? 40 : conf < 0.85 ? 80 : 100
      const alpha = isActive ? 0.45 : 0.22
      ctx.fillStyle = `rgba(${r},${g},${b},${alpha})`
      ctx.fillRect(x, y, w, h)
      ctx.strokeStyle = isActive ? `rgba(${r},${g},${b},0.9)` : `rgba(${r},${g},${b},0.5)`
      ctx.lineWidth = isActive ? 3 : 1.5
      ctx.setLineDash([4, 3])
      ctx.strokeRect(x, y, w, h)
      ctx.setLineDash([])
      if (isActive) {
        ctx.fillStyle = '#333'
        ctx.font = 'bold 10px sans-serif'
        ctx.fillText('置信度 ' + (conf * 100).toFixed(0) + '%', x, y - 4)
      }
    } else if (seg.status === 'UNCERTAIN') {
      ctx.fillStyle = 'rgba(255, 193, 7, 0.25)'
      ctx.fillRect(x, y, w, h)
      ctx.strokeStyle = isActive ? '#ff4d4f' : '#fa8c16'
      ctx.lineWidth = isActive ? 3 : 2
      ctx.setLineDash([6, 3])
      ctx.strokeRect(x, y, w, h)
      ctx.setLineDash([])
      if (isActive) {
        ctx.fillStyle = '#ff4d4f'
        ctx.font = 'bold 12px sans-serif'
        ctx.fillText('⚠ 存疑', x, y - 6)
      }
    } else if (seg.status === 'SKIPPED') {
      ctx.strokeStyle = 'rgba(0, 0, 0, 0.3)'
      ctx.lineWidth = 1
      ctx.setLineDash([3, 3])
      ctx.strokeRect(x, y, w, h)
      ctx.setLineDash([])
    }
    ctx.restore()
  })
}

function highlightArea(seg) {
  highlightedSegment = seg
  drawOverlay(seg)
}

function clearHighlight() {
  highlightedSegment = null
  drawOverlay()
}

function onCanvasClick(e) {
  const canvas = overlayCanvas.value
  const img = previewImage.value
  if (!canvas || !img) return
  const canvasRect = canvas.getBoundingClientRect()
  const clickX = e.clientX - canvasRect.left
  const clickY = e.clientY - canvasRect.top
  const scaleX = canvas.width / img.naturalWidth
  const scaleY = canvas.height / img.naturalHeight
  for (const seg of reviewSegments.value) {
    let box
    try { box = JSON.parse(seg.boundingBox || '{}') } catch { box = {} }
    if (!box.w || !box.h) continue
    const x = box.x * scaleX
    const y = box.y * scaleY
    const w = box.w * scaleX
    const h = box.h * scaleY
    if (clickX >= x && clickX <= x + w && clickY >= y && clickY <= y + h) {
      focusSeg(seg)
      return
    }
  }
}

function handleShortcut(e) {
  if (!reviewing.value) return
  if (submitDialogVisible.value) return
  const activeSeg = reviewSegments.value.find(s => s._active)
  if (e.key === 'j' || e.key === 'ArrowDown') { e.preventDefault(); navigateNext() }
  else if (e.key === 'k' || e.key === 'ArrowUp') { e.preventDefault(); navigatePrev() }
  else if (e.key === 'n' || e.key === 'N') { e.preventDefault(); navigateUncertain() }
  else if (e.key === 'Enter' && !e.ctrlKey && !e.shiftKey) {
    if (activeSeg && (activeSeg.status === 'PENDING' || activeSeg.status === 'UNCERTAIN')) {
      activeSeg.status = 'CONFIRMED'
      activeSeg._active = false
      drawOverlay()
      navigateNext()
    }
    e.preventDefault()
  }
  else if (e.key === 'Enter' && e.ctrlKey && !e.shiftKey) {
    if (activeSeg && (activeSeg.status === 'PENDING' || activeSeg.status === 'UNCERTAIN')) { e.preventDefault(); confirmSegment(activeSeg) }
  }
  else if (e.key === 'Enter' && e.shiftKey && !e.ctrlKey) {
    if (activeSeg && (activeSeg.status === 'PENDING' || activeSeg.status === 'UNCERTAIN')) { e.preventDefault(); skipSegment(activeSeg) }
  }
  else if (e.key === 's' && e.ctrlKey && !e.shiftKey) { e.preventDefault(); saveDraft() }
  else if (e.key === 'u' || e.key === 'U') {
    if (activeSeg && (activeSeg.status === 'CONFIRMED' || activeSeg.status === 'REVIEWED')) {
      e.preventDefault()
      undoSegment(activeSeg)
    }
  }
  else if (e.key === 'Enter' && e.ctrlKey && e.shiftKey) { e.preventDefault(); openSubmitDialog() }
}
</script>

<style scoped>
.kr-page { max-width: 1040px; margin: 0 auto; width: 100%; padding: var(--page-pad-y) var(--page-pad-x); }
.kr-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--s-6); }
.kr-title { font-family: var(--font-heading); font-size: var(--text-2xl); font-weight: var(--weight-semibold); color: var(--ink); margin: 0; }
.kr-tabs {
  display: flex; gap: 0; background: var(--base-alt); border-radius: var(--radius-md);
  padding: 3px;
}
.kr-tabs button {
  padding: var(--s-2) var(--s-6); border: none; border-radius: calc(var(--radius-md) - 2px);
  background: transparent; color: var(--ink-soft); cursor: pointer;
  font-size: var(--text-sm); font-family: var(--font-body); font-weight: var(--weight-medium);
  transition: all var(--dur-fast) var(--ease-soft);
}
.kr-tabs button.active {
  background: var(--brand); color: #fff;
  box-shadow: 0 1px 3px oklch(0.38 0.105 175 / 0.30);
}

.kr-upload { margin-top: var(--s-4); }
.upload-zone {
  border: 2px dashed var(--border); border-radius: var(--radius-lg); padding: var(--s-12) var(--s-10);
  text-align: center; background: var(--surface); color: var(--ink-soft); font-size: var(--text-sm);
}
.kr-file-name { margin-top: var(--s-3); font-weight: var(--weight-medium); color: var(--ink); }
.kr-cat { margin: var(--s-5) 0; display: flex; align-items: center; gap: var(--s-3); flex-wrap: wrap; justify-content: center; }
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
.kr-cat-menu-del { color: var(--ink-soft); font-size: var(--text-lg); line-height: 1; }
.kr-cat-menu-del:hover { color: #cf1322; }
.kr-cat-add-row { display: inline-flex; gap: var(--s-2); align-items: center; }
.kr-cat-inp {
  padding: var(--s-2) var(--s-3); border: 1.5px solid var(--border); border-radius: var(--radius-md);
  font-size: var(--text-sm); font-family: var(--font-body); width: 140px; background: var(--base);
  transition: border-color var(--dur-fast) var(--ease-soft);
}
.kr-cat-inp:focus { border-color: var(--brand); outline: none; }

.kr-btn {
  padding: var(--s-2) var(--s-5); border: none; border-radius: var(--radius-md);
  background: var(--base-alt); cursor: pointer; font-size: var(--text-sm); font-family: var(--font-body);
  font-weight: var(--weight-medium); color: var(--ink-soft);
  transition: all var(--dur-fast) var(--ease-soft);
  margin: var(--s-1);
}
.kr-btn:hover { background: var(--border); color: var(--ink); }
.kr-btn-primary { background: var(--brand); color: #fff; }
.kr-btn-primary:hover { background: var(--brand-deep); }
.kr-btn-danger { background: var(--danger-soft); color: var(--danger); }
.kr-btn-danger:hover { background: #ffd8d2; }
.kr-btn-warn { background: #fff7e6; color: #d46b08; }
.kr-btn-warn:hover { background: #ffe7ba; }
.kr-btn-sm { padding: var(--s-1) var(--s-3); font-size: var(--text-xs); }
.kr-btn-undo { background: var(--base); color: var(--ink-muted); }
.kr-btn-undo:hover { background: var(--base-alt); color: var(--ink-soft); }
.kr-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.kr-btn:disabled:hover { background: var(--base-alt); }
.kr-btn-primary:disabled:hover { background: var(--brand); }
.kr-progress { width: 100%; margin-top: var(--s-3); height: 6px; }

.kr-list { margin-top: var(--s-4); }
.kr-empty { color: var(--ink-soft); text-align: center; padding: var(--s-10); }
.ck-search { display: flex; gap: var(--s-3); align-items: center; }
.ck-search-inp {
  flex: 1; padding: var(--s-3) var(--s-4); border: 1.5px solid var(--border); border-radius: var(--radius-md);
  font-size: var(--text-sm); font-family: var(--font-body); background: var(--base);
  transition: border-color var(--dur-fast) var(--ease-soft);
}
.ck-search-inp:focus { border-color: var(--brand); outline: none; }
.ck-search-sel {
  padding: var(--s-3) var(--s-4); border: 1.5px solid var(--border); border-radius: var(--radius-md);
  font-size: var(--text-sm); font-family: var(--font-body); background: var(--surface); min-width: 140px;
}
.kr-card { display: flex; align-items: center; justify-content: space-between; padding: var(--s-4); margin-bottom: var(--s-2);
  background: var(--surface); border-radius: var(--radius-md); border: 1px solid var(--border-light);
  transition: border-color var(--dur-fast) var(--ease-soft), box-shadow var(--dur-fast) var(--ease-soft);
}
.kr-card:hover { border-color: var(--brand); box-shadow: var(--shadow-sm); }
.kr-card-info { display: flex; align-items: center; gap: var(--s-3); flex-wrap: wrap; }
.kr-tag { font-size: var(--text-3xs); padding: 2px 8px; border-radius: var(--radius-full); background: var(--base-alt); color: var(--ink-soft); }
.kr-tag-status { background: var(--brand-soft); color: var(--brand); }
.kr-tag-dify { background: var(--base-alt); }
.kr-tag-success { background: var(--success-soft); color: var(--success); }
.kr-tag-danger { background: var(--danger-soft); color: var(--danger); }
.kr-date { font-size: var(--text-xs); color: var(--ink-muted); }
.kr-card-actions { display: inline-flex; gap: var(--s-2); align-items: center; }

.kr-review-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.55); z-index: 100;
  display: flex; justify-content: center; padding-top: var(--s-4);
  outline: none;
}
.kr-review {
  width: 95vw; max-width: 1400px; background: var(--base); border-radius: var(--radius-lg);
  display: flex; flex-direction: column; max-height: 94vh;
}
.kr-review-bar {
  display: flex; align-items: center; gap: var(--s-2); padding: var(--s-3) var(--s-5);
  border-bottom: 1px solid var(--border); flex-shrink: 0; flex-wrap: wrap;
}
.kr-review-bar h3 { flex: 1; margin: 0; font-size: var(--text-md); min-width: 200px; }
.kr-bar-group { display: flex; gap: var(--s-2); }
.kr-bar-sep { width: 1px; height: 24px; background: var(--border); flex-shrink: 0; }
.kr-badge { font-size: var(--text-xs); color: var(--ink-soft); }
.kr-badge-count { white-space: nowrap; }
.kr-review-body { flex: 1; overflow: hidden; padding: var(--s-4); min-height: 0; }

.kr-split { display: flex; gap: var(--s-4); height: 100%; }
.kr-preview-pane { flex: 1; min-width: 0; overflow: auto; background: #f5f5f5; border-radius: var(--radius-md); display: flex; align-items: flex-start; justify-content: center; }
.kr-image-container { position: relative; max-width: 100%; max-height: 100%; }
.kr-preview-image { max-width: 100%; max-height: 70vh; object-fit: contain; display: block; }
.kr-overlay-canvas { position: absolute; pointer-events: auto; cursor: crosshair; }
.kr-pdf-container { width: 100%; height: 100%; display: flex; flex-direction: column; }
.kr-pdf-frame { width: 100%; flex: 1; border: none; min-height: 60vh; }
.kr-text-preview { width: 100%; padding: var(--s-4); }
.kr-text-content { white-space: pre-wrap; font-size: var(--text-sm); line-height: 1.7; color: var(--ink); }
.kr-toc {
  width: 180px; flex-shrink: 0; overflow-y: auto; padding: var(--s-3);
  background: var(--surface); border-radius: var(--radius-md); border: 1px solid var(--border-light);
  align-self: flex-start; max-height: calc(85vh - 200px); position: sticky; top: 0;
}
.kr-toc-item {
  padding: var(--s-1) 0; font-size: var(--text-xs); color: var(--ink-soft); cursor: pointer; line-height: 1.4;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.kr-toc-item:hover, .kr-toc-item[data-active="true"] { color: var(--brand); font-weight: var(--weight-medium); }
.kr-toc-l1 { font-size: var(--text-sm); font-weight: var(--weight-semibold); }
.kr-toc-l2 { font-size: var(--text-xs); }
.kr-toc-l3 { font-size: var(--text-2xs); }
.kr-toc-l4, .kr-toc-l5, .kr-toc-l6 { font-size: var(--text-2xs); color: var(--ink-soft); }
.kr-seg-pane { flex: 1; min-width: 320px; max-width: 480px; overflow-y: auto; }

.kr-seg-list { display: flex; flex-direction: column; gap: var(--s-2); }
.kr-seg {
  padding: var(--s-3); border-radius: var(--radius-md); border-left: 4px solid var(--border);
  background: var(--surface); cursor: pointer; transition: all var(--dur-fast);
}
.kr-seg-active { box-shadow: 0 0 0 2px var(--brand); }
.kr-seg-confirmed { border-left-color: #52c41a; }
.kr-seg-reviewed { border-left-color: #1890ff; }
.kr-seg-uncertain { border-left-color: #fa8c16; background: oklch(0.97 0.02 75); }
.kr-seg-skipped { border-left-color: #d9d9d9; opacity: 0.6; }
.kr-seg-pending { border-left-color: var(--ink-soft); background: #fafafa; }
.kr-seg-head { display: flex; align-items: center; gap: var(--s-3); margin-bottom: var(--s-2); }
.kr-seg-idx { font-weight: var(--weight-semibold); font-size: var(--text-sm); color: var(--ink); }
.kr-seg-conf { font-size: var(--text-xs); padding: 1px 6px; border-radius: var(--radius-full); }
.kr-conf-high { background: #e6f7e6; color: #389e0d; }
.kr-conf-low { background: #fff7e6; color: #d46b08; }
.kr-seg-status { font-size: var(--text-xs); color: var(--ink-soft); }
.kr-seg-text {
  font-size: var(--text-sm); color: var(--ink); line-height: 1.8;
  margin-bottom: var(--s-2);
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-body);
}
.kr-strike { text-decoration: line-through; color: var(--ink-soft); }
.kr-seg-actions { display: flex; gap: var(--s-2); justify-content: flex-end; margin-top: var(--s-2); }
.kr-seg-reviewed { font-size: var(--text-sm); color: var(--ink); margin-top: var(--s-1); }
.kr-reviewed-label { color: #1890ff; font-weight: var(--weight-medium); }
.kr-confirmed-label { color: #52c41a; font-weight: var(--weight-medium); }

.kr-review-footer {
  border-top: 1px solid var(--border); padding: var(--s-3) var(--s-5); flex-shrink: 0;
  display: flex; align-items: center; gap: var(--s-4); flex-wrap: wrap;
}
.kr-footer-stats { font-size: var(--text-xs); color: var(--ink-soft); }
.kr-footer-actions { display: flex; gap: var(--s-2); }
.kr-footer-shortcuts { font-size: var(--text-3xs); color: var(--ink-soft); margin-left: auto; }

.kr-dialog-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 200;
  display: flex; align-items: center; justify-content: center;
}
.kr-dialog {
  background: var(--base); border-radius: var(--radius-lg); padding: var(--s-8);
  max-width: 480px; width: 90%;
}
.kr-dialog h3 { margin: 0 0 var(--s-5) 0; font-size: var(--text-lg); }
.kr-dialog-stats { display: flex; flex-direction: column; gap: var(--s-3); margin-bottom: var(--s-5); }
.kr-dialog-stat { font-size: var(--text-sm); padding: var(--s-2) var(--s-3); border-radius: var(--radius-md); }
.kr-dstat-confirmed { background: #e6f7e6; color: #389e0d; }
.kr-dstat-reviewed { background: #e6f0ff; color: #1890ff; }
.kr-dstat-uncertain { background: #fff7e6; color: #d46b08; }
.kr-dstat-skipped { background: #f0f0f0; color: #999; }
.kr-dialog-warn { color: #cf1322; font-size: var(--text-sm); margin-bottom: var(--s-3); }
.kr-dialog-hint { color: var(--ink-soft); font-size: var(--text-xs); margin-bottom: var(--s-5); }
.kr-dialog-actions { display: flex; gap: var(--s-3); justify-content: flex-end; }
</style>
