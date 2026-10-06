<template>
  <div>
    <!-- KPI -->
    <el-row :gutter="12" class="kpi-row">
      <el-col :span="6" v-for="k in kpis" :key="k.label">
        <el-card shadow="never" :body-style="{ padding: '16px 20px' }">
          <div class="kpi">
            <div>
              <div class="kpi-num" :style="{ color: k.color }">{{ k.value }}</div>
              <div class="muted">{{ k.label }}</div>
            </div>
            <el-icon :size="34" :color="k.color"><component :is="k.icon" /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 拒医风险复访任务（24 小时时限） -->
    <el-card shadow="never" style="margin-bottom:12px" v-if="followTasks.length">
      <template #header>
        <div class="card-head">
          <span class="section-title" style="margin:0;color:#c45656">
            拒医风险复访任务
            <el-badge :value="followTasks.length" type="danger" style="margin-left:8px" />
          </span>
          <span class="muted">拒绝就医登记后 24 小时内须完成复访，逾期红色标记</span>
        </div>
      </template>
      <el-table :data="followTasks" size="small" @row-click="row => $router.push(`/events/${row.event.id}`)"
                style="cursor:pointer" :row-class-name="row => isOverdue(row) ? 'row-overdue' : ''">
        <el-table-column label="事件" width="130">
          <template #default="{ row }">{{ row.event.eventNo }}</template>
        </el-table-column>
        <el-table-column label="老人" width="90">
          <template #default="{ row }">{{ row.elder.name }}</template>
        </el-table-column>
        <el-table-column label="复访方式" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ FOLLOW_TASK_TYPE_LABEL[row.taskType] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="轮次" width="60">
          <template #default="{ row }">第{{ row.roundNo }}轮</template>
        </el-table-column>
        <el-table-column label="医生建议 / 拒绝原因" min-width="260">
          <template #default="{ row }">
            <div style="font-size:12px">
              <div><b>建议：</b>{{ row.refusal.doctorAdvice }}</div>
              <div class="muted"><b>拒绝：</b>{{ row.refusal.refuseReason }} · 家属{{ row.refusal.familyInformed ? `知情(${row.refusal.familyAttitude || '未记录'})` : '不知情' }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="拒医风险" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="RISK_LEVEL_TAG[row.refusal.riskLevel]" effect="dark">
              {{ RISK_LEVEL_LABEL[row.refusal.riskLevel] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="170">
          <template #default="{ row }">
            <span :style="{ color: isOverdue(row) ? '#f56c6c' : '', fontWeight: isOverdue(row) ? 700 : 400 }">
              {{ fmt(row.dueAt) }}
            </span>
            <el-tag v-if="isOverdue(row)" type="danger" size="small" style="margin-left:4px">已逾期</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span class="section-title" style="margin:0">求助事件队列</span>
          <div>
            <el-radio-group v-model="filter" size="small" @change="load" style="margin-right:12px">
              <el-radio-button value="">处置中</el-radio-button>
              <el-radio-button value="CLOSED">已归档</el-radio-button>
              <el-radio-button value="all">全部</el-radio-button>
            </el-radio-group>
            <el-button type="danger" :icon="Bell" @click="showHelp = true">模拟一键求助</el-button>
          </div>
        </div>
      </template>

      <el-table :data="events" stripe v-loading="loading" @row-click="row => $router.push(`/events/${row.id}`)"
                :row-class-name="rowClass" style="cursor:pointer">
        <el-table-column prop="eventNo" label="事件编号" width="130" />
        <el-table-column label="老人" width="100">
          <template #default="{ row }">{{ row.elder.name }}</template>
        </el-table-column>
        <el-table-column label="来源" width="100">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ SOURCE_LABEL[row.source] }}</el-tag>
            <el-icon v-if="row.nightHelp" title="夜间求助" color="#e6a23c" style="margin-left:4px"><Moon /></el-icon>
          </template>
        </el-table-column>
        <el-table-column label="研判" width="110">
          <template #default="{ row }">
            <span v-if="row.triageType">{{ TRIAGE_LABEL[row.triageType] }}</span>
            <span v-else class="muted">待研判</span>
          </template>
        </el-table-column>
        <el-table-column label="严重程度" width="90">
          <template #default="{ row }">
            <el-tag v-if="row.severity" size="small" :type="SEVERITY_TAG[row.severity]" effect="dark">
              {{ SEVERITY_LABEL[row.severity] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险标记" min-width="220">
          <template #default="{ row }">
            <el-tag v-for="f in (row.riskFlags || '').split(',').filter(Boolean)" :key="f"
                    :type="RISK_TAG[f]" size="small" style="margin:2px">{{ RISK_LABEL[f] }}</el-tag>
            <span v-if="!row.riskFlags" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS_TAG[row.status]">{{ STATUS_LABEL[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="求助时间" width="170">
          <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 模拟发起求助 -->
    <el-dialog v-model="showHelp" title="模拟老人发起一键求助" width="560px">
      <el-form :model="helpForm" label-width="92px">
        <el-form-item label="老人">
          <el-select v-model="helpForm.elderId" placeholder="选择老人" style="width:100%">
            <el-option v-for="e in elders" :key="e.id" :label="`${e.name}（${e.address}）`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="求助来源">
          <el-radio-group v-model="helpForm.source">
            <el-radio value="SMART_BUTTON">智能按钮</el-radio>
            <el-radio value="PHONE">电话</el-radio>
            <el-radio value="MINI_APP">社区小程序</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="设备/分机">
          <el-input v-model="helpForm.sourceDetail" placeholder="如 SOS-DEVICE-001 / 热线分机" />
        </el-form-item>
        <el-form-item label="语音内容">
          <el-input v-model="helpForm.voiceContent" type="textarea" :rows="2"
                    placeholder="老人电话/按钮接通后值班社工听到的内容" />
        </el-form-item>
        <el-form-item label="定位坐标">
          <el-input v-model.number="helpForm.callLongitude" placeholder="经度" style="width:48%" />
          <el-input v-model.number="helpForm.callLatitude" placeholder="纬度" style="width:48%;margin-left:4%" />
          <div class="muted" style="margin-top:4px">与老人档案住址偏差超过 500 米将自动标记“定位偏移”</div>
        </el-form-item>
        <el-form-item label="定位描述">
          <el-input v-model="helpForm.callAddress" placeholder="如 幸福社区东门附近" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showHelp = false">取消</el-button>
        <el-button type="danger" @click="submitHelp">发起求助</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell, BellFilled, Moon, Timer, CircleCheck, Warning } from '@element-plus/icons-vue'
import api from '../api'
import {
  SOURCE_LABEL, TRIAGE_LABEL, SEVERITY_LABEL, SEVERITY_TAG,
  RISK_LABEL, RISK_TAG, STATUS_LABEL, STATUS_TAG,
  FOLLOW_TASK_TYPE_LABEL, RISK_LEVEL_LABEL, RISK_LEVEL_TAG
} from '../api/dict'

const router = useRouter()
const events = ref([])
const elders = ref([])
const followTasks = ref([])
const loading = ref(false)
const filter = ref('')
const showHelp = ref(false)
const helpForm = reactive({
  elderId: null, source: 'SMART_BUTTON', sourceDetail: '',
  voiceContent: '', callLongitude: null, callLatitude: null, callAddress: ''
})

const kpis = computed(() => [
  { label: '待研判', value: events.value.filter(e => e.status === 'NEW').length, color: '#f56c6c', icon: BellFilled },
  { label: '处置中', value: events.value.filter(e => !['CLOSED', 'NEW'].includes(e.status)).length, color: '#e6a23c', icon: Timer },
  { label: '夜间求助', value: events.value.filter(e => e.nightHelp).length, color: '#722ed1', icon: Moon },
  { label: '含风险标记', value: events.value.filter(e => e.riskFlags).length, color: '#fa8c16', icon: Warning }
])

async function load() {
  loading.value = true
  try {
    const params = {}
    if (filter.value === 'CLOSED') params.status = 'CLOSED'
    else if (filter.value === 'all') params.all = true
    events.value = await api.get('/events', { params })
  } finally {
    loading.value = false
  }
}

async function loadElders() {
  elders.value = await api.get('/elders')
}

async function loadFollowTasks() {
  followTasks.value = await api.get('/refusal-follow-tasks', { params: { status: 'PENDING' } })
}

function isOverdue(task) {
  return task.status === 'PENDING' && task.dueAt && new Date(task.dueAt) < new Date()
}

async function submitHelp() {
  if (!helpForm.elderId) {
    ElMessage.warning('请选择老人')
    return
  }
  const e = await api.post('/events', { ...helpForm })
  ElMessage.success(`求助事件 ${e.eventNo} 已创建，风险标记：${e.riskFlags || '无'}`)
  showHelp.value = false
  await load()
  router.push(`/events/${e.id}`)
}

function rowClass({ row }) {
  return row.status === 'NEW' ? 'row-new' : ''
}

function fmt(s) {
  return s ? s.replace('T', ' ').substring(0, 16) : ''
}

function onReload() { load(); loadFollowTasks() }
onMounted(() => {
  load()
  loadElders()
  loadFollowTasks()
  window.addEventListener('workbench-reload', onReload)
})
onUnmounted(() => window.removeEventListener('workbench-reload', onReload))
</script>

<style scoped>
.kpi-row { margin-bottom: 12px; }
.kpi { display: flex; justify-content: space-between; align-items: center; }
.kpi-num { font-size: 30px; font-weight: 700; line-height: 1.2; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
:deep(.row-new) { background: #fef0f0 !important; }
:deep(.row-overdue) { background: #fef0f0 !important; }
</style>
