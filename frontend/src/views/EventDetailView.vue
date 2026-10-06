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

    <!-- 拒医复访升级事件提示：携带原拒医记录/家属态度/医生建议，防止被当成普通求助 -->
    <el-alert v-if="d.event?.relationType === 'ESCALATED_FROM_REFUSAL'" type="error" show-icon
              :closable="false" style="margin-bottom:12px">
      <template #title>
        <b>本事件由拒医事件 {{ d.relatedEvent?.eventNo }} 复访后升级生成，不是一次新的普通求助</b>
      </template>
      <div style="margin-top:4px">
        系统已携带原拒医记录、家属态度与医生建议，风险等级提升至
        <el-tag size="small" type="danger" effect="dark">{{ SEVERITY_LABEL[d.event?.severity] }}</el-tag>
        并已重新通知家属与医疗人员。
        <el-button v-if="d.relatedEvent" link type="primary" size="small"
                   @click="$router.push(`/events/${d.relatedEvent.id}`)">查看原拒医事件 →</el-button>
      </div>
    </el-alert>
    <el-alert v-if="d.escalatedEvent" type="warning" show-icon :closable="false" style="margin-bottom:12px">
      <template #title>
        本拒医事件复访后症状未缓解/加重，已生成升级事件
        <el-button link type="primary" size="small"
                   @click="$router.push(`/events/${d.escalatedEvent.id}`)">
          {{ d.escalatedEvent.eventNo }} →
        </el-button>
      </template>
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
            <el-button type="danger" plain :icon="Warning" @click="openRefusal"
                       v-if="d.event?.triageType && d.event.triageType !== 'FALSE_TRIGGER' && !d.refusalBlocked">
              登记拒绝就医
            </el-button>
            <el-tooltip v-if="d.refusalBlocked" content="拒医复访未完成，事件不能关闭" placement="top">
              <span>
                <el-button type="danger" plain disabled>⑤ 回访归档</el-button>
              </span>
            </el-tooltip>
            <el-button v-if="!d.refusalBlocked" type="danger" plain :icon="CircleCheck" @click="openFollow">
              {{ d.event?.triageType === 'FALSE_TRIGGER' ? '② 回访归档' : '⑤ 回访归档' }}
            </el-button>
            <el-button :icon="EditPen" @click="noteDialog = true">添加备注</el-button>
          </el-space>
          <el-alert v-if="d.refusalBlocked" type="error" :closable="false" style="margin-top:10px"
                    title="该事件存在老人拒绝就医记录：24 小时内复访未完成，事件不能关闭。请在下方完成复访登记。" />
        </el-card>

        <!-- 拒医随访闭环 -->
        <el-card v-if="d.refusals?.length" shadow="never" style="margin-bottom:12px"
                 class="refusal-card">
          <template #header>
            <span class="section-title">
              拒绝就医与 24 小时复访
              <el-tag v-if="d.refusalBlocked" type="danger" size="small" effect="dark" style="margin-left:8px">
                随访中·禁止关闭
              </el-tag>
              <el-tag v-else type="success" size="small" style="margin-left:8px">随访已了结</el-tag>
            </span>
          </template>

          <div v-for="r in d.refusals" :key="r.id" class="record-box refusal-box">
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="异常体征" :span="2">
                <span style="color:#f56c6c;font-weight:600">{{ r.abnormalSign || '—' }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="家庭医生">
                <b>{{ r.doctorName }}</b>
              </el-descriptions-item>
              <el-descriptions-item label="登记时间">{{ fmt(r.recordedAt) }}（{{ r.recordedByName }}）</el-descriptions-item>
              <el-descriptions-item label="医生建议" :span="2">{{ r.doctorAdvice }}</el-descriptions-item>
              <el-descriptions-item label="老人拒绝原因" :span="2">{{ r.refuseReason }}</el-descriptions-item>
              <el-descriptions-item label="家属是否知情">
                <el-tag size="small" :type="r.familyInformed ? 'success' : 'danger'">
                  {{ r.familyInformed ? '已知情' : '不知情' }}
                </el-tag>
                <span v-if="r.familyInformed" style="margin-left:6px">
                  {{ r.familyName }} · {{ r.familyAttitude || '态度未记录' }}
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="随访结论">
                <el-tag v-if="r.resolved" size="small"
                        :type="r.resolveOutcome === 'RELIEVED' ? 'success' : 'danger'">
                  {{ REVISIT_RESULT_LABEL[r.resolveOutcome] }}
                </el-tag>
                <el-tag v-else size="small" type="warning">待复访</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="替代照护安排" :span="2">{{ r.alternativeCare || '无' }}</el-descriptions-item>
            </el-descriptions>
          </div>

          <el-divider content-position="left">复访任务</el-divider>
          <div v-for="t in d.revisitTasks" :key="t.id" class="revisit-row">
            <el-tag :type="REVISIT_TAG[t.status]" effect="plain">
              {{ REVISIT_TASK_STATUS[t.status] || t.status }}
            </el-tag>
            <el-tag size="small" type="warning" effect="dark" style="margin-left:6px">
              {{ REVISIT_METHOD_LABEL[t.method] }}
            </el-tag>
            <b style="margin:0 8px">责任人 {{ t.assigneeName || '—' }}</b>
            <span class="muted">截止 {{ fmt(t.dueAt) }}</span>
            <el-button v-if="t.status !== 'COMPLETED'" type="danger" size="small" style="margin-left:auto"
                       @click="openRevisit(t)">完成复访登记</el-button>
            <template v-else>
              <el-tag v-if="t.familyNotified" size="small" type="success" style="margin-left:auto">已重新通知家属</el-tag>
              <el-tag v-if="t.medicalNotified" size="small" type="danger" style="margin-left:6px">已重新通知医疗人员</el-tag>
            </template>
            <div style="width:100%;margin-top:4px">
              <span v-if="t.result" class="muted">
                复访结果：{{ REVISIT_RESULT_LABEL[t.result] }} · {{ fmt(t.completedAt) }} · {{ t.completedByName }}
              </span>
              <div v-if="t.note" class="muted">说明：{{ t.note }}</div>
            </div>
          </div>
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

    <!-- ===== 登记拒绝就医 ===== -->
    <el-dialog v-model="refusalDialog" title="登记老人拒绝就医（生成 24 小时内复访任务）" width="620px" top="5vh">
      <el-alert type="error" :closable="false" style="margin-bottom:12px"
                title="家庭医生判断体征异常但老人拒绝就医时登记。事件将进入“拒医随访中”，复访完成前不能关闭。" />
      <el-form :model="refusal" label-width="110px">
        <el-form-item label="异常体征">
          <el-input v-model="refusal.abnormalSign" placeholder="如 血压 185/110mmHg，头晕、胸闷" />
        </el-form-item>
        <el-form-item label="家庭医生">
          <el-input v-model="refusal.doctorName" placeholder="提出就医建议的医生姓名" />
        </el-form-item>
        <el-form-item label="医生建议">
          <el-input v-model="refusal.doctorAdvice" type="textarea" :rows="2"
                    placeholder="建议就诊科室、风险告知、用药监测要求等" />
        </el-form-item>
        <el-form-item label="老人拒绝原因">
          <el-input v-model="refusal.refuseReason" type="textarea" :rows="2"
                    placeholder="如 怕花钱 / 怕麻烦子女 / 自觉无大碍 / 行动不便" />
        </el-form-item>
        <el-form-item label="家属是否知情">
          <el-switch v-model="refusal.familyInformed" active-text="已知情" inactive-text="不知情" />
        </el-form-item>
        <template v-if="refusal.familyInformed">
          <el-form-item label="知情家属">
            <el-input v-model="refusal.familyName" placeholder="家属姓名" style="width:48%" />
            <el-select v-model="refusal.familyAttitude" placeholder="家属态度" style="width:48%;margin-left:4%">
              <el-option label="积极配合送医" value="积极配合送医" />
              <el-option label="尊重老人意愿" value="尊重老人意愿" />
              <el-option label="拒绝送医" value="拒绝送医" />
              <el-option label="无法联系" value="无法联系" />
            </el-select>
          </el-form-item>
        </template>
        <el-form-item label="替代照护安排">
          <el-input v-model="refusal.alternativeCare" type="textarea" :rows="2"
                    placeholder="未送医期间的用药、血压监测、陪护/邻里照看等安排" />
        </el-form-item>
        <el-form-item label="复访方式">
          <el-radio-group v-model="refusal.revisitMethod">
            <el-radio v-for="(v, k) in REVISIT_METHOD_LABEL" :key="k" :value="k">{{ v }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复访责任人">
          <el-input v-model="refusal.revisitAssignee" placeholder="留空默认当前操作人" />
        </el-form-item>
        <el-form-item label="复访截止">
          <el-date-picker v-model="refusal.revisitDueAt" type="datetime" placeholder="留空默认 24 小时内"
                          value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refusalDialog = false">取消</el-button>
        <el-button type="danger" @click="submitRefusal">保存并生成复访任务</el-button>
      </template>
    </el-dialog>

    <!-- ===== 完成拒医复访 ===== -->
    <el-dialog v-model="revisitDialog" title="完成拒医复访登记" width="560px">
      <el-alert type="warning" :closable="false" style="margin-bottom:12px"
                title="症状加重或未缓解时，平台将提升风险等级、重新通知家属与医疗人员，并把原拒医记录、家属态度、医生建议带入一条新的升级事件。" />
      <el-form :model="revisit" label-width="100px">
        <el-form-item label="实际复访方式">
          <el-radio-group v-model="revisit.method">
            <el-radio v-for="(v, k) in REVISIT_METHOD_LABEL" :key="k" :value="k">{{ v }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复访结果">
          <el-radio-group v-model="revisit.result">
            <el-radio value="RELIEVED">症状缓解（解除随访，可归档）</el-radio>
            <el-radio value="UNRELIEVED">症状未缓解（升级+重新通知）</el-radio>
            <el-radio value="WORSENED">症状加重（极高风险+呼叫120）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="重新通知方式" v-if="revisit.result !== 'RELIEVED'">
          <el-select v-model="revisit.notifyChannel" style="width:100%">
            <el-option label="电话" value="电话" />
            <el-option label="短信" value="短信" />
            <el-option label="微信" value="微信" />
            <el-option label="当面" value="当面" />
          </el-select>
        </el-form-item>
        <el-form-item label="复访说明">
          <el-input v-model="revisit.note" type="textarea" :rows="3"
                    placeholder="现场症状、血压复测值、处置情况等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="revisitDialog = false">取消</el-button>
        <el-button :type="revisit.result === 'RELIEVED' ? 'success' : 'danger'"
                   @click="submitRevisit">提交复访结果</el-button>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Search, Phone, House, ChatDotRound, Files, CircleCheck, CircleClose,
  EditPen, Picture, UserFilled, Warning
} from '@element-plus/icons-vue'
import api from '../api'
import {
  SOURCE_LABEL, TRIAGE_LABEL, SEVERITY_LABEL, SEVERITY_TAG, TARGET_LABEL,
  SERVICE_LABEL, FOLLOW_RESULT_LABEL, RISK_LABEL, RISK_TAG, STATUS_LABEL, STATUS_TAG,
  RISK_LEVEL_LABEL, RISK_LEVEL_TAG, ENTRY_METHODS, ELDER_STATUS_OPTIONS,
  REVISIT_METHOD_LABEL, REVISIT_RESULT_LABEL
} from '../api/dict'

const route = useRoute()
const router = useRouter()
const d = ref({})
const loading = ref(false)

const DISPATCH_STATUS = { NOTIFIED: '已通知', ACCEPTED: '已受理', ARRIVED: '已到场', REJECTED: '无法响应' }
const REVISIT_TASK_STATUS = { PENDING: '待复访', COMPLETED: '已复访', OVERDUE: '已逾期' }
const REVISIT_TAG = { PENDING: 'warning', COMPLETED: 'success', OVERDUE: 'danger' }
const LOG_LABEL = {
  HELP: '求助', TRIAGE: '研判', DISPATCH: '转介', ONSITE: '上门', SERVICE: '服务',
  FAMILY_COMM: '家属沟通', FOLLOW_UP: '回访', CLOSE: '归档', SYSTEM: '系统', NOTE: '备注',
  REFUSAL: '拒医登记', REVISIT: '拒医复访'
}
const LOG_TYPE = {
  HELP: 'danger', TRIAGE: 'warning', DISPATCH: 'warning', ONSITE: 'primary',
  SERVICE: 'success', FAMILY_COMM: 'info', FOLLOW_UP: 'success', CLOSE: 'success',
  SYSTEM: 'info', NOTE: 'info', REFUSAL: 'danger', REVISIT: 'danger'
}

const flags = computed(() => (d.value.event?.riskFlags || '').split(',').filter(Boolean))
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
  await api.post(`/events/${route.params.id}/follow-up`, follow)
  ElMessage.success('事件已归档，画像与关怀计划已更新')
  followDialog.value = false
  load()
}

/* ---- 拒绝就医登记 ---- */
const refusalDialog = ref(false)
const refusal = reactive({})
function openRefusal() {
  Object.assign(refusal, {
    abnormalSign: '', doctorName: '', doctorAdvice: '', refuseReason: '',
    familyInformed: false, familyName: '', familyAttitude: '积极配合送医',
    alternativeCare: '', revisitMethod: 'PHONE', revisitAssignee: '', revisitDueAt: null
  })
  refusalDialog.value = true
}
async function submitRefusal() {
  if (!refusal.doctorName || !refusal.doctorAdvice || !refusal.refuseReason) {
    ElMessage.warning('请填写家庭医生、医生建议与老人拒绝原因')
    return
  }
  await api.post(`/events/${route.params.id}/medical-refusals`, refusal)
  ElMessage.success('已登记拒绝就医，并生成 24 小时内复访任务')
  refusalDialog.value = false
  load()
}

/* ---- 完成拒医复访 ---- */
const revisitDialog = ref(false)
const revisit = reactive({})
const currentTask = ref(null)
function openRevisit(task) {
  currentTask.value = task
  Object.assign(revisit, {
    result: 'RELIEVED', method: task.method || 'PHONE', note: '', notifyChannel: '电话'
  })
  revisitDialog.value = true
}
async function submitRevisit() {
  if (revisit.result !== 'RELIEVED') {
    await ElMessageBox.confirm(
      revisit.result === 'WORSENED'
        ? '复访判定症状加重：将提升为极高风险、呼叫 120 并重新通知家属/医生，同时生成携带历史的升级事件。确认？'
        : '复访判定症状未缓解：将提升风险等级、重新通知家属/医生，并生成携带历史的升级事件。确认？',
      '复访升级确认', { type: 'warning', confirmButtonText: '确认升级' })
  }
  const res = await api.post(`/events/revisits/${currentTask.value.id}/complete`, revisit)
  revisitDialog.value = false
  if (res.relieved) {
    ElMessage.success('复访症状缓解，随访闭环结束，事件可正常回访归档')
    load()
  } else if (res.escalatedEvent) {
    ElMessage.warning(`已生成升级事件 ${res.escalatedEvent.eventNo}，正在跳转…`)
    revisitDialog.value = false
    router.push(`/events/${res.escalatedEvent.id}`)
  }
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
.refusal-card { border: 1px solid #fbc4c4; }
.refusal-box { border-left: 3px solid #f56c6c; padding-left: 8px; }
.revisit-row {
  display: flex; align-items: center; flex-wrap: wrap; gap: 2px;
  padding: 8px 10px; margin-bottom: 8px; background: #fef6f6; border-radius: 6px;
  border: 1px dashed #f5b7b7;
}
.elder-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.person-row { display: flex; align-items: center; gap: 2px; padding: 5px 0; font-size: 13px; border-bottom: 1px dashed #ebeef5; }
</style>
