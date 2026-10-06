package com.community.eldercare.service;

import com.community.eldercare.common.ApiException;
import com.community.eldercare.domain.*;
import com.community.eldercare.dto.FollowTaskCompleteRequest;
import com.community.eldercare.dto.RefusalRequest;
import com.community.eldercare.notify.SseService;
import com.community.eldercare.repo.*;
import com.community.eldercare.security.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 拒绝就医后的风险随访服务。
 * 登记拒医（医生建议/拒绝原因/家属知情与态度/替代照护）后事件不能直接归档，
 * 自动生成 24 小时内复访任务（电话复访/上门复访/医生复诊）；
 * 复访加重时提升风险等级并重新通知家属与医疗人员；
 * 症状未缓解的拒医记录会随老人下一次求助自动带入新事件。
 */
@Service
public class RefusalService {

    private final RefusalRecordRepository refusalRepo;
    private final RiskFollowTaskRepository taskRepo;
    private final HelpEventRepository eventRepo;
    private final ElderRepository elderRepo;
    private final EventLogRepository logRepo;
    private final EventDispatchRepository dispatchRepo;
    private final FamilyCommunicationRepository commRepo;
    private final EmergencyContactRepository contactRepo;
    private final SseService sse;

    /** 复访任务时限（小时），默认 24 小时 */
    @Value("${app.refusal-follow-hours:24}")
    private int followHours;

    public RefusalService(RefusalRecordRepository refusalRepo, RiskFollowTaskRepository taskRepo,
                          HelpEventRepository eventRepo, ElderRepository elderRepo,
                          EventLogRepository logRepo, EventDispatchRepository dispatchRepo,
                          FamilyCommunicationRepository commRepo, EmergencyContactRepository contactRepo,
                          SseService sse) {
        this.refusalRepo = refusalRepo;
        this.taskRepo = taskRepo;
        this.eventRepo = eventRepo;
        this.elderRepo = elderRepo;
        this.logRepo = logRepo;
        this.dispatchRepo = dispatchRepo;
        this.commRepo = commRepo;
        this.contactRepo = contactRepo;
        this.sse = sse;
    }

    /* ===================== 拒医登记 ===================== */

    @Transactional
    public RefusalRecord createRefusal(Long eventId, RefusalRequest req) {
        HelpEvent e = mustGetEvent(eventId);
        if ("CLOSED".equals(e.getStatus())) {
            throw new ApiException("事件已归档，不能登记拒医记录");
        }
        if (refusalRepo.existsByEventIdAndStatus(eventId, "OPEN")) {
            throw new ApiException("本事件已存在随访中的拒医记录，请勿重复登记");
        }

        RefusalRecord r = new RefusalRecord();
        r.setEvent(e);
        r.setElder(e.getElder());
        r.setDoctorName(req.getDoctorName());
        r.setAbnormalFinding(req.getAbnormalFinding());
        r.setDoctorAdvice(req.getDoctorAdvice());
        r.setRefuseReason(req.getRefuseReason());
        r.setFamilyInformed(req.isFamilyInformed());
        r.setFamilyAttitude(req.getFamilyAttitude());
        r.setAlternativeCare(req.getAlternativeCare());
        r.setStatus("OPEN");
        r.setRiskLevel("HIGH");
        r.setCreatedByName(UserContext.currentName());
        r = refusalRepo.save(r);

        // 事件叠加拒医风险标记
        Set<String> flags = flagsOf(e);
        flags.add("REFUSE_HOSPITAL");
        e.setRiskFlags(String.join(",", flags));
        eventRepo.save(e);

        // 自动生成 24 小时内复访任务
        RiskFollowTask task = generateTask(r, normalizeTaskType(req.getFollowTaskType()), 1);

        addLog(e, "REFUSAL", "登记拒绝就医：" + nz(req.getAbnormalFinding(), "身体指标异常")
                + "；医生建议：" + req.getDoctorAdvice()
                + "；老人拒绝原因：" + req.getRefuseReason()
                + "；家属" + (req.isFamilyInformed() ? "已知情（态度 " + nz(req.getFamilyAttitude(), "未记录") + "）" : "尚未知情，请尽快通知")
                + (req.getAlternativeCare() != null && !req.getAlternativeCare().isBlank()
                ? "；替代照护安排：" + req.getAlternativeCare() : "")
                + "。事件暂不能归档，已生成 " + followHours + " 小时"
                + taskTypeLabel(task.getTaskType()) + "任务（截止 " + task.getDueAt() + "）",
                UserContext.currentName());
        broadcast("event-update", eventId);
        return r;
    }

    /* ===================== 追加复访任务 ===================== */

    /** 为随访中的拒医记录追加一种复访方式（三种方式可并行安排） */
    @Transactional
    public RiskFollowTask addTask(Long refusalId, String taskType) {
        RefusalRecord r = mustGetRefusal(refusalId);
        if (!"OPEN".equals(r.getStatus())) {
            throw new ApiException("该拒医记录随访已解除，不能再安排复访任务");
        }
        String type = normalizeTaskType(taskType);
        boolean dup = taskRepo.findByRefusalIdAndStatus(refusalId, "PENDING").stream()
                .anyMatch(t -> t.getTaskType().equals(type));
        if (dup) {
            throw new ApiException("已存在待执行的" + taskTypeLabel(type) + "任务");
        }
        RiskFollowTask t = generateTask(r, type, r.getFollowRounds() + 1);
        addLog(r.getEvent(), "FOLLOW_TASK", "追加" + taskTypeLabel(type) + "任务，截止 " + t.getDueAt(),
                UserContext.currentName());
        broadcast("event-update", r.getEvent().getId());
        return t;
    }

    /* ===================== 完成复访 ===================== */

    @Transactional
    public RiskFollowTask completeTask(Long taskId, FollowTaskCompleteRequest req) {
        RiskFollowTask t = taskRepo.findById(taskId)
                .orElseThrow(() -> new ApiException(404, "复访任务不存在: " + taskId));
        if (!"PENDING".equals(t.getStatus())) {
            throw new ApiException("该复访任务已完成或已取消");
        }
        String result = req.getResult();
        if (!Set.of("IMPROVED", "UNCHANGED", "WORSENED", "NO_ANSWER").contains(result)) {
            throw new ApiException("复访结果必须是 IMPROVED/UNCHANGED/WORSENED/NO_ANSWER");
        }

        RefusalRecord r = t.getRefusal();
        HelpEvent e = r.getEvent();
        LocalDateTime now = LocalDateTime.now();
        t.setStatus("DONE");
        t.setResult(result);
        t.setResultNote(req.getNote());
        t.setCompletedAt(now);
        t.setCompletedByName(UserContext.currentName());
        taskRepo.save(t);
        r.setFollowRounds(r.getFollowRounds() + 1);

        switch (result) {
            case "IMPROVED" -> {
                // 症状缓解：解除随访，同记录其余待复访任务取消，事件可归档
                r.setStatus("RESOLVED");
                r.setResolvedAt(now);
                cancelPendingTasks(r.getId(), taskId);
                addLog(e, "FOLLOW_TASK", taskTypeLabel(t.getTaskType()) + "（第" + t.getRoundNo()
                        + "轮）结果：症状缓解，拒医风险随访解除，事件可回访归档"
                        + note(req.getNote()), UserContext.currentName());
            }
            case "WORSENED" -> {
                // 症状加重：提升风险等级，重新通知家属和医疗人员，生成下一轮复访
                escalate(r, e);
                String nextType = req.getNextTaskType() != null && !req.getNextTaskType().isBlank()
                        ? normalizeTaskType(req.getNextTaskType()) : "VISIT";
                RiskFollowTask next = generateTask(r, nextType, t.getRoundNo() + 1);
                addLog(e, "FOLLOW_TASK", taskTypeLabel(t.getTaskType()) + "（第" + t.getRoundNo()
                        + "轮）结果：症状加重！风险等级已提升为 " + EventService.severityLabel(r.getRiskLevel())
                        + "，已重新通知家属和家庭医生，并生成 " + followHours + " 小时"
                        + taskTypeLabel(next.getTaskType()) + "任务（第" + next.getRoundNo() + "轮）"
                        + note(req.getNote()), UserContext.currentName());
                broadcast("refusal-escalated", e.getId());
            }
            case "UNCHANGED" -> {
                // 未缓解：保持随访，生成下一轮复访；该拒医记录将随老人下次求助带入新事件
                String nextType = req.getNextTaskType() != null && !req.getNextTaskType().isBlank()
                        ? normalizeTaskType(req.getNextTaskType()) : "PHONE";
                RiskFollowTask next = generateTask(r, nextType, t.getRoundNo() + 1);
                addLog(e, "FOLLOW_TASK", taskTypeLabel(t.getTaskType()) + "（第" + t.getRoundNo()
                        + "轮）结果：症状未缓解，继续保持风险随访，已生成 " + followHours + " 小时"
                        + taskTypeLabel(next.getTaskType()) + "任务（第" + next.getRoundNo()
                        + "轮）；老人再次求助时本拒医记录将自动带入新事件"
                        + note(req.getNote()), UserContext.currentName());
            }
            case "NO_ANSWER" -> {
                String nextType = req.getNextTaskType() != null && !req.getNextTaskType().isBlank()
                        ? normalizeTaskType(req.getNextTaskType()) : "PHONE";
                RiskFollowTask next = generateTask(r, nextType, t.getRoundNo() + 1);
                addLog(e, "FOLLOW_TASK", taskTypeLabel(t.getTaskType()) + "（第" + t.getRoundNo()
                        + "轮）结果：未联系上老人，已生成 " + followHours + " 小时"
                        + taskTypeLabel(next.getTaskType()) + "任务（第" + next.getRoundNo()
                        + "轮）继续跟进" + note(req.getNote()), UserContext.currentName());
            }
        }
        refusalRepo.save(r);
        broadcast("event-update", e.getId());
        return t;
    }

    /** 症状加重：拒医记录、事件、老人画像风险等级各升一档，并重新通知家属与医疗人员 */
    private void escalate(RefusalRecord r, HelpEvent e) {
        r.setRiskLevel(escalateLevel(r.getRiskLevel()));

        // 事件严重程度只升不降
        String sev = e.getSeverity();
        if (sev == null || levelRank(sev) < levelRank(r.getRiskLevel())) {
            e.setSeverity(r.getRiskLevel());
        }
        Set<String> flags = flagsOf(e);
        flags.add("REFUSE_HOSPITAL");
        e.setRiskFlags(String.join(",", flags));
        eventRepo.save(e);

        // 老人画像风险等级同步提升（只升不降）
        Elder elder = r.getElder();
        if (levelRank(elder.getRiskLevel()) < levelRank(r.getRiskLevel())) {
            elder.setRiskLevel(r.getRiskLevel());
            elder.setUpdatedAt(LocalDateTime.now());
            elderRepo.save(elder);
        }

        // 重新通知医疗人员：自动向判断医生发起转介通知
        EventDispatch d = new EventDispatch();
        d.setEvent(e);
        d.setTargetType("FAMILY_DOCTOR");
        d.setTargetName(r.getDoctorName());
        d.setNotifyChannel("平台推送");
        d.setStatus("NOTIFIED");
        d.setNotifiedByName("系统");
        d.setNotifiedAt(LocalDateTime.now());
        d.setNote("系统自动重新通知：拒医复访症状加重（" + nz(r.getAbnormalFinding(), "身体指标异常")
                + "），风险等级 " + EventService.severityLabel(r.getRiskLevel()) + "，请尽快复诊评估");
        dispatchRepo.save(d);

        // 重新通知家属：自动向主要紧急联系人推送（无主要联系人则取第一位）
        contactRepo.findByElderId(elder.getId()).stream()
                .sorted(Comparator.comparing(EmergencyContact::isPrimaryContact).reversed())
                .findFirst()
                .ifPresent(contact -> {
                    FamilyCommunication c = new FamilyCommunication();
                    c.setEvent(e);
                    c.setContact(contact);
                    c.setContactName(contact.getName());
                    c.setContactPhone(contact.getPhone());
                    c.setChannel("平台推送");
                    c.setReached(true);
                    c.setOperatorName("系统");
                    c.setNote("系统自动重新通知家属：老人拒医后复访症状加重（"
                            + nz(r.getAbnormalFinding(), "身体指标异常") + "），风险等级已提升为 "
                            + EventService.severityLabel(r.getRiskLevel())
                            + "，请家属尽快协助送医或联系社区");
                    commRepo.save(c);
                });
    }

    /* ===================== 查询 ===================== */

    @Transactional(readOnly = true)
    public List<Map<String, Object>> eventRefusals(Long eventId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (RefusalRecord r : refusalRepo.findByEventIdOrderByCreatedAtDesc(eventId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("refusal", r);
            m.put("tasks", taskRepo.findByRefusalIdOrderByCreatedAtAsc(r.getId()));
            list.add(m);
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<RiskFollowTask> listTasks(String status, Long elderId) {
        if (elderId != null) {
            return taskRepo.findByElderIdOrderByCreatedAtDesc(elderId);
        }
        if (status != null && !status.isBlank()) {
            return taskRepo.findByStatusOrderByDueAtAsc(status);
        }
        return taskRepo.findByStatusOrderByDueAtAsc("PENDING");
    }

    /* ===================== 内部工具 ===================== */

    /** 生成一条 24 小时内复访任务 */
    private RiskFollowTask generateTask(RefusalRecord r, String taskType, int round) {
        RiskFollowTask t = new RiskFollowTask();
        t.setRefusal(r);
        t.setEvent(r.getEvent());
        t.setElder(r.getElder());
        t.setTaskType(taskType);
        t.setRoundNo(round);
        t.setDueAt(LocalDateTime.now().plusHours(followHours));
        return taskRepo.save(t);
    }

    private void cancelPendingTasks(Long refusalId, Long exceptTaskId) {
        for (RiskFollowTask x : taskRepo.findByRefusalIdAndStatus(refusalId, "PENDING")) {
            if (!x.getId().equals(exceptTaskId)) {
                x.setStatus("CANCELLED");
                taskRepo.save(x);
            }
        }
    }

    private static int levelRank(String level) {
        if (level == null) return 0;
        return switch (level) {
            case "LOW" -> 1;
            case "MEDIUM" -> 2;
            case "HIGH" -> 3;
            case "CRITICAL" -> 4;
            default -> 0;
        };
    }

    /** 风险等级提升一档（最高 CRITICAL） */
    static String escalateLevel(String level) {
        return switch (level == null ? "HIGH" : level) {
            case "LOW" -> "MEDIUM";
            case "MEDIUM" -> "HIGH";
            default -> "CRITICAL";
        };
    }

    private String normalizeTaskType(String type) {
        if (type == null || type.isBlank()) {
            return "PHONE";
        }
        if (!Set.of("PHONE", "VISIT", "DOCTOR_REVIEW").contains(type)) {
            throw new ApiException("复访方式必须是 PHONE 电话复访 / VISIT 上门复访 / DOCTOR_REVIEW 医生复诊");
        }
        return type;
    }

    private HelpEvent mustGetEvent(Long id) {
        return eventRepo.findById(id).orElseThrow(() -> new ApiException(404, "事件不存在: " + id));
    }

    private RefusalRecord mustGetRefusal(Long id) {
        return refusalRepo.findById(id).orElseThrow(() -> new ApiException(404, "拒医记录不存在: " + id));
    }

    private Set<String> flagsOf(HelpEvent e) {
        Set<String> s = new LinkedHashSet<>();
        if (e.getRiskFlags() != null && !e.getRiskFlags().isBlank()) {
            s.addAll(Arrays.asList(e.getRiskFlags().split(",")));
        }
        return s;
    }

    private void addLog(HelpEvent e, String type, String content, String operator) {
        EventLog l = new EventLog();
        l.setEvent(e);
        l.setType(type);
        l.setContent(content);
        l.setOperatorName(operator);
        logRepo.save(l);
    }

    private void broadcast(String name, Long eventId) {
        Map<String, Object> p = new HashMap<>();
        p.put("eventId", eventId);
        p.put("at", LocalDateTime.now().toString());
        sse.broadcast(name, p);
    }

    private String nz(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }

    private String note(String s) {
        return s == null || s.isBlank() ? "" : "；说明：" + s;
    }

    public static String taskTypeLabel(String s) {
        return switch (s) {
            case "PHONE" -> "电话复访";
            case "VISIT" -> "上门复访";
            case "DOCTOR_REVIEW" -> "医生复诊";
            default -> s;
        };
    }

    public static String followTaskResultLabel(String s) {
        return switch (s) {
            case "IMPROVED" -> "症状缓解";
            case "UNCHANGED" -> "未缓解";
            case "WORSENED" -> "症状加重";
            case "NO_ANSWER" -> "未联系上";
            default -> s;
        };
    }
}
