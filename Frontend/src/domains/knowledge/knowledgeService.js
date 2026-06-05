import http from '@/core/axios'

export const httpPut = (url, data) => http.put(url, data)
export const httpDelete = (url) => http.delete(url)
export const getOriginalFileUrl = (documentId) => `/api/knowledge/file/${documentId}`
export const getPreviewFileUrl = (documentId) => `/api/knowledge/preview/${documentId}`

export const uploadAndOcr = (formData) =>
  http.post('/api/knowledge/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })

export const getReviewData = (documentId) =>
  http.get(`/api/knowledge/review/${documentId}`)

export const submitReview = (documentId, segments, reviewedBy) =>
  http.post(`/api/knowledge/review/${documentId}/submit`, { segments, reviewedBy })

export const getKnowledgeList = (status) =>
  http.get('/api/knowledge/list', { params: { status } })

export const getPendingReview = (keyword = '', category = '') =>
  http.get('/api/knowledge/pending-review', { params: { keyword, category } })

export const getKnowledgeDetail = (documentId) =>
  http.get(`/api/knowledge/detail/${documentId}`)

export const searchKnowledge = (keyword, page = 1, size = 20, category = '') =>
  http.get('/api/knowledge/search', { params: { keyword, page, size, category } })

export const getCategoryList = () =>
  http.get('/api/knowledge/categories')

export const getRevisionHistory = (documentId) =>
  http.get(`/api/knowledge/${documentId}/history`)

export const archiveDocument = (documentId, reason) =>
  http.post(`/api/knowledge/${documentId}/archive`, { reason })

export const retryDifySync = (documentId) =>
  http.post(`/api/knowledge/${documentId}/retry-sync`)

export const rejectDocument = (documentId, reviewedBy) =>
  http.delete(`/api/knowledge/review/${documentId}/reject`, { data: { reviewedBy } })

export const deleteKnowledgeDocument = (documentId) =>
  http.delete(`/api/knowledge/${documentId}`)

export const toggleDocumentEnabled = (documentId, enabled) =>
  http.put(`/api/knowledge/${documentId}/toggle`, { enabled })

export const regenerateAllToc = () =>
  http.post('/api/knowledge/regenerate-toc')

export const reindexAllToEs = () =>
  http.post('/api/knowledge/reindex-es')

export const getCategoryStats = () =>
  http.get('/api/knowledge/categories/stats')

export const initChunkUpload = (fileName, totalChunks, fileHash, fileSize) =>
  http.post('/api/upload/init', { fileName, totalChunks, fileHash, fileSize })

export const uploadChunk = (uploadId, index, chunkBlob) => {
  const formData = new FormData()
  formData.append('file', chunkBlob)
  return http.post(`/api/upload/chunk?index=${index}&uploadId=${uploadId}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const mergeChunks = (uploadId, fileHash) =>
  http.post('/api/upload/merge', { uploadId, fileHash })

export const batchArchive = (documentIds, reason = 'BATCH_MANUAL') =>
  http.post('/api/knowledge/batch-archive', { documentIds, reason })

export const batchDeleteDocuments = (documentIds) =>
  http.post('/api/knowledge/batch-delete', { documentIds })
