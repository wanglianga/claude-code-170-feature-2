<template>
  <el-container style="height:100vh">
    <el-aside width="210px" class="aside">
      <div class="logo">
        <el-icon size="22"><AlarmClock /></el-icon>
        <span>独居老人求助平台</span>
      </div>
      <el-menu :default-active="$route.path" router background-color="#8e1f2b"
               text-color="#f5d6d6" active-text-color="#ffd04b">
        <el-menu-item index="/workbench">
          <el-icon><Monitor /></el-icon><span>值班工作台</span>
        </el-menu-item>
        <el-menu-item index="/elders">
          <el-icon><User /></el-icon><span>老人档案画像</span>
        </el-menu-item>
        <el-menu-item index="/stats">
          <el-icon><DataAnalysis /></el-icon><span>照护能力复盘</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="crumb">
          <el-icon><Location /></el-icon>
          <span>幸福社区</span>
          <el-divider direction="vertical" />
          <span>{{ $route.meta.title }}</span>
        </div>
        <div class="user-box">
          <el-badge :is-dot="online" type="danger" class="sse-badge">
            <el-tag size="small" :type="online ? 'success' : 'info'">
              {{ online ? '实时通道已连接' : '实时通道断开' }}
            </el-tag>
          </el-badge>
          <el-dropdown @command="onCommand">
            <span class="user-name">
              <el-icon><Avatar /></el-icon>
              {{ user.name }}
              <el-tag size="small" effect="dark" type="danger" style="margin-left:6px">{{ roleLabel }}</el-tag>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view v-slot="{ Component }">
          <component :is="Component" @refresh="connectSse" />
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import api from '../api'

const router = useRouter()
const route = useRoute()
const user = JSON.parse(localStorage.getItem('user') || '{}')
const online = ref(false)
let es = null
let retryTimer = null

const ROLE = {
  DUTY_WORKER: '值班社工', GRID_WORKER: '网格员', NEIGHBOR_VOLUNTEER: '邻里志愿者',
  FAMILY_DOCTOR: '家庭医生', ADMIN: '社区管理者'
}
const roleLabel = ROLE[user.role] || user.role

function connectSse() {
  if (es) { es.close(); es = null }
  const token = localStorage.getItem('token')
  es = new EventSource(`/api/events/stream?token=${token}`)
  // SSE 不能加自定义头，用 query token 兜底；后端默认放行 stream，数据为通知不含敏感信息
  es.addEventListener('connected', () => { online.value = true })
  es.addEventListener('event-new', ev => {
    try {
      const p = JSON.parse(ev.data)
      ElNotification({
        title: '新求助告警',
        message: `有新的一键求助事件，请立即研判（事件 #${p.eventId}）`,
        type: 'error',
        duration: 0,
        onClick: () => router.push(`/events/${p.eventId}`)
      })
      window.dispatchEvent(new CustomEvent('workbench-reload'))
    } catch (e) { /* ignore */ }
  })
  es.addEventListener('event-update', () => {
    if (route.path === '/workbench') reloadWorkbench?.()
  })
  es.addEventListener('refusal-escalated', ev => {
    try {
      const p = JSON.parse(ev.data)
      ElNotification({
        title: '拒医复访症状加重',
        message: `拒医风险随访复访发现症状加重，风险等级已提升，系统已重新通知家属和家庭医生（事件 #${p.eventId}）`,
        type: 'error',
        duration: 0,
        onClick: () => router.push(`/events/${p.eventId}`)
      })
      window.dispatchEvent(new CustomEvent('workbench-reload'))
    } catch (e) { /* ignore */ }
  })
  es.addEventListener('event-closed', () => reloadWorkbench?.())
  es.onerror = () => {
    online.value = false
    clearTimeout(retryTimer)
    retryTimer = setTimeout(connectSse, 5000)
  }
}

const reloadWorkbench = () => {
  window.dispatchEvent(new CustomEvent('workbench-reload'))
}

function onCommand(cmd) {
  if (cmd === 'logout') {
    api.post('/auth/logout').catch(() => {})
    localStorage.removeItem('token')
    localStorage.removeItem('user')
    router.push('/login')
  }
}

onMounted(connectSse)
onBeforeUnmount(() => { if (es) es.close(); clearTimeout(retryTimer) })
</script>

<style scoped>
.aside { background: #8e1f2b; }
.logo {
  height: 60px; display: flex; align-items: center; gap: 8px;
  color: #fff; font-weight: 700; font-size: 15px; padding: 0 16px;
  background: #741823;
}
.aside :deep(.el-menu) { border-right: none; }
.header {
  background: #fff; display: flex; align-items: center; justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
}
.crumb { display: flex; align-items: center; gap: 6px; color: #606266; font-size: 14px; }
.user-box { display: flex; align-items: center; gap: 16px; }
.user-name { display: flex; align-items: center; gap: 4px; cursor: pointer; color: #303133; }
.main { background: #f0f2f5; padding: 16px; overflow-y: auto; }
</style>
