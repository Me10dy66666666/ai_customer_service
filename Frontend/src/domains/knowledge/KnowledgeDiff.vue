<template>
  <div class="kd-overlay" @click.self="$emit('close')">
    <div class="kd-dialog">
      <div class="kd-header">
        <h3>版本差异对比 — {{ documentTitle }}</h3>
        <button class="kd-close-btn" @click="$emit('close')">&times;</button>
      </div>

      <div class="kd-body">
        <div class="kd-select">
          <div class="kd-select-item">
            <span>版本A：</span>
            <select v-model="revA" @change="loadDiff">
              <option v-for="r in revisions" :key="r.id" :value="r.id">
                [{{ r.changeType }}] {{ r.changedBy }} — {{ r.changedAt }}
              </option>
            </select>
          </div>
          <div class="kd-select-item">
            <span>版本B：</span>
            <select v-model="revB" @change="loadDiff">
              <option v-for="r in revisions" :key="r.id" :value="r.id">
                [{{ r.changeType }}] {{ r.changedBy }} — {{ r.changedAt }}
              </option>
            </select>
          </div>
        </div>

        <div v-if="diffResult" class="kd-result">
          <div class="kd-meta">
            <span>A: {{ diffResult.revisionA?.changeType }} by {{ diffResult.revisionA?.changedBy }}</span>
            <span>B: {{ diffResult.revisionB?.changeType }} by {{ diffResult.revisionB?.changedBy }}</span>
          </div>
          <div class="kd-diff-container">
            <div class="kd-pane">
              <h4>旧版本</h4>
              <div class="kd-content">
                <span v-for="(part, idx) in diffParts" :key="idx"
                      :class="{ 'kd-added': part.added, 'kd-removed': part.removed, 'kd-equal': !part.added && !part.removed }">
                  {{ part.value }}
                </span>
              </div>
            </div>
            <div class="kd-pane">
              <h4>新版本</h4>
              <div class="kd-content">
                <span v-for="(part, idx) in diffParts" :key="idx"
                      :class="{ 'kd-added': part.added, 'kd-removed': part.removed, 'kd-equal': !part.added && !part.removed }"
                      v-show="!part.removed">
                  {{ part.value }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <div v-else-if="revisions.length > 0" class="kd-empty">请选择两个版本进行对比</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import * as knowledgeService from '@/domains/knowledge/knowledgeService'
import http from '@/core/axios'

const props = defineProps({ documentId: Number, documentTitle: String })
defineEmits(['close'])

const revisions = ref([])
const revA = ref(null)
const revB = ref(null)
const diffResult = ref(null)
const diffParts = ref([])

onMounted(async () => {
  try {
    const res = await knowledgeService.getRevisionHistory(props.documentId)
    if (res.data.code === 200) {
      revisions.value = res.data.data || []
      if (revisions.value.length >= 2) {
        revA.value = revisions.value[1].id
        revB.value = revisions.value[0].id
        loadDiff()
      }
    }
  } catch { ElMessage.error('加载版本历史失败') }
})

async function loadDiff() {
  if (!revA.value || !revB.value) return
  try {
    const res = await http.get(`/api/knowledge/${props.documentId}/diff?revA=${revA.value}&revB=${revB.value}`)
    if (res.data.code === 200) {
      diffResult.value = res.data.data
      diffParts.value = computeDiff(res.data.data.contentA || '', res.data.data.contentB || '')
    }
  } catch { ElMessage.error('加载差异对比失败') }
}

function computeDiff(textA, textB) {
  const linesA = textA.split('\n')
  const linesB = textB.split('\n')
  const parts = []

  let i = 0, j = 0
  while (i < linesA.length && j < linesB.length) {
    if (linesA[i] === linesB[j]) {
      parts.push({ value: linesA[i] + '\n', added: false, removed: false })
      i++; j++
    } else {
      const nextInB = linesB.indexOf(linesA[i], j)
      const nextInA = linesA.indexOf(linesB[j], i)
      if (nextInB === -1 || (nextInA !== -1 && nextInA <= nextInB - j)) {
        if (nextInA !== -1 && nextInA - i > 0) {
          for (let k = i; k < nextInA; k++) {
            parts.push({ value: linesA[k] + '\n', added: false, removed: true })
          }
          i = nextInA
        } else {
          parts.push({ value: linesA[i] + '\n', added: false, removed: true })
          i++
        }
      } else {
        for (let k = j; k < nextInB; k++) {
          parts.push({ value: linesB[k] + '\n', added: true, removed: false })
        }
        j = nextInB
      }
    }
  }
  while (i < linesA.length) parts.push({ value: linesA[i++] + '\n', added: false, removed: true })
  while (j < linesB.length) parts.push({ value: linesB[j++] + '\n', added: true, removed: false })

  return parts
}
</script>

<style scoped>
.kd-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.55); z-index: 300;
  display: flex; align-items: center; justify-content: center;
}
.kd-dialog {
  background: var(--base); border-radius: var(--radius-lg);
  width: 90vw; max-width: 1200px; max-height: 90vh;
  display: flex; flex-direction: column;
  box-shadow: 0 8px 40px rgba(0,0,0,0.18);
}
.kd-header {
  display: flex; align-items: center; justify-content: space-between;
  padding: var(--s-5) var(--s-6); border-bottom: 1px solid var(--border); flex-shrink: 0;
}
.kd-header h3 { margin: 0; font-size: var(--text-lg); font-family: var(--font-heading); }
.kd-close-btn {
  background: none; border: none; font-size: var(--text-xl); cursor: pointer;
  color: var(--ink-soft); padding: 0 var(--s-2); line-height: 1;
}
.kd-close-btn:hover { color: var(--ink); }
.kd-body { padding: var(--s-6); overflow-y: auto; flex: 1; min-height: 0; }

.kd-select { display: flex; gap: var(--s-6); margin-bottom: var(--s-5); background: var(--surface); padding: var(--s-4); border-radius: var(--radius-md); }
.kd-select-item { display: flex; align-items: center; gap: var(--s-2); font-size: var(--text-sm); }
.kd-select-item select { padding: var(--s-2) var(--s-3); border: 1px solid var(--border); border-radius: var(--radius-md); font-size: var(--text-sm); font-family: var(--font-body); max-width: 320px; }

.kd-meta { display: flex; gap: var(--s-6); font-size: var(--text-xs); color: var(--ink-soft); margin-bottom: var(--s-3); }
.kd-diff-container { display: flex; gap: var(--s-4); }
.kd-pane { flex: 1; min-width: 0; background: var(--surface); border-radius: var(--radius-md); overflow: hidden; border: 1px solid var(--border-light); }
.kd-pane h4 { margin: 0; padding: var(--s-3) var(--s-4); background: var(--base-alt); font-size: var(--text-sm); border-bottom: 1px solid var(--border-light); }
.kd-content { padding: var(--s-4); font-size: var(--text-sm); line-height: 1.7; max-height: 52vh; overflow-y: auto; white-space: pre-wrap; word-break: break-all; }
.kd-added { background: #e6ffec; color: #1a7f37; }
.kd-removed { background: #ffebe9; color: #cf222e; text-decoration: line-through; }
.kd-equal { color: var(--ink); }
.kd-empty { color: var(--ink-soft); text-align: center; padding: var(--s-10); }
</style>
