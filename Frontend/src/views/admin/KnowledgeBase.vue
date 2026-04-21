<template>
  <div class="kb-container">
    <h2>知识库管理</h2>
    
    <div class="upload-section">
      <!-- 70% width upload area, centered -->
      <div 
        class="upload-box"
        @click="triggerFileInput"
        @drop.prevent="handleDrop"
        @dragover.prevent
        @dragenter.prevent="isDragging = true"
        @dragleave.prevent="isDragging = false"
        :class="{ 'dragging': isDragging }"
      >
        <input 
          type="file" 
          ref="fileInput" 
          class="hidden-input" 
          @change="handleFileSelect"
          accept=".pdf,.doc,.docx,.xls,.xlsx,.txt"
        />
        <div class="upload-content">
          <p class="upload-text">点击或将文档拖入</p>
          <p v-if="selectedFile" class="file-name">已选择: {{ selectedFile.name }}</p>
        </div>
      </div>
      
      <!-- Button area -->
      <div class="action-area">
        <button 
          v-if="selectedFile" 
          @click="uploadFile" 
          class="upload-btn"
          :disabled="uploading"
        >
          {{ uploading ? '上传中...' : '开始上传' }}
        </button>
      </div>

      <!-- Log area (below button) -->
      <div class="log-area">
        <div v-if="uploadResult" class="result-message success">
          上传成功! 文档ID: {{ uploadResult.docId }}, 分片数: {{ uploadResult.chunkCount }}
        </div>
        <div v-if="errorMessage" class="result-message error">
          {{ errorMessage }}
        </div>
      </div>
    </div>

    <!-- Document List Table -->
    <div class="document-list-section">
      <h3>文档列表</h3>
      <table class="doc-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>标题</th>
            <th>分类</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="doc in documents" :key="doc.id">
            <td>{{ doc.id }}</td>
            <td>{{ doc.title }}</td>
            <td>{{ doc.category || '未分类' }}</td>
            <td>
              <span :class="['status-badge', doc.status === 1 ? 'active' : 'inactive']">
                {{ doc.status === 1 ? '启用' : '禁用' }}
              </span>
            </td>
            <td>{{ new Date(doc.createTime).toLocaleString() }}</td>
            <td class="actions">
              <button 
                @click="toggleStatus(doc)" 
                class="action-btn toggle"
              >
                {{ doc.status === 1 ? '禁用' : '启用' }}
              </button>
              <button 
                @click="handleDelete(doc.id)" 
                class="action-btn delete"
              >
                删除
              </button>
            </td>
          </tr>
          <tr v-if="documents.length === 0">
            <td colspan="6" class="empty-state">暂无文档</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Delete Confirmation Modal -->
    <div v-if="showDeleteModal" class="modal-overlay">
      <div class="modal-content">
        <h3>确定删除该文档吗？</h3>
        <p>此操作将同步删除数据库和Dify知识库中的文档，且不可恢复。</p>
        <div class="modal-actions">
          <button @click="cancelDelete" class="modal-btn cancel">取消</button>
          <button @click="confirmDelete" class="modal-btn confirm">确定</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { uploadDocument, getDocuments, deleteDocument, updateDocumentStatus } from '../../api/kb'

const fileInput = ref(null)
const selectedFile = ref(null)
const isDragging = ref(false)
const uploading = ref(false)
const uploadResult = ref(null)
const errorMessage = ref('')
const documents = ref([])

// Delete Modal State
const showDeleteModal = ref(false)
const deleteDocId = ref(null)

const triggerFileInput = () => {
  fileInput.value.click()
}

const handleFileSelect = (event) => {
  const files = event.target.files
  if (files.length > 0) {
    selectedFile.value = files[0]
    resetStatus()
  }
}

const handleDrop = (event) => {
  isDragging.value = false
  const files = event.dataTransfer.files
  if (files.length > 0) {
    selectedFile.value = files[0]
    resetStatus()
  }
}

const resetStatus = () => {
  uploadResult.value = null
  errorMessage.value = ''
}

const fetchDocuments = async () => {
  try {
    const res = await getDocuments()
    if (res.data.code === 200) {
      documents.value = res.data.data
    }
  } catch (error) {
    console.error("Failed to fetch documents:", error)
  }
}

const uploadFile = async (force = false) => {
  // Handle event object being passed as first argument
  if (typeof force !== 'boolean') force = false
  
  if (!selectedFile.value) return

  uploading.value = true
  errorMessage.value = ''
  uploadResult.value = null

  const formData = new FormData()
  formData.append('file', selectedFile.value)
  formData.append('category', 'manual') // Default category
  if (force) {
    formData.append('force', 'true')
  }

  try {
    const response = await uploadDocument(formData)
    if (response.data.code === 200) {
      uploadResult.value = response.data.data
      selectedFile.value = null // Clear selection allowing new upload
      fetchDocuments() // Refresh list
    } else if (response.data.code === 409) {
      // Prompt user for duplicate file
      const confirmUpload = confirm(`文件 "${selectedFile.value.name}" 已存在，是否继续上传？\n(系统将自动重命名为 "${selectedFile.value.name}(1)" 等)`)
      if (confirmUpload) {
        // Recursive call with force=true
        // We must return here to keep uploading state true, or handle it properly
        // However, await uploadFile(true) inside async function works.
        // But need to be careful about `uploading` state.
        // It's already true.
        await uploadFile(true)
        return // Return to avoid setting uploading=false twice prematurely if recursive call handles it
      } else {
        errorMessage.value = '已取消上传'
      }
    } else {
      errorMessage.value = response.data.message || '上传失败'
    }
  } catch (error) {
    console.error(error)
    errorMessage.value = '网络错误或服务器异常'
  } finally {
    // Only set false if not recursively calling (which would keep it true)
    // Actually, recursive call will set it to false in its finally block.
    // So when control returns here, it is already false?
    // Yes.
    // But if we return early above, we skip this finally?
    // No, 'return' inside try block still executes finally block.
    // So if I recursively call, the inner call sets false. Then outer call sets false again.
    // That's fine.
    // BUT, if I return early, the finally block runs immediately after return.
    // So:
    // 1. uploadFile(false) starts. uploading=true.
    // 2. 409. confirm.
    // 3. await uploadFile(true).
    //    3.1. uploadFile(true) starts. uploading=true.
    //    3.2. Success.
    //    3.3. Finally: uploading=false.
    // 4. Returns to outer.
    // 5. Outer Finally: uploading=false.
    
    // The only issue is if I don't await, or if the UI flickers. Await is fine.
    uploading.value = false
  }
}

const handleDelete = (id) => {
  deleteDocId.value = id
  showDeleteModal.value = true
}

const confirmDelete = async () => {
  if (!deleteDocId.value) return

  try {
    const res = await deleteDocument(deleteDocId.value)
    if (res.data.code === 200) {
      alert('删除成功')
      fetchDocuments()
    } else {
      alert('删除失败: ' + res.data.message)
    }
  } catch (error) {
    console.error(error)
    const msg = error.response?.data?.message || '删除失败，请检查网络或联系管理员'
    alert(msg)
  } finally {
    showDeleteModal.value = false
    deleteDocId.value = null
  }
}

const cancelDelete = () => {
  showDeleteModal.value = false
  deleteDocId.value = null
}

const toggleStatus = async (doc) => {
  const newStatus = doc.status === 1 ? 0 : 1
  const oldStatus = doc.status
  
  doc.status = newStatus
  
  try {
    const res = await updateDocumentStatus(doc.id, newStatus)
    if (res.data.code !== 200) {
      doc.status = oldStatus
      alert('状态更新失败: ' + res.data.message)
    }
  } catch (error) {
    console.error(error)
    doc.status = oldStatus
    const msg = error.response?.data?.message || '状态更新失败，请检查网络或联系管理员'
    alert(msg)
  }
}

onMounted(() => {
  fetchDocuments()
})
</script>

<style scoped>
.kb-container {
  padding: 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow-y: auto; /* Enable scrolling for the whole page */
}

.upload-section {
  display: flex;
  flex-direction: column;
  align-items: center; /* Center everything horizontally */
  margin-top: 20px;
  margin-bottom: 40px;
}

.upload-box {
  width: 70%; /* Requirement: 70% width */
  height: 200px; /* Fixed height for consistency */
  border: 2px dashed #bdc3c7;
  border-radius: 8px;
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  transition: all 0.3s;
  background-color: #fafafa;
}

.upload-box:hover, .upload-box.dragging {
  border-color: #3498db;
  background-color: #ecf0f1;
}

.upload-content {
  text-align: center;
}

.upload-text {
  color: #7f8c8d;
  font-size: 18px;
  margin: 0;
}

.file-name {
  margin-top: 10px;
  color: #2c3e50;
  font-weight: bold;
}

.hidden-input {
  display: none;
}

.action-area {
  margin-top: 20px;
  width: 70%; /* Match upload box width */
  display: flex;
  justify-content: center;
}

.upload-btn {
  padding: 10px 40px;
  background-color: #27ae60;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 16px;
  transition: background-color 0.3s;
}

.upload-btn:disabled {
  background-color: #95a5a6;
  cursor: not-allowed;
}

.upload-btn:hover:not(:disabled) {
  background-color: #2ecc71;
}

.log-area {
  margin-top: 20px;
  width: 70%; /* Match upload box width */
}

.result-message {
  padding: 10px;
  border-radius: 4px;
  text-align: center;
}

.success {
  background-color: #d4edda;
  color: #155724;
}

.error {
  background-color: #f8d7da;
  color: #721c24;
}

/* Table Styles */
.document-list-section {
  width: 90%;
  margin: 0 auto;
}

.doc-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 20px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
  background: white;
  border-radius: 8px;
  overflow: hidden;
}

.doc-table th, .doc-table td {
  padding: 12px 15px;
  text-align: left;
  border-bottom: 1px solid #eee;
}

.doc-table th {
  background-color: #f8f9fa;
  font-weight: 600;
  color: #333;
}

.status-badge {
  padding: 4px 8px;
  border-radius: 12px;
  font-size: 0.85rem;
}

.status-badge.active {
  background-color: #e6fffa;
  color: #00b894;
}

.status-badge.inactive {
  background-color: #ffeaa7;
  color: #d63031;
}

.actions {
  display: flex;
  gap: 10px;
}

.action-btn {
  padding: 5px 10px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
  transition: background 0.2s;
}

.action-btn.toggle {
  background-color: #3498db;
  color: white;
}

.action-btn.toggle:hover {
  background-color: #2980b9;
}

.action-btn.delete {
  background-color: #e74c3c;
  color: white;
}

.action-btn.delete:hover {
  background-color: #c0392b;
}

.empty-state {
  text-align: center;
  color: #999;
  padding: 30px;
}

/* Modal Styles */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background-color: white;
  padding: 25px;
  border-radius: 8px;
  width: 400px;
  text-align: center;
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.2);
}

.modal-content h3 {
  margin-top: 0;
  color: #2c3e50;
  margin-bottom: 15px;
}

.modal-content p {
  color: #7f8c8d;
  margin-bottom: 25px;
  font-size: 15px;
}

.modal-actions {
  display: flex;
  justify-content: center;
  gap: 20px;
}

.modal-btn {
  padding: 10px 30px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: all 0.2s;
}

.modal-btn.cancel {
  background-color: #ecf0f1;
  color: #7f8c8d;
}

.modal-btn.cancel:hover {
  background-color: #bdc3c7;
  color: #2c3e50;
}

.modal-btn.confirm {
  background-color: #e74c3c;
  color: white;
}

.modal-btn.confirm:hover {
  background-color: #c0392b;
  transform: translateY(-1px);
}
</style>
