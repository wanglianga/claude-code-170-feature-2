<template>
  <div v-loading="loading">
    <el-page-header @back="$router.push('/workbench')" style="margin-bottom:12px">
      <template #content>
        <span style="font-weight:700;font-size:16px">{{ d.event?.eventNo }}</span>
        <el-tag :type="STATUS_TAG[d.event?.status]" style="margin-left:10px">{{ STATUS_LABEL[d.event?.status] }}</el-tag>
        <el-tag v-if="d.event?.nightHelp" type="warning" effect="dark" style="margin-left:6px">夜间求助</el-tag>
        <el-tag v-for="f in flags" :key="f" :type="RISK_TAG[f]" effect="dark" size="small" style="margin-left:6px">
          {{ RISK_LABEL[f] }}
        </el-tag>
      </template>
      <template #extra>
        <el-button-group>
          <el-button :icon="Plus" @click="flagDialog = true">补充风险标记</el-button>
        </el-button-group>
      </template>
    </el-page-header>

    <!-- 历史拒医带入提示：老人有未缓解的拒医记录（来自之前事件） -->
    <el-alert v-if="d.activeRefusal && d.activeRefusalEventNo" type="error" show-icon
              :closable="false" style="margin-bottom:12px">
      <template #title>
        该老人有未缓解的拒医记录（来自事件 {{ d.activeRefusalEventNo }}，随访中）——本次求助勿按普通求助处理
      </template>
      <div style="font-size:13px;line-height:1.8">
        <b>异常发现：</b>{{ d.activeRefusal.abnormalFinding || '身体指标异常' }}
        <b>医生建议：</b>{{ d.activeRefusal.doctorAdvice }}
        <b>拒绝原因：</b>{{ d.activeRefusal.refuseReason }}
        <b>家属：</b>{{ d.activeRefusal.familyInformed ? `知情（${d.activeRefusal.familyAttitude || '态度未记录'}）` : '不知情' }}
        <b>替代照护：</b>{{ d.activeRefusal.alternativeCare || '—' }}
        <b>拒医风险等级：</b>{{ RISK_LEVEL_LABEL[d.activeRefusal.riskLevel] }}
      </div>
    </el-alert>

    <el-row :gutter="12">
      <!-- 左：处置区 -->
      <el-col :span="17">
        <!-- 求助信息 -->
        <el-card shadow="never" style="margin-bottom:12px">
          <template #header><span class="section-title">求助信息</span></template>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="求助来源">
              {{ SOURCE_LABEL[d.event?.source] }}
              <span v-if="d.event?.sourceDetail" class="muted">（{{ d.event.sourceDetail }}）</span>
            </el-descriptions-item>
            <el-descriptions-item label="求助时间">{{ fmt(d.event?.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="定位">
              {{ d.event?.callAddress || '—' }}
              <span class="muted" v-if="d.event?.callLongitude">
                （{{ d.event.callLongitude }}, {{ d.event.callLatitude }}）
              </span>
              <el-tag v-if="d.event?.locationOffset" type="danger" size="small" style="margin-left:6px">
                偏移 {{ d.event.locationOffsetMeters }} 米
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="响应时长">
              <span v-if="d.event?.responseMinutes != null">{{ d.event.responseMinutes }} 分钟到场</span>
              <span v-else class="muted">尚未到场</span>
            </el-descriptions-item>
            <el-descriptions-item label="当前语音内容" :span="2">
              {{ d.event?.voiceContent || '（无语音内容）' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 操作按钮 -->
        <el-card shadow="never" style="margin-bottom:12px" v-if="d.event?.status !== 'CLOSED'">
          <template #header><span class="section-title">处置操作</span></template>
          <el-space wrap>
            <el-button type="danger" :icon="Search" @click="openTriage"
                       v-if="!d.event?.triageType">① 值班研判</el-button>
            <el-button type="warning" :icon="Phone" @click="openDispatch"
                       v-if="d.event?.triageType && d.event.triageType !== 'FALSE_TRIGGER'">② 转介通知</el-button>
            <el-button type="primary" :icon="House" @click="openOnsite"
                       v-if="d.event?.triageType && d.event.triageType !== 'FALSE_TRIGGER'">③ 上门核实</el-button>
            <el-button type="info" :icon="ChatDotRound" @click="openComm"
                       v-if="d.event?.triageType && d.event.triageType !== 'FALSE_TRIGGER'">家属沟通</el-button>
            <el-button type="success" :icon="Files" @click="openService"
                       v-if="d.event?.triageType && d.event.triageType !== 'FALSE_TRIGGER'">④ 后续服务</el-button>
            <el-button type="danger" plain :icon="FirstAidKit" @click="openRefusal"
                       v-if="d.event?.triageType && d.event.triageType !== 'FALSE_TRIGGER' && !hasOpenRefusal">拒医登记</el-button>
            <el-tooltip :disabled="!hasOpenRefusal" placement="top"
                        content="存在随访中的拒医记录：须先完成 24 小时复访且症状缓解后才能归档">
              <span>
                <el-button type="danger" plain :icon="CircleCheck" @click="openFollow" :disabled="hasOpenRefusal">
                  {{ d.event?.triageType === 'FALSE_TRIGGER' ? '② 回访归档' : '⑤ 回访归档' }}
                </el-button>
              </span>
            </el-tooltip>
            <el-button :icon="EditPen" @click="noteDialog = true">添加备注</el-button>
          </el-space>
        </el-card>

        <!-- 处置记录 -->
        <el-card shadow="never" style="margin-bottom:12px">
          <template #header><span class="section-title">转介与到场（{{ d.dispatches?.length || 0 }}）</span></template>
          <el-empty v-if="!d.dispatches?.length" :image-size="50" description="尚未转介" />
          <el-timeline v-else>
            <el-timeline-item v-for="x in d.dispatches" :key="x.id" :timestamp="fmt(x.notifiedAt)"
                              :type="x.effectiveResponse ? 'success' : 'primary'">
              <el-tag size="small">{{ TARGET_LABEL[x.targetType] }}</el-tag>
              <b style="margin:0 6px">{{ x.targetName }}</b>
              <span class="muted">{{ x.targetPhone }}</span>
              <el-tag size="small" :type="dispatchTag(x.status)" style="margin-left:8px">
                {{ DISPATCH_STATUS[x.status] }}
              </el-tag>
              <el-tag v-if="x.neighborReported" size="small"
                      :type="x.neighborInfoClear ? 'success' : 'danger'" style="margin-left:6px">
                邻居代报·{{ x.neighborInfoClear ? '信息清晰' : '信息不清' }}
              </el-tag>
              <div class="muted" style="margin-top:4px">
                通知人 {{ x.notifiedByName }} · {{ x.notifyChannel }}
                <el-button v-if="x.status !== 'ARRIVED' && x.status !== 'REJECTED'" link type="primary"
                           size="small" style="margin-left:10px"
                           @click="changeDispatch(x.id, 'ACCEPTED')">受理</el-button>
                <el-button v-if="x.status === 'ACCEPTED' || x.status === 'NOTIFIED'" link type="success"
                           size="small" @click="changeDispatch(x.id, 'ARRIVED')">到场</el-button>
                <el-button v-if="x.status !== 'REJECTED' && x.status !== 'ARRIVED'" link type="danger"
                           size="small" @click="changeDispatch(x.id, 'REJECTED')">无法响应</el-button>
              </div>
              <div v-if="x.note" class="muted">{{ x.note }}</div>
            </el-timeline-item>
          </el-timeline>
        </el-card>

        <el-card shadow="never" style="margin-bottom:12px">
          <template #header><span class="section-title">上门核实记录（{{ d.visits?.length || 0 }}）</span></template>
          <el-empty v-if="!d.visits?.length" :image-size="50" description="尚未上门" />
          <div v-for="v in d.visits" :key="v.id" class="record-box">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="入户方式">
                {{ v.entryMethod }}
                <el-tag v-if="!v.entered" type="danger" size="small">未能入户</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="到场时间">{{ fmt(v.arrivedAt) }}（{{ v.visitorName }}）</el-descriptions-item>
              <el-descriptions-item label="老人状态" :span="2">{{ v.elderStatus }}</el-descriptions-item>
              <el-descriptions-item label="联系家属">
                <el-tag size="small" :type="v.familyContacted ? 'success' : 'info'">
                  {{ v.familyContacted ? '已联系' : '未联系' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="处置判断">
                <el-tag v-if="v.hospitalNeeded" type="danger" size="small">需送医</el-tag>
                <el-tag v-if="v.refuseMedical" type="danger" size="small" effect="plain">拒绝就医</el-tag>
                <el-tag v-if="v.temporaryCareNeeded" type="warning" size="small">需临时照护</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="后续服务建议" :span="2">{{ v.followServiceSuggestions || '—' }}</el-descriptions-item>
              <el-descriptions-item label="家中安全隐患" :span="2">
                <span :style="{ color: v.safetyHazards ? '#f56c6c' : '' }">{{ v.safetyHazards || '无' }}</span>
              </el-descriptions-item>
              <el-descriptions-item v-if="v.photoUrls" label="现场照片" :span="2">
                <el-image v-for="(u, i) in v.photoUrls.split(',').filter(Boolean)" :key="i"
                          :src="u" :preview-src-list="v.photoUrls.split(',')" fit="cover"
                          style="width:88px;height:88px;border-radius:6px;margin-right:8px"
                          preview-teleported />
                <span class="muted">（演示环境为示例路径）</span>
              </el-descriptions-item>
              <el-descriptions-item v-if="v.note" label="备注" :span="2">{{ v.note }}</el-descriptions-item>
            </el-descriptions>
          </div>
        </el-card>

        <!-- 拒绝就医 · 风险随访 -->
        <el-card shadow="never" style="margin-bottom:12px" v-if="d.refusals?.length">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span class="section-title" style="margin:0;color:#c45656">拒绝就医 · 风险随访（{{ d.refusals.length }}）</span>
              <span class="muted">随访解除前事件不能归档</span>
            </div>
          </template>
          <div v-for="item in d.refusals" :key="item.refusal.id" class="record-box">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="随访状态">
                <el-tag size="small" :type="REFUSAL_STATUS_TAG[item.refusal.status]" effect="dark">
                  {{ REFUSAL_STATUS_LABEL[item.refusal.status] }}
                </el-tag>
                <el-tag size="small" :type="RISK_LEVEL_TAG[item.refusal.riskLevel]" effect="dark" style="margin-left:6px">
                  拒医风险 {{ RISK_LEVEL_LABEL[item.refusal.riskLevel] }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="判断医生">{{ item.refusal.doctorName }}（登记：{{ item.refusal.createdByName }} · {{ fmt(item.refusal.createdAt) }}）</el-descriptions-item>
              <el-descriptions-item label="异常发现" :span="2">
                <span style="color:#f56c6c">{{ item.refusal.abnormalFinding || '—' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="医生建议" :span="2">{{ item.refusal.doctorAdvice }}</el-descriptions-item>
              <el-descriptions-item label="老人拒绝原因" :span="2">{{ item.refusal.refuseReason }}</el-descriptions-item>
              <el-descriptions-item label="家属知情">
                <el-tag size="small" :type="item.refusal.familyInformed ? 'success' : 'danger'">
                  {{ item.refusal.familyInformed ? '已知情' : '不知情' }}
                </el-tag>
                <span v-if="item.refusal.familyInformed" class="muted" style="margin-left:6px">
                  态度：{{ item.refusal.familyAttitude || '未记录' }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="替代照护安排">{{ item.refusal.alternativeCare || '—' }}</el-descriptions-item>
            </el-descriptions>

            <!-- 复访任务 -->
            <div style="margin:8px 0 4px;display:flex;justify-content:space-between;align-items:center">
              <b style="font-size:13px">24 小时复访任务</b>
              <el-dropdown v-if="item.refusal.status === 'OPEN'" @command="t => addTask(item.refusal.id, t)">
                <el-button size="small" :icon="Plus">追加复访方式</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="(v, k) in FOLLOW_TASK_TYPE_LABEL" :key="k" :command="k">{{ v }}</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <el-table :data="item.tasks" size="small">
              <el-table-column label="轮次" width="60" prop="roundNo" />
              <el-table-column label="复访方式" width="100">
                <template #default="{ row }">
                  <el-tag size="small" effect="plain">{{ FOLLOW_TASK_TYPE_LABEL[row.taskType] }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="截止时间" width="150">
                <template #default="{ row }">
                  <span :style="{ color: isOverdue(row) ? '#f56c6c' : '', fontWeight: isOverdue(row) ? 700 : 400 }">
                    {{ fmt(row.dueAt) }}
                  </span>
                  <el-tag v-if="isOverdue(row)" type="danger" size="small" style="margin-left:4px">已逾期</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 'PENDING' ? 'warning' : row.status === 'DONE' ? 'success' : 'info'">
                    {{ FOLLOW_TASK_STATUS_LABEL[row.status] }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="复访结果" min-width="180">
                <template #default="{ row }">
                  <template v-if="row.result">
                    <el-tag size="small" :type="FOLLOW_TASK_RESULT_TAG[row.result]" effect="dark">
                      {{ FOLLOW_TASK_RESULT_LABEL[row.result] }}
                    </el-tag>
                    <span class="muted" style="margin-left:6px">{{ row.completedByName }} · {{ fmt(row.completedAt) }}</span>
                    <div v-if="row.resultNote" class="muted">{{ row.resultNote }}</div>
                  </template>
                  <el-button v-else-if="row.status === 'PENDING'" type="primary" size="small"
                             @click="openComplete(row)">完成复访</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-card shadow="never" style="margin-bottom:12px">
              <template #header><span class="section-title">后续服务（{{ d.services?.length || 0 }}）</span></template>
              <el-empty v-if="!d.services?.length" :image-size="40" description="无" />
              <el-table v-else :data="d.services" size="small">
                <el-table-column label="类型" width="100">
                  <template #default="{ row }">{{ SERVICE_LABEL[row.serviceType] }}</template>
                </el-table-column>
                <el-table-column prop="providerName" label="提供方" />
                <el-table-column prop="content" label="内容" show-overflow-tooltip />
              </el-table>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card shadow="never" style="margin-bottom:12px">
              <template #header><span class="section-title">家属沟通（{{ d.communications?.length || 0 }}）</span></template>
              <el-empty v-if="!d.communications?.length" :image-size="40" description="无" />
              <div v-for="c in d.communications" :key="c.id" style="margin-bottom:8px">
                <el-tag size="small" :type="c.reached ? 'success' : 'danger'">
                  {{ c.reached ? '已接通' : '未接通' }}
                </el-tag>
                <b style="margin:0 6px">{{ c.contactName }}</b>
                <span class="muted">{{ c.attitude }} · {{ fmt(c.communicatedAt) }}</span>
                <div v-if="c.note" class="muted" style="padding-left:4px">{{ c.note }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-card shadow="never" v-if="d.followUps?.length">
          <template #header><span class="section-title">回访记录</span></template>
          <div v-for="f in d.followUps" :key="f.id" class="record-box">
            <el-tag :type="f.result === 'IMPROVED' ? 'success' : f.result === 'WORSENED' ? 'danger' : 'info'">
              {{ FOLLOW_RESULT_LABEL[f.result] }}
            </el-tag>
            <span style="margin:0 8px">{{ f.method }} · 满意度 {{ f.satisfaction || '—' }}/5 · {{ fmt(f.followedUpAt) }}</span>
            <el-tag type="warning" effect="plain" v-if="f.nextCareType">
              下一次关怀：{{ SERVICE_LABEL[f.nextCareType] }} · {{ fmt(f.nextCareAt) }}
            </el-tag>
            <div v-if="f.note" class="muted" style="margin-top:4px">{{ f.note }}</div>
          </div>
        </el-card>
      </el-col>

      <!-- 右：老人画像 + 时间线 -->
      <el-col :span="7">
        <el-card shadow="never" style="margin-bottom:12px">
          <template #header>
            <div style="display:flex;justify-content:space-between;align-items:center">
              <span class="section-title" style="margin:0">老人照护画像</span>
              <el-link type="primary" @click="$router.push(`/elders/${d.elder?.id}`)">完整档案</el-link>
            </div>
          </template>
          <template v-if="d.elder">
            <div class="elder-head">
              <el-avatar :size="46" style="background:#c45656">{{ d.elder.name?.[0] }}</el-avatar>
              <div>
                <div style="font-weight:700;font-size:16px">
                  {{ d.elder.name }}
                  <el-tag size="small" :type="RISK_LEVEL_TAG[d.elder.riskLevel]" effect="dark">
                    {{ RISK_LEVEL_LABEL[d.elder.riskLevel] }}
                  </el-tag>
                </div>
                <div class="muted">{{ d.elder.gender }} · {{ d.elder.age }}岁 · {{ d.elder.livingStatus }}</div>
              </div>
            </div>
            <el-descriptions :column="1" size="small" border>
              <el-descriptions-item label="基础病">{{ d.elder.chronicDiseases || '无' }}</el-descriptions-item>
              <el-descriptions-item label="用药备注">{{ d.elder.medicalNote || '—' }}</el-descriptions-item>
              <el-descriptions-item label="住址">{{ d.elder.address }}</el-descriptions-item>
              <el-descriptions-item label="门禁信息">
                <span style="color:#b88230">{{ d.elder.doorAccessInfo || '无' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="安全设施">
                <el-icon :color="d.elder.gasAlarmInstalled ? '#67c23a' : '#f56c6c'">
                  <CircleCheck v-if="d.elder.gasAlarmInstalled" /><CircleClose v-else />
                </el-icon> 燃气报警器
                <el-icon style="margin-left:10px" v-if="d.elder.smokeAlarmInstalled" color="#67c23a"><CircleCheck /></el-icon> 烟感
                <el-icon style="margin-left:10px" v-if="d.elder.sosDeviceInstalled" color="#67c23a"><CircleCheck /></el-icon> SOS设备
              </el-descriptions-item>
              <el-descriptions-item label="最近家属探访">{{ d.elder.lastFamilyVisitDate || '无记录' }}</el-descriptions-item>
              <el-descriptions-item label="医生随访">{{ d.elder.lastDoctorFollowUpDate || '无记录' }}</el-descriptions-item>
              <el-descriptions-item label="随访结论">{{ d.elder.doctorFollowUpConclusion || '—' }}</el-descriptions-item>
              <el-descriptions-item label="家属响应评分">
                <el-rate :model-value="Math.round((d.elder.familyResponsivenessScore||0)/20)" disabled size="small" />
                {{ d.elder.familyResponsivenessScore }}
              </el-descriptions-item>
              <el-descriptions-item label="邻里可信度">
                <el-rate :model-value="Math.round((d.elder.neighborReliabilityScore||0)/20)" disabled size="small" />
                {{ d.elder.neighborReliabilityScore }}
              </el-descriptions-item>
              <el-descriptions-item label="历史误触">{{ d.elder.falseTriggerCount }} 次</el-descriptions-item>
              <el-descriptions-item label="服务建议">
                <el-tag v-if="d.elder.suggestDailyVisit" size="small" type="danger" effect="plain">巡访</el-tag>
                <el-tag v-if="d.elder.suggestMealService" size="small" type="warning" effect="plain">助餐</el-tag>
                <el-tag v-if="d.elder.suggestAgingRetrofit" size="small" type="info" effect="plain">适老化改造</el-tag>
                <el-tag v-if="d.elder.suggestPsychCompanion" size="small" type="success" effect="plain">心理陪伴</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="下次主动关怀">
                {{ d.elder.proactiveCareType || '—' }}
                <span class="muted" v-if="d.elder.nextProactiveCareAt">{{ fmt(d.elder.nextProactiveCareAt) }}</span>
              </el-descriptions-item>
            </el-descriptions>

            <div style="margin-top:10px">
              <div class="muted" style="margin-bottom:4px">紧急联系人</div>
              <div v-for="c in d.contacts" :key="c.id" class="person-row">
                <el-tag size="small" :type="c.primaryContact ? 'danger' : 'info'" effect="plain">
                  {{ c.relationship }}
                </el-tag>
                <b style="margin:0 6px">{{ c.name }}</b>
                <span class="muted">{{ c.phone }}</span>
                <el-tag v-if="c.oftenUnreachable" size="small" type="danger" style="margin-left:auto">长期不接</el-tag>
              </div>
            </div>
            <div style="margin-top:10px">
              <div class="muted" style="margin-bottom:4px">邻里响应人</div>
              <div v-for="n in d.neighbors" :key="n.id" class="person-row">
                <el-icon color="#409eff"><UserFilled /></el-icon>
                <b style="margin:0 6px">{{ n.name }}</b>
                <span class="muted">{{ n.phone }} · {{ n.proximity }}</span>
                <el-tag size="small" type="success" effect="plain" style="margin-left:auto">可信度 {{ n.reliabilityScore }}</el-tag>
              </div>
            </div>
          </template>
        </el-card>

        <el-card shadow="never">
          <template #header><span class="section-title">事件全流程时间线</span></template>
          <el-timeline class="timeline-dense">
            <el-timeline-item v-for="l in d.logs" :key="l.id" :timestamp="fmt(l.createdAt)"
                              :type="LOG_TYPE[l.type] || 'info'" size="large">
              <el-tag size="small" effect="plain" style="margin-right:6px">{{ LOG_LABEL[l.type] || l.type }}</el-tag>
              <span style="font-size:13px">{{ l.content }}</span>
              <div class="muted">{{ l.operatorName }}</div>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>

    <!-- ===== 研判 ===== -->
    <el-dialog v-model="triageDialog" title="值班社工研判" width="520px">
      <el-form :model="triage" label-width="92px">
        <el-form-item label="研判类型">
          <el-radio-group v-model="triage.triageType">
            <el-radio v-for="(v, k) in TRIAGE_LABEL" :key="k" :value="k">{{ v }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="严重程度">
          <el-radio-group v-model="triage.severity">
            <el-radio v-for="(v, k) in SEVERITY_LABEL" :key="k" :value="k">{{ v }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="研判说明">
          <el-input v-model="triage.triageNote" type="textarea" :rows="3"
                    placeholder="结合语音内容、档案基础病、历史求助与误触记录综合判断" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="triageDialog = false">取消</el-button>
        <el-button type="danger" @click="submitTriage">提交研判</el-button>
      </template>
    </el-dialog>

    <!-- ===== 转介 ===== -->
    <el-dialog v-model="dispatchDialog" title="转介 / 通知" width="540px">
      <el-form :model="dispatch" label-width="100px">
        <el-form-item label="通知对象">
          <el-radio-group v-model="dispatch.targetType" @change="onTargetChange">
            <el-radio v-for="(v, k) in TARGET_LABEL" :key="k" :value="k">{{ v }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="dispatch.targetName" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="dispatch.targetPhone" />
        </el-form-item>
        <el-form-item label="通知方式">
          <el-select v-model="dispatch.notifyChannel" style="width:100%">
            <el-option label="电话" value="电话" />
            <el-option label="平台推送" value="平台推送" />
            <el-option label="短信" value="短信" />
          </el-select>
        </el-form-item>
        <el-form-item label="邻居代报">
          <el-switch v-model="dispatch.neighborReported" />
        </el-form-item>
        <el-form-item v-if="dispatch.neighborReported" label="代报信息">
          <el-radio-group v-model="dispatch.neighborInfoClear">
            <el-radio :value="true">信息清晰</el-radio>
            <el-radio :value="false">信息不清</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="dispatch.note" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchDialog = false">取消</el-button>
        <el-button type="warning" @click="submitDispatch">发出通知</el-button>
      </template>
    </el-dialog>

    <!-- ===== 上门 ===== -->
    <el-dialog v-model="onsiteDialog" title="上门核实记录" width="640px" top="5vh">
      <el-form :model="onsite" label-width="110px">
        <el-form-item label="入户方式">
          <el-select v-model="onsite.entryMethod" style="width:100%">
            <el-option v-for="m in ENTRY_METHODS" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否入户">
          <el-switch v-model="onsite.entered" active-text="已入户" inactive-text="未能入户" />
        </el-form-item>
        <el-form-item label="现场照片">
          <el-upload :action="uploadUrl" :headers="uploadHeaders" name="file"
                     :on-success="onUploaded" :show-file-list="true" accept="image/*">
            <el-button :icon="Picture">上传现场照片</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="老人状态">
          <el-select v-model="onsite.elderStatus" style="width:100%" allow-create filterable>
            <el-option v-for="s in ELDER_STATUS_OPTIONS" :key="s" :label="s" :value="s" />
          </el-select>
        </el-form-item>
        <el-row>
          <el-col :span="12"><el-form-item label="联系家属"><el-switch v-model="onsite.familyContacted" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="需要送医"><el-switch v-model="onsite.hospitalNeeded" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="拒绝就医"><el-switch v-model="onsite.refuseMedical" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="临时照护"><el-switch v-model="onsite.temporaryCareNeeded" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="后续服务建议">
          <el-checkbox-group v-model="onsite.suggestions">
            <el-checkbox value="日常巡访">日常巡访</el-checkbox>
            <el-checkbox value="助餐">助餐</el-checkbox>
            <el-checkbox value="适老化改造">适老化改造</el-checkbox>
            <el-checkbox value="心理陪伴">心理陪伴</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="家中安全隐患">
          <el-input v-model="onsite.safetyHazards" type="textarea" :rows="2"
                    placeholder="如：地滑、电线老化、燃气隐患、照明不足、无扶手等" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="onsite.note" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="onsiteDialog = false">取消</el-button>
        <el-button type="primary" @click="submitOnsite">提交上门记录</el-button>
      </template>
    </el-dialog>

    <!-- ===== 家属沟通 ===== -->
    <el-dialog v-model="commDialog" title="家属沟通记录" width="500px">
      <el-form :model="comm" label-width="92px">
        <el-form-item label="联系人">
          <el-select v-model="comm.contactId" style="width:100%" @change="onContactChange">
            <el-option v-for="c in d.contacts" :key="c.id"
                       :label="`${c.relationship} ${c.name} ${c.phone}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="姓名"><el-input v-model="comm.contactName" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="comm.contactPhone" /></el-form-item>
        <el-form-item label="方式">
          <el-select v-model="comm.channel" style="width:100%">
            <el-option label="电话" value="电话" />
            <el-option label="短信" value="短信" />
            <el-option label="微信" value="微信" />
            <el-option label="当面" value="当面" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否接通">
          <el-switch v-model="comm.reached" active-text="已接通" inactive-text="未接通" />
        </el-form-item>
        <el-form-item v-if="comm.reached" label="家属态度">
          <el-radio-group v-model="comm.attitude">
            <el-radio value="积极配合">积极配合</el-radio>
            <el-radio value="一般">一般</el-radio>
            <el-radio value="推诿拒绝">推诿拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="comm.note" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="commDialog = false">取消</el-button>
        <el-button type="info" @click="submitComm">保存沟通记录</el-button>
      </template>
    </el-dialog>

    <!-- ===== 后续服务 ===== -->
    <el-dialog v-model="serviceDialog" title="后续服务安排" width="500px">
      <el-form :model="service" label-width="92px">
        <el-form-item label="服务类型">
          <el-select v-model="service.serviceType" style="width:100%">
            <el-option v-for="(v, k) in SERVICE_LABEL" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="提供方"><el-input v-model="service.providerName" placeholder="如 康泰养老服务中心 / 助餐点" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="service.providerPhone" /></el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="service.status">
            <el-radio value="PENDING">待服务</el-radio>
            <el-radio value="IN_PROGRESS">进行中</el-radio>
            <el-radio value="DONE">已完成</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="服务内容"><el-input v-model="service.content" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="serviceDialog = false">取消</el-button>
        <el-button type="success" @click="submitService">保存服务</el-button>
      </template>
    </el-dialog>

    <!-- ===== 回访归档 ===== -->
    <el-dialog v-model="followDialog" title="回访并归档（联动更新老人画像与关怀计划）" width="540px">
      <el-alert type="info" :closable="false" style="margin-bottom:12px"
                title="归档后将更新老人风险等级、家属响应表现、邻里协助可信度，并生成下一次主动关怀计划" />
      <el-form :model="follow" label-width="100px">
        <el-form-item label="回访方式">
          <el-radio-group v-model="follow.method">
            <el-radio value="电话回访">电话回访</el-radio>
            <el-radio value="上门回访">上门回访</el-radio>
            <el-radio value="小程序">小程序</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="回访结果">
          <el-radio-group v-model="follow.result">
            <el-radio v-for="(v, k) in FOLLOW_RESULT_LABEL" :key="k" :value="k">{{ v }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="满意度">
          <el-rate v-model="follow.satisfaction" />
        </el-form-item>
        <el-form-item label="回访备注">
          <el-input v-model="follow.note" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="关怀类型">
          <el-select v-model="follow.nextCareType" clearable placeholder="留空则系统按风险自动排期" style="width:100%">
            <el-option v-for="(v, k) in SERVICE_LABEL" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="关怀时间">
          <el-date-picker v-model="follow.nextCareAt" type="datetime" placeholder="留空则自动排期"
                          value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="followDialog = false">取消</el-button>
        <el-button type="danger" @click="submitFollow">完成回访并归档</el-button>
      </template>
    </el-dialog>

    <!-- ===== 拒医登记 ===== -->
    <el-dialog v-model="refusalDialog" title="拒绝就医登记（登记后生成 24 小时复访任务，随访解除前事件不能归档）" width="600px" top="4vh">
      <el-alert type="warning" :closable="false" style="margin-bottom:12px"
                title="家庭医生判断老人身体异常建议就医，但老人拒绝。请完整记录医生建议、拒绝原因、家属知情情况与替代照护安排" />
      <el-form :model="refusal" label-width="100px">
        <el-form-item label="判断医生" required>
          <el-input v-model="refusal.doctorName" placeholder="如 钱医生" />
        </el-form-item>
        <el-form-item label="异常发现">
          <el-input v-model="refusal.abnormalFinding" placeholder="如 血压 182/110 mmHg，伴头晕、心悸" />
        </el-form-item>
        <el-form-item label="医生建议" required>
          <el-input v-model="refusal.doctorAdvice" type="textarea" :rows="2"
                    placeholder="如 建议立即前往急诊观察，控制血压，排除心脑血管意外" />
        </el-form-item>
        <el-form-item label="拒绝原因" required>
          <el-input v-model="refusal.refuseReason" type="textarea" :rows="2"
                    placeholder="如 害怕住院 / 费用顾虑 / 自觉休息即可 / 无人陪同" />
        </el-form-item>
        <el-form-item label="家属知情">
          <el-switch v-model="refusal.familyInformed" active-text="已知情" inactive-text="不知情" />
        </el-form-item>
        <el-form-item v-if="refusal.familyInformed" label="家属态度">
          <el-radio-group v-model="refusal.familyAttitude">
            <el-radio v-for="a in FAMILY_ATTITUDE_OPTIONS" :key="a" :value="a">{{ a }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="替代照护安排">
          <el-input v-model="refusal.alternativeCare" type="textarea" :rows="2"
                    placeholder="如 居家观察，每 4 小时测压；家庭医生每日两次电话随访；社区护士明早上门测压" />
        </el-form-item>
        <el-form-item label="首轮复访方式">
          <el-radio-group v-model="refusal.followTaskType">
            <el-radio v-for="(v, k) in FOLLOW_TASK_TYPE_LABEL" :key="k" :value="k">{{ v }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refusalDialog = false">取消</el-button>
        <el-button type="danger" @click="submitRefusal">登记并生成复访任务</el-button>
      </template>
    </el-dialog>

    <!-- ===== 完成复访 ===== -->
    <el-dialog v-model="completeDialog" :title="`完成复访（${FOLLOW_TASK_TYPE_LABEL[complete.taskType] || ''} · 第${complete.roundNo}轮）`" width="520px">
      <el-alert v-if="complete.result === 'WORSENED'" type="error" :closable="false" style="margin-bottom:12px"
                title="症状加重：提交后系统将提升拒医/事件/老人风险等级，自动重新通知家属和家庭医生，并生成下一轮 24 小时复访任务" />
      <el-alert v-else-if="complete.result === 'UNCHANGED' || complete.result === 'NO_ANSWER'" type="warning"
                :closable="false" style="margin-bottom:12px"
                title="症状未缓解：拒医记录保持随访中，将生成下一轮复访任务；老人再次求助时本记录会自动带入新事件" />
      <el-form :model="complete" label-width="110px">
        <el-form-item label="复访结果" required>
          <el-radio-group v-model="complete.result">
            <el-radio v-for="(v, k) in FOLLOW_TASK_RESULT_LABEL" :key="k" :value="k">{{ v }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复访说明">
          <el-input v-model="complete.note" type="textarea" :rows="3"
                    placeholder="如 复测血压 165/98，仍头晕；老人仍拒绝去医院" />
        </el-form-item>
        <el-form-item v-if="complete.result !== 'IMPROVED'" label="下一轮复访方式">
          <el-radio-group v-model="complete.nextTaskType">
            <el-radio v-for="(v, k) in FOLLOW_TASK_TYPE_LABEL" :key="k" :value="k">{{ v }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="completeDialog = false">取消</el-button>
        <el-button :type="complete.result === 'WORSENED' ? 'danger' : 'primary'" @click="submitComplete">提交复访结果</el-button>
      </template>
    </el-dialog>

    <!-- 风险标记 / 备注 -->
    <el-dialog v-model="flagDialog" title="补充风险标记" width="420px">
      <el-radio-group v-model="manualFlag" style="display:flex;flex-direction:column;gap:8px">
        <el-radio v-for="(v, k) in RISK_LABEL" :key="k" :value="k">{{ v }}</el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="flagDialog = false">取消</el-button>
        <el-button type="danger" @click="submitFlag">添加标记</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="noteDialog" title="添加事件备注" width="440px">
      <el-input v-model="noteContent" type="textarea" :rows="3" placeholder="其他需要留痕的信息" />
      <template #footer>
        <el-button @click="noteDialog = false">取消</el-button>
        <el-button type="primary" @click="submitNote">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Search, Phone, House, ChatDotRound, Files, CircleCheck, CircleClose,
  EditPen, Picture, UserFilled, FirstAidKit
} from '@element-plus/icons-vue'
import api from '../api'
import {
  SOURCE_LABEL, TRIAGE_LABEL, SEVERITY_LABEL, SEVERITY_TAG, TARGET_LABEL,
  SERVICE_LABEL, FOLLOW_RESULT_LABEL, RISK_LABEL, RISK_TAG, STATUS_LABEL, STATUS_TAG,
  RISK_LEVEL_LABEL, RISK_LEVEL_TAG, ENTRY_METHODS, ELDER_STATUS_OPTIONS,
  FOLLOW_TASK_TYPE_LABEL, FOLLOW_TASK_RESULT_LABEL, FOLLOW_TASK_RESULT_TAG,
  REFUSAL_STATUS_LABEL, REFUSAL_STATUS_TAG, FOLLOW_TASK_STATUS_LABEL, FAMILY_ATTITUDE_OPTIONS
} from '../api/dict'

const route = useRoute()
const d = ref({})
const loading = ref(false)

const DISPATCH_STATUS = { NOTIFIED: '已通知', ACCEPTED: '已受理', ARRIVED: '已到场', REJECTED: '无法响应' }
const LOG_LABEL = {
  HELP: '求助', TRIAGE: '研判', DISPATCH: '转介', ONSITE: '上门', SERVICE: '服务',
  FAMILY_COMM: '家属沟通', FOLLOW_UP: '回访', CLOSE: '归档', SYSTEM: '系统', NOTE: '备注',
  REFUSAL: '拒医', FOLLOW_TASK: '复访'
}
const LOG_TYPE = {
  HELP: 'danger', TRIAGE: 'warning', DISPATCH: 'warning', ONSITE: 'primary',
  SERVICE: 'success', FAMILY_COMM: 'info', FOLLOW_UP: 'success', CLOSE: 'success',
  SYSTEM: 'info', NOTE: 'info', REFUSAL: 'danger', FOLLOW_TASK: 'warning'
}

const flags = computed(() => (d.value.event?.riskFlags || '').split(',').filter(Boolean))
const hasOpenRefusal = computed(() => (d.value.refusals || []).some(x => x.refusal.status === 'OPEN'))
const uploadUrl = '/api/files/upload'
const uploadHeaders = { Authorization: `Bearer ${localStorage.getItem('token')}` }

async function load() {
  loading.value = true
  try {
    d.value = await api.get(`/events/${route.params.id}`)
  } finally {
    loading.value = false
  }
}
function fmt(s) { return s ? String(s).replace('T', ' ').substring(0, 16) : '' }
function dispatchTag(s) {
  return { NOTIFIED: 'info', ACCEPTED: 'warning', ARRIVED: 'success', REJECTED: 'danger' }[s] || 'info'
}

/* ---- 表单状态 ---- */
const triageDialog = ref(false)
const triage = reactive({ triageType: 'BODY_DISCOMFORT', severity: 'MEDIUM', triageNote: '' })
function openTriage() { Object.assign(triage, { triageType: 'BODY_DISCOMFORT', severity: 'MEDIUM', triageNote: '' }); triageDialog.value = true }
async function submitTriage() {
  await api.post(`/events/${route.params.id}/triage`, triage)
  ElMessage.success('研判已提交')
  triageDialog.value = false
  load()
}

const dispatchDialog = ref(false)
const dispatch = reactive({ targetType: 'GRID_WORKER', targetName: '', targetPhone: '', notifyChannel: '电话', neighborReported: false, neighborInfoClear: true, note: '' })
function openDispatch() {
  Object.assign(dispatch, { targetType: 'GRID_WORKER', targetName: '', targetPhone: '', notifyChannel: '电话', neighborReported: false, neighborInfoClear: true, note: '' })
  dispatchDialog.value = true
}
function onTargetChange(t) {
  if (t === 'NEIGHBOR_VOLUNTEER' && d.value.neighbors?.[0]) {
    dispatch.targetName = d.value.neighbors[0].name
    dispatch.targetPhone = d.value.neighbors[0].phone
  }
}
async function submitDispatch() {
  if (!dispatch.targetName) { ElMessage.warning('请填写姓名'); return }
  await api.post(`/events/${route.params.id}/dispatches`, dispatch)
  ElMessage.success('已通知')
  dispatchDialog.value = false
  load()
}
async function changeDispatch(id, status) {
  await api.put(`/events/dispatches/${id}/status`, null, { params: { status } })
  load()
}

const onsiteDialog = ref(false)
const onsite = reactive({})
function openOnsite() {
  Object.assign(onsite, {
    entryMethod: d.value.elder?.doorAccessInfo ? '智能锁授权' : '钥匙开门',
    entered: true, photoUrls: '', elderStatus: '清醒正常',
    familyContacted: false, hospitalNeeded: false, refuseMedical: false,
    temporaryCareNeeded: false, suggestions: [], safetyHazards: '', note: ''
  })
  onsiteDialog.value = true
}
function onUploaded(res) {
  if (res.code === 0) {
    onsite.photoUrls = onsite.photoUrls ? onsite.photoUrls + ',' + res.data.url : res.data.url
    ElMessage.success('照片已上传')
  }
}
async function submitOnsite() {
  if (!onsite.elderStatus) { ElMessage.warning('请填写老人状态'); return }
  await api.post(`/events/${route.params.id}/onsite`, {
    ...onsite,
    followServiceSuggestions: onsite.suggestions.join(',')
  })
  ElMessage.success('上门记录已提交')
  onsiteDialog.value = false
  load()
}

const commDialog = ref(false)
const comm = reactive({})
function openComm() {
  const primary = d.value.contacts?.find(c => c.primaryContact) || d.value.contacts?.[0]
  Object.assign(comm, {
    contactId: primary?.id || null, contactName: primary?.name || '',
    contactPhone: primary?.phone || '', channel: '电话', reached: true, attitude: '积极配合', note: ''
  })
  commDialog.value = true
}
function onContactChange(id) {
  const c = d.value.contacts.find(x => x.id === id)
  if (c) { comm.contactName = c.name; comm.contactPhone = c.phone }
}
async function submitComm() {
  if (!comm.contactName) { ElMessage.warning('请填写联系人'); return }
  await api.post(`/events/${route.params.id}/family-communications`, comm)
  ElMessage.success('沟通记录已保存')
  commDialog.value = false
  load()
}

const serviceDialog = ref(false)
const service = reactive({})
function openService() {
  Object.assign(service, { serviceType: 'DAILY_VISIT', providerName: '', providerPhone: '', status: 'DONE', content: '' })
  serviceDialog.value = true
}
async function submitService() {
  if (!service.providerName) { ElMessage.warning('请填写提供方'); return }
  await api.post(`/events/${route.params.id}/services`, service)
  ElMessage.success('服务已保存')
  serviceDialog.value = false
  load()
}

const followDialog = ref(false)
const follow = reactive({})
function openFollow() {
  Object.assign(follow, { result: 'STABLE', method: '电话回访', satisfaction: 4, note: '', nextCareType: null, nextCareAt: null })
  followDialog.value = true
}
async function submitFollow() {
  await ElMessageBox.confirm('确认完成回访并归档？归档将联动更新老人画像', '归档确认', { type: 'warning' })
  try {
    await api.post(`/events/${route.params.id}/follow-up`, follow)
    ElMessage.success('事件已归档，画像与关怀计划已更新')
    followDialog.value = false
    load()
  } catch (e) { /* 后端拦截（如存在随访中的拒医记录）时错误信息已由拦截器提示 */ }
}

/* ---- 拒医登记与复访 ---- */
const refusalDialog = ref(false)
const refusal = reactive({})
function openRefusal() {
  Object.assign(refusal, {
    doctorName: '钱医生', abnormalFinding: '', doctorAdvice: '', refuseReason: '',
    familyInformed: false, familyAttitude: '积极配合', alternativeCare: '', followTaskType: 'PHONE'
  })
  refusalDialog.value = true
}
async function submitRefusal() {
  if (!refusal.doctorName) { ElMessage.warning('请填写判断医生'); return }
  if (!refusal.doctorAdvice) { ElMessage.warning('请填写医生建议'); return }
  if (!refusal.refuseReason) { ElMessage.warning('请填写老人拒绝原因'); return }
  await api.post(`/events/${route.params.id}/refusals`, refusal)
  ElMessage.success('拒医记录已登记，24 小时复访任务已生成')
  refusalDialog.value = false
  load()
}

const completeDialog = ref(false)
const complete = reactive({ id: null, taskType: '', roundNo: 1, result: 'IMPROVED', note: '', nextTaskType: 'PHONE' })
watch(() => complete.result, r => {
  // 加重默认升级为上门复访，其余默认电话复访（可手动调整）
  complete.nextTaskType = r === 'WORSENED' ? 'VISIT' : 'PHONE'
})
function openComplete(task) {
  Object.assign(complete, {
    id: task.id, taskType: task.taskType, roundNo: task.roundNo,
    result: 'IMPROVED', note: '', nextTaskType: 'PHONE'
  })
  completeDialog.value = true
}
async function submitComplete() {
  await api.post(`/refusal-follow-tasks/${complete.id}/complete`, {
    result: complete.result, note: complete.note, nextTaskType: complete.nextTaskType
  })
  if (complete.result === 'IMPROVED') ElMessage.success('症状缓解，拒医随访已解除，事件可回访归档')
  else if (complete.result === 'WORSENED') ElMessage.warning('已提升风险等级并重新通知家属与家庭医生')
  else ElMessage.info('已生成下一轮 24 小时复访任务')
  completeDialog.value = false
  load()
}

async function addTask(refusalId, taskType) {
  await api.post(`/refusals/${refusalId}/tasks`, null, { params: { taskType } })
  ElMessage.success(`已追加${FOLLOW_TASK_TYPE_LABEL[taskType]}任务`)
  load()
}

function isOverdue(task) {
  return task.status === 'PENDING' && task.dueAt && new Date(task.dueAt) < new Date()
}

const flagDialog = ref(false)
const manualFlag = ref('CANNOT_OPEN_DOOR')
async function submitFlag() {
  await api.post(`/events/${route.params.id}/flags`, null, { params: { flag: manualFlag.value } })
  flagDialog.value = false
  load()
}
const noteDialog = ref(false)
const noteContent = ref('')
async function submitNote() {
  if (!noteContent.value) return
  await api.post(`/events/${route.params.id}/notes`, { content: noteContent.value })
  noteDialog.value = false
  noteContent.value = ''
  load()
}

function onBus() { if (d.value.event && d.value.event.status !== 'CLOSED') load() }
onMounted(() => {
  load()
  window.addEventListener('workbench-reload', onBus)
})
</script>

<style scoped>
.record-box { margin-bottom: 10px; }
.elder-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.person-row { display: flex; align-items: center; gap: 2px; padding: 5px 0; font-size: 13px; border-bottom: 1px dashed #ebeef5; }
</style>
