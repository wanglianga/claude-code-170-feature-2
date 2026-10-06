// 平台字典
export const SOURCE_LABEL = {
  SMART_BUTTON: '智能按钮',
  PHONE: '电话',
  MINI_APP: '社区小程序'
}

export const TRIAGE_LABEL = {
  LIFE_ASSIST: '生活协助',
  BODY_DISCOMFORT: '身体不适',
  FALL_RISK: '跌倒风险',
  EMOTIONAL_CRISIS: '情绪危机',
  FALSE_TRIGGER: '误触'
}

export const SEVERITY_LABEL = {
  LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '紧急'
}

export const SEVERITY_TAG = {
  LOW: 'info', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger'
}

export const TARGET_LABEL = {
  GRID_WORKER: '网格员',
  NEIGHBOR_VOLUNTEER: '邻里志愿者',
  FAMILY_DOCTOR: '家庭医生',
  EMERGENCY_120: '120急救'
}

export const SERVICE_LABEL = {
  DAILY_VISIT: '日常巡访',
  MEAL_SERVICE: '助餐',
  AGING_RETROFIT: '适老化改造',
  PSYCH_COMPANION: '心理陪伴',
  TEMP_CARE: '临时照护',
  HOSPITAL_ESCORT: '送医陪诊',
  OTHER: '其他服务'
}

export const FOLLOW_RESULT_LABEL = {
  IMPROVED: '好转',
  STABLE: '平稳',
  WORSENED: '恶化',
  NO_ANSWER: '未联系上'
}

export const RISK_LABEL = {
  CANNOT_OPEN_DOOR: '无法开门',
  LOCATION_OFFSET: '定位偏移',
  FAMILY_UNREACHABLE: '家属长期不接电话',
  NEIGHBOR_INFO_UNCLEAR: '邻居代报信息不清',
  REFUSE_HOSPITAL: '老人拒绝就医',
  HOME_SAFETY_HAZARD: '家中存在安全隐患',
  FREQUENT_NIGHT_HELP: '频繁夜间求助'
}

export const RISK_TAG = {
  CANNOT_OPEN_DOOR: 'danger',
  LOCATION_OFFSET: 'warning',
  FAMILY_UNREACHABLE: 'danger',
  NEIGHBOR_INFO_UNCLEAR: 'warning',
  REFUSE_HOSPITAL: 'danger',
  HOME_SAFETY_HAZARD: 'danger',
  FREQUENT_NIGHT_HELP: 'danger'
}

export const STATUS_LABEL = {
  NEW: '待研判',
  TRIAGED: '已研判',
  DISPATCHED: '已转介',
  ONSITE: '已上门',
  SERVICED: '已服务',
  COMMUNICATED: '已沟通家属',
  FOLLOWED_UP: '待回访归档',
  CLOSED: '已归档'
}

export const STATUS_TAG = {
  NEW: 'danger',
  TRIAGED: 'warning',
  DISPATCHED: 'warning',
  ONSITE: 'primary',
  SERVICED: 'primary',
  COMMUNICATED: 'primary',
  FOLLOWED_UP: 'warning',
  CLOSED: 'success'
}

export const RISK_LEVEL_LABEL = {
  LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '极高风险'
}

export const RISK_LEVEL_TAG = {
  LOW: 'success', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'danger'
}

export const ENTRY_METHODS = ['钥匙开门', '门禁密码', '智能锁授权', '邻居协助开门', '家属到场开门', '破锁入户', '未能入户']

export const ELDER_STATUS_OPTIONS = ['清醒正常', '身体不适', '已跌倒', '意识不清', '情绪激动', '未见到老人']

/* ---- 拒绝就医 · 风险随访 ---- */

// 复访方式
export const FOLLOW_TASK_TYPE_LABEL = {
  PHONE: '电话复访',
  VISIT: '上门复访',
  DOCTOR_REVIEW: '医生复诊'
}

// 复访结果
export const FOLLOW_TASK_RESULT_LABEL = {
  IMPROVED: '症状缓解',
  UNCHANGED: '未缓解',
  WORSENED: '症状加重',
  NO_ANSWER: '未联系上'
}

export const FOLLOW_TASK_RESULT_TAG = {
  IMPROVED: 'success',
  UNCHANGED: 'warning',
  WORSENED: 'danger',
  NO_ANSWER: 'info'
}

// 拒医记录状态
export const REFUSAL_STATUS_LABEL = {
  OPEN: '随访中',
  RESOLVED: '已缓解解除'
}

export const REFUSAL_STATUS_TAG = {
  OPEN: 'danger',
  RESOLVED: 'success'
}

// 复访任务状态
export const FOLLOW_TASK_STATUS_LABEL = {
  PENDING: '待复访',
  DONE: '已完成',
  CANCELLED: '已取消'
}

// 家属态度
export const FAMILY_ATTITUDE_OPTIONS = ['积极配合', '一般', '推诿拒绝', '未能联系上']
