package com.community.eldercare.service;

import com.community.eldercare.common.ApiException;
import com.community.eldercare.domain.*;
import com.community.eldercare.dto.*;
import com.community.eldercare.notify.SseService;
import com.community.eldercare.repo.*;
import com.community.eldercare.security.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 求助事件全生命周期服务。
 * 求助 → 研判 → 转介 → 上门核实 → 后续服务 → 家属沟通 → 回访 → 归档（联动更新画像）。
 */
@Service
public class EventService {

    private final HelpEventRepository eventRepo;
    private final ElderRepository elderRepo;
    private final EventLogRepository logRepo;
    private final EventDispatchRepository dispatchRepo;
    private final OnsiteVisitRepository onsiteRepo;
    private final EventServiceRepository serviceRepo;
    private final FamilyCommunicationRepository commRepo;
    private final EventFollowUpRepository followUpRepo;
    private final EmergencyContactRepository contactRepo;
    private final NeighborResponderRepository neighborRepo;
    private final RefusalRecordRepository refusalRepo;
    private final RiskFollowTaskRepository followTaskRepo;
    private final SseService sse;

    @Value("${app.location-offset-meters:500}")
    private int locationOffsetMetersThreshold;

    @Value("${app.frequent-night-threshold:3}")
    private int frequentNightThreshold;

    public EventService(HelpEventRepository eventRepo, ElderRepository elderRepo,
                        EventLogRepository logRepo, EventDispatchRepository dispatchRepo,
                        OnsiteVisitRepository onsiteRepo, EventServiceRepository serviceRepo,
                        FamilyCommunicationRepository commRepo, EventFollowUpRepository followUpRepo,
                        EmergencyContactRepository contactRepo, NeighborResponderRepository neighborRepo,
                        RefusalRecordRepository refusalRepo, RiskFollowTaskRepository followTaskRepo,
                        SseService sse) {
        this.eventRepo = eventRepo;
        this.elderRepo = elderRepo;
        this.logRepo = logRepo;
        this.dispatchRepo = dispatchRepo;
        this.onsiteRepo = onsiteRepo;
        this.serviceRepo = serviceRepo;
        this.commRepo = commRepo;
        this.followUpRepo = followUpRepo;
        this.contactRepo = contactRepo;
        this.neighborRepo = neighborRepo;
        this.refusalRepo = refusalRepo;
        this.followTaskRepo = followTaskRepo;
        this.sse = sse;
    }

    /* ===================== 求助发起 ===================== */

    @Transactional
    public HelpEvent createHelp(HelpRequest req) {
        Elder elder = elderRepo.findById(req.getElderId())
                .orElseThrow(() -> new ApiException("老人不存在: " + req.getElderId()));

        HelpEvent e = new HelpEvent();
        e.setEventNo(generateEventNo());
        e.setElder(elder);
        e.setStatus("NEW");
        e.setSource(req.getSource());
        e.setSourceDetail(req.getSourceDetail());
        e.setVoiceContent(req.getVoiceContent());
        e.setCallLongitude(req.getCallLongitude());
        e.setCallLatitude(req.getCallLatitude());
        e.setCallAddress(req.getCallAddress());
        LocalDateTime now = LocalDateTime.now();
        boolean night = now.getHour() >= 22 || now.getHour() < 6;
        e.setNightHelp(night);

        Set<String> flags = new LinkedHashSet<>();

        // 定位偏移判定：与档案住址坐标距离超过阈值
        if (req.getCallLongitude() != null && req.getCallLatitude() != null
                && elder.getHomeLongitude() != null && elder.getHomeLatitude() != null) {
            int dist = (int) Math.round(haversine(elder.getHomeLatitude(), elder.getHomeLongitude(),
                    req.getCallLatitude(), req.getCallLongitude()));
            e.setLocationOffsetMeters(dist);
            if (dist > locationOffsetMetersThreshold) {
                e.setLocationOffset(true);
                flags.add("LOCATION_OFFSET");
            }
        }

        // 频繁夜间求助：近 30 天夜间求助次数（含本次）达到阈值
        if (night) {
            long recent = eventRepo.findNightHelpsSince(elder.getId(), now.minusDays(30)).size();
            if (recent + 1 >= frequentNightThreshold) {
                flags.add("FREQUENT_NIGHT_HELP");
            }
        }

        // 家属长期不接电话：档案主要联系人已被标记
        contactRepo.findByElderId(elder.getId()).stream()
                .filter(EmergencyContact::isOftenUnreachable).findFirst()
                .ifPresent(c -> flags.add("FAMILY_UNREACHABLE"));

        // 未缓解的拒医记录随新求助自动带入：防止值班人员只看到一次新的普通求助
        Optional<RefusalRecord> activeRefusal =
                refusalRepo.findFirstByElderIdAndStatusOrderByCreatedAtDesc(elder.getId(), "OPEN");
        activeRefusal.ifPresent(r -> flags.add("REFUSE_HOSPITAL"));

        e.setRiskFlags(String.join(",", flags));
        e = eventRepo.save(e);

        addLog(e, "HELP", "收到求助：来源 " + sourceLabel(req.getSource())
                + (req.getSourceDetail() != null ? "（" + req.getSourceDetail() + "）" : "")
                + (night ? "，夜间求助" : "")
                + (req.getVoiceContent() != null ? "，语音内容：" + req.getVoiceContent() : ""),
                "系统");

        // 智能按钮自动播报老人基础病与紧急联系人，便于值班社工快速研判
        addLog(e, "SYSTEM", "档案提示：基础病 " + nz(elder.getChronicDiseases(), "无")
                + "；门禁 " + nz(elder.getDoorAccessInfo(), "无")
                + "；画像风险 " + elder.getRiskLevel()
                + "；历史误触 " + elder.getFalseTriggerCount() + " 次", "系统");

        // 拒医历史带入：原拒医记录、家属态度、医生建议一并提示值班人员
        final HelpEvent saved = e;
        activeRefusal.ifPresent(r -> addLog(saved, "REFUSAL",
                "历史拒医带入（来自事件 " + r.getEvent().getEventNo() + "，症状未缓解，随访中）："
                        + "异常发现 " + nz(r.getAbnormalFinding(), "身体指标异常")
                        + "；医生建议：" + r.getDoctorAdvice()
                        + "；老人拒绝原因：" + r.getRefuseReason()
                        + "；家属" + (r.isFamilyInformed()
                        ? "知情，态度 " + nz(r.getFamilyAttitude(), "未记录") : "不知情")
                        + (r.getAlternativeCare() != null && !r.getAlternativeCare().isBlank()
                        ? "；替代照护安排：" + r.getAlternativeCare() : "")
                        + "；当前拒医风险等级 " + severityLabel(r.getRiskLevel())
                        + "。本次求助请结合上述情况研判，勿按普通求助处理。", "系统"));

        broadcast("event-new", e.getId());
        return e;
    }

    /* ===================== 研判 ===================== */

    @Transactional
    public HelpEvent triage(Long eventId, TriageRequest req) {
        HelpEvent e = mustGet(eventId);
        e.setTriageType(req.getTriageType());
        e.setSeverity(req.getSeverity());
        e.setTriageNote(req.getTriageNote());
        e.setTriageWorkerName(UserContext.currentName());
        e.setTriagedAt(LocalDateTime.now());
        if ("FALSE_TRIGGER".equals(req.getTriageType())) {
            e.setStatus("FOLLOWED_UP");
            addLog(e, "TRIAGE", "研判为误触，严重程度 " + severityLabel(req.getSeverity())
                    + note(req.getTriageNote()), UserContext.currentName());
        } else {
            e.setStatus("TRIAGED");
            addLog(e, "TRIAGE", "研判类型 " + triageLabel(req.getTriageType())
                    + "，严重程度 " + severityLabel(req.getSeverity())
                    + note(req.getTriageNote()), UserContext.currentName());
        }
        broadcast("event-update", eventId);
        return e;
    }

    /* ===================== 转介/通知 ===================== */

    @Transactional
    public EventDispatch dispatch(Long eventId, DispatchRequest req) {
        HelpEvent e = mustGet(eventId);
        EventDispatch d = new EventDispatch();
        d.setEvent(e);
        d.setTargetType(req.getTargetType());
        d.setTargetName(req.getTargetName());
        d.setTargetPhone(req.getTargetPhone());
        d.setNotifyChannel(req.getNotifyChannel());
        d.setNeighborReported(req.isNeighborReported());
        d.setNeighborInfoClear(req.isNeighborInfoClear());
        d.setNotifiedByName(UserContext.currentName());
        d.setNotifiedAt(LocalDateTime.now());
        d = dispatchRepo.save(d);

        Set<String> flags = flagsOf(e);
        if (req.isNeighborReported() && !req.isNeighborInfoClear()) {
            flags.add("NEIGHBOR_INFO_UNCLEAR");
        }
        e.setRiskFlags(String.join(",", flags));
        e.setStatus("DISPATCHED");
        e.setDispatchedAt(e.getDispatchedAt() == null ? LocalDateTime.now() : e.getDispatchedAt());

        addLog(e, "DISPATCH", "已通知" + targetLabel(req.getTargetType()) + "：" + req.getTargetName()
                + (req.isNeighborReported() ? "（邻居代报"
                + (req.isNeighborInfoClear() ? "，信息清晰" : "，信息不清") + "）" : "")
                + note(req.getNote()), UserContext.currentName());
        broadcast("event-update", eventId);
        return d;
    }

    @Transactional
    public EventDispatch updateDispatchStatus(Long dispatchId, String status) {
        EventDispatch d = dispatchRepo.findById(dispatchId)
                .orElseThrow(() -> new ApiException("转介记录不存在"));
        d.setStatus(status);
        LocalDateTime now = LocalDateTime.now();
        if ("ACCEPTED".equals(status)) {
            d.setAcceptedAt(now);
            d.setEffectiveResponse(true);
        } else if ("ARRIVED".equals(status)) {
            d.setArrivedAt(now);
            d.setEffectiveResponse(true);
        } else if ("REJECTED".equals(status)) {
            d.setEffectiveResponse(false);
        }
        addLog(d.getEvent(), "DISPATCH", targetLabel(d.getTargetType()) + " " + d.getTargetName()
                + " 状态更新为 " + dispatchStatusLabel(status), UserContext.currentName());
        broadcast("event-update", d.getEvent().getId());
        return d;
    }

    /* ===================== 上门核实 ===================== */

    @Transactional
    public OnsiteVisit onsite(Long eventId, OnsiteVisitRequest req) {
        HelpEvent e = mustGet(eventId);
        OnsiteVisit v = new OnsiteVisit();
        v.setEvent(e);
        v.setEntryMethod(req.getEntryMethod());
        v.setEntered(req.isEntered());
        v.setPhotoUrls(req.getPhotoUrls());
        v.setElderStatus(req.getElderStatus());
        v.setFamilyContacted(req.isFamilyContacted());
        v.setHospitalNeeded(req.isHospitalNeeded());
        v.setRefuseMedical(req.isRefuseMedical());
        v.setTemporaryCareNeeded(req.isTemporaryCareNeeded());
        v.setFollowServiceSuggestions(req.getFollowServiceSuggestions());
        v.setSafetyHazards(req.getSafetyHazards());
        v.setNote(req.getNote());
        v.setVisitorName(UserContext.currentName());
        LocalDateTime now = LocalDateTime.now();
        v.setArrivedAt(now);
        v = onsiteRepo.save(v);

        Set<String> flags = flagsOf(e);
        if (!req.isEntered() || "未能入户".equals(req.getEntryMethod())) {
            flags.add("CANNOT_OPEN_DOOR");
        }
        if (req.isRefuseMedical()) {
            flags.add("REFUSE_HOSPITAL");
        }
        if (req.getSafetyHazards() != null && !req.getSafetyHazards().isBlank()) {
            flags.add("HOME_SAFETY_HAZARD");
        }
        e.setRiskFlags(String.join(",", flags));
        e.setStatus("ONSITE");
        if (e.getArrivedAt() == null) {
            e.setArrivedAt(now);
            e.setResponseMinutes((int) ChronoUnit.MINUTES.between(e.getCreatedAt(), now));
        }
        if (req.isHospitalNeeded()) {
            e.setHospitalTransfer(true);
        }

        addLog(e, "ONSITE", "上门核实：入户方式 " + req.getEntryMethod()
                + "，老人状态 " + req.getElderStatus()
                + (req.isHospitalNeeded() ? "，需送医" : "")
                + (req.isRefuseMedical() ? "，老人拒绝就医" : "")
                + (req.isTemporaryCareNeeded() ? "，需临时照护" : "")
                + (req.getSafetyHazards() != null && !req.getSafetyHazards().isBlank()
                ? "，发现家中安全隐患：" + req.getSafetyHazards() : ""),
                UserContext.currentName());
        broadcast("event-update", eventId);
        return v;
    }

    /* ===================== 后续服务 ===================== */

    @Transactional
    public com.community.eldercare.domain.EventService addService(Long eventId, EventServiceRequest req) {
        HelpEvent e = mustGet(eventId);
        com.community.eldercare.domain.EventService svc = new com.community.eldercare.domain.EventService();
        svc.setEvent(e);
        svc.setServiceType(req.getServiceType());
        svc.setProviderName(req.getProviderName());
        svc.setProviderPhone(req.getProviderPhone());
        svc.setContent(req.getContent());
        svc.setStatus(req.getStatus() == null || req.getStatus().isBlank() ? "DONE" : req.getStatus());
        svc = serviceRepo.save(svc);

        e.setStatus("SERVICED");
        e.setServiceType(mergeCsv(e.getServiceType(), req.getServiceType()));
        addLog(e, "SERVICE", "后续服务：" + serviceLabel(req.getServiceType())
                + "，提供方 " + req.getProviderName()
                + (req.getContent() != null ? "，内容 " + req.getContent() : ""),
                UserContext.currentName());
        broadcast("event-update", eventId);
        return svc;
    }

    /* ===================== 家属沟通 ===================== */

    @Transactional
    public FamilyCommunication familyComm(Long eventId, FamilyCommRequest req) {
        HelpEvent e = mustGet(eventId);
        FamilyCommunication c = new FamilyCommunication();
        c.setEvent(e);
        c.setContactName(req.getContactName());
        c.setContactPhone(req.getContactPhone());
        c.setChannel(req.getChannel());
        c.setReached(req.isReached());
        c.setAttitude(req.getAttitude());
        c.setNote(req.getNote());
        c.setOperatorName(UserContext.currentName());
        if (req.getContactId() != null) {
            EmergencyContact ec = contactRepo.findById(req.getContactId()).orElse(null);
            c.setContact(ec);
            if (ec != null) {
                ec.setContactAttempts(ec.getContactAttempts() + 1);
                if (req.isReached()) {
                    ec.setContactReached(ec.getContactReached() + 1);
                }
                // 同一联系人累计 3 次以上未接通率超过 2/3，标记长期不接
                if (ec.getContactAttempts() >= 3
                        && (ec.getContactAttempts() - ec.getContactReached()) * 3 >= ec.getContactAttempts() * 2) {
                    ec.setOftenUnreachable(true);
                    ec.setReachabilityNote("累计" + ec.getContactAttempts() + "次联系中"
                            + (ec.getContactAttempts() - ec.getContactReached()) + "次未接通");
                }
                contactRepo.save(ec);
            }
        }
        c = commRepo.save(c);

        Set<String> flags = flagsOf(e);
        long unreached = commRepo.findByEventIdOrderByCommunicatedAtAsc(eventId).stream()
                .filter(x -> !x.isReached()).count();
        if (!req.isReached() && unreached >= 2) {
            flags.add("FAMILY_UNREACHABLE");
        }
        e.setRiskFlags(String.join(",", flags));
        e.setStatus("COMMUNICATED");

        addLog(e, "FAMILY_COMM", "联系家属 " + req.getContactName()
                + "：" + (req.isReached() ? "已接通，态度 " + nz(req.getAttitude(), "未记录") : "未接通")
                + note(req.getNote()), UserContext.currentName());
        broadcast("event-update", eventId);
        return c;
    }

    /* ===================== 回访 + 归档（联动更新画像） ===================== */

    @Transactional
    public EventFollowUp followUpAndClose(Long eventId, FollowUpRequest req) {
        HelpEvent e = mustGet(eventId);
        // 存在随访中的拒医记录时事件不能直接关闭：须先完成 24 小时复访且症状缓解
        if (refusalRepo.existsByEventIdAndStatus(eventId, "OPEN")) {
            throw new ApiException("存在未解除的拒绝就医风险随访：请先完成 24 小时复访任务，"
                    + "确认症状缓解后才能归档（症状加重/未缓解将继续随访）");
        }
        EventFollowUp f = new EventFollowUp();
        f.setEvent(e);
        f.setResult(req.getResult());
        f.setMethod(req.getMethod());
        f.setSatisfaction(req.getSatisfaction());
        f.setNote(req.getNote());
        f.setOperatorName(UserContext.currentName());
        f.setFollowedUpAt(LocalDateTime.now());

        // 下一次主动关怀：显式指定优先；否则按风险自动排期
        String careType = req.getNextCareType();
        LocalDateTime careAt = req.getNextCareAt();
        if (careAt == null) {
            String[] auto = autoSchedule(e);
            if (careType == null) {
                careType = auto[0];
            }
            careAt = LocalDateTime.now().plusDays(Long.parseLong(auto[1]));
        }
        f.setNextCareType(careType);
        f.setNextCareAt(careAt);
        f = followUpRepo.save(f);

        e.setFollowUpResult(req.getResult());
        e.setFollowUpNote(req.getNote());
        e.setFollowedUpAt(f.getFollowedUpAt());

        addLog(e, "FOLLOW_UP", "回访结果 " + followResultLabel(req.getResult())
                + (req.getSatisfaction() != null ? "，满意度 " + req.getSatisfaction() + "/5" : "")
                + note(req.getNote()), UserContext.currentName());

        closeAndUpdateProfile(e, careType, careAt);
        return f;
    }

    /** 归档：更新老人档案、家属响应表现、邻里协助可信度、下一次主动关怀计划 */
    private void closeAndUpdateProfile(HelpEvent e, String careType, LocalDateTime careAt) {
        Elder elder = e.getElder();
        Set<String> flags = flagsOf(e);

        // 1. 误触记录
        if ("FALSE_TRIGGER".equals(e.getTriageType())) {
            elder.setFalseTriggerCount(elder.getFalseTriggerCount() + 1);
            elder.setFalseTriggerNote("历史误触累计 " + elder.getFalseTriggerCount()
                    + " 次，最近事件 " + e.getEventNo());
        }

        // 2. 家属响应表现：按本事件接通情况和联系人累计表现调整
        var comms = commRepo.findByEventIdOrderByCommunicatedAtAsc(e.getId());
        if (!comms.isEmpty()) {
            long reached = comms.stream().filter(FamilyCommunication::isReached).count();
            int score = (int) Math.round(100.0 * reached / comms.size());
            elder.setFamilyResponsivenessScore(score);
            if (score < 50) {
                elder.setFamilyResponsivenessNote("本事件家属接通率 " + score
                        + "%，存在长期不接电话/响应不及时情况，事件 " + e.getEventNo());
            } else {
                elder.setFamilyResponsivenessNote("最近事件 " + e.getEventNo() + " 接通率 " + score + "%");
            }
        }

        // 3. 邻里协助可信度
        var dispatches = dispatchRepo.findByEventIdOrderByNotifiedAtAsc(e.getId()).stream()
                .filter(d -> "NEIGHBOR_VOLUNTEER".equals(d.getTargetType())).toList();
        if (!dispatches.isEmpty()) {
            for (EventDispatch d : dispatches) {
                if (d.getTargetName() != null) {
                    neighborRepo.findByElderId(elder.getId()).stream()
                            .filter(n -> n.getName().equals(d.getTargetName()))
                            .findFirst().ifPresent(n -> {
                                n.setCallCount(n.getCallCount() + 1);
                                if (d.isEffectiveResponse()) {
                                    n.setEffectiveCount(n.getEffectiveCount() + 1);
                                }
                                if (d.isNeighborReported() && !d.isNeighborInfoClear()) {
                                    n.setReliabilityScore(Math.max(30, n.getReliabilityScore() - 15));
                                    n.setNote("代报信息不清，事件 " + e.getEventNo());
                                } else if (d.isEffectiveResponse()) {
                                    n.setReliabilityScore(Math.min(100, n.getReliabilityScore() + 5));
                                }
                                neighborRepo.save(n);
                            });
                }
            }
            java.util.OptionalDouble avgOpt = neighborRepo.findByElderId(elder.getId()).stream()
                    .mapToInt(NeighborResponder::getReliabilityScore).average();
            int avg = avgOpt.isPresent() ? (int) Math.round(avgOpt.getAsDouble()) : 80;
            elder.setNeighborReliabilityScore(avg);
        }

        // 4. 服务建议（日常巡访 / 助餐 / 适老化改造 / 心理陪伴）
        var visits = onsiteRepo.findByEventIdOrderByArrivedAtAsc(e.getId());
        boolean hazard = flags.contains("HOME_SAFETY_HAZARD")
                || visits.stream().anyMatch(v -> v.getSafetyHazards() != null && !v.getSafetyHazards().isBlank());
        boolean fall = "FALL_RISK".equals(e.getTriageType());
        boolean emotion = "EMOTIONAL_CRISIS".equals(e.getTriageType());
        boolean tempCare = visits.stream().anyMatch(OnsiteVisit::isTemporaryCareNeeded);

        if (hazard || fall) {
            elder.setSuggestAgingRetrofit(true);
        }
        if (emotion || "EMOTIONAL_CRISIS".equals(e.getTriageType())) {
            elder.setSuggestPsychCompanion(true);
        }
        if (tempCare || flags.contains("FAMILY_UNREACHABLE") || "HIGH".equals(e.getSeverity())
                || "CRITICAL".equals(e.getSeverity())) {
            elder.setSuggestDailyVisit(true);
        }
        // 上门建议中包含助餐则标记
        if (visits.stream().map(OnsiteVisit::getFollowServiceSuggestions)
                .filter(Objects::nonNull).anyMatch(s -> s.contains("助餐"))) {
            elder.setSuggestMealService(true);
        }

        // 5. 综合风险等级
        elder.setRiskLevel(recomputeRisk(e, flags, elder));

        // 6. 下一次主动关怀计划
        elder.setProactiveCareType(serviceLabel(careType));
        elder.setNextProactiveCareAt(careAt);
        elder.setProactiveCareNote("由事件 " + e.getEventNo() + " 回访后排期");

        elder.setUpdatedAt(LocalDateTime.now());
        elderRepo.save(elder);

        e.setStatus("CLOSED");
        e.setClosedAt(LocalDateTime.now());
        e.setProfileUpdated(true);
        if (e.getServiceType() == null) {
            e.setServiceType(careType);
        }
        eventRepo.save(e);

        addLog(e, "CLOSE", "事件归档。已联动更新老人画像：风险等级 " + elder.getRiskLevel()
                + "、家属响应 " + elder.getFamilyResponsivenessScore()
                + "、邻里可信度 " + elder.getNeighborReliabilityScore()
                + "；下一次主动关怀（" + elder.getProactiveCareType() + "）时间 " + careAt,
                UserContext.currentName());
        broadcast("event-closed", e.getId());
    }

    private String[] autoSchedule(HelpEvent e) {
        String severity = e.getSeverity();
        String careType = "DAILY_VISIT";
        long days;
        if ("FALL_RISK".equals(e.getTriageType())
                || e.getRiskFlags() != null && e.getRiskFlags().contains("HOME_SAFETY_HAZARD")) {
            careType = "AGING_RETROFIT";
        }
        if ("EMOTIONAL_CRISIS".equals(e.getTriageType())) {
            careType = "PSYCH_COMPANION";
        }
        if ("CRITICAL".equals(severity)) {
            days = 1;
        } else if ("HIGH".equals(severity)) {
            days = 3;
        } else if ("MEDIUM".equals(severity)) {
            days = 7;
        } else {
            days = 14;
        }
        if ("FALSE_TRIGGER".equals(e.getTriageType())) {
            days = 14;
            careType = "DAILY_VISIT";
        }
        return new String[]{careType, String.valueOf(days)};
    }

    private String recomputeRisk(HelpEvent e, Set<String> flags, Elder elder) {
        int weight = 0;
        if ("CRITICAL".equals(e.getSeverity())) weight += 4;
        else if ("HIGH".equals(e.getSeverity())) weight += 3;
        else if ("MEDIUM".equals(e.getSeverity())) weight += 1;
        if (flags.contains("CANNOT_OPEN_DOOR")) weight += 2;
        if (flags.contains("LOCATION_OFFSET")) weight += 1;
        if (flags.contains("FAMILY_UNREACHABLE")) weight += 1;
        if (flags.contains("REFUSE_HOSPITAL")) weight += 2;
        if (flags.contains("HOME_SAFETY_HAZARD")) weight += 2;
        if (flags.contains("FREQUENT_NIGHT_HELP")) weight += 2;
        if (e.isHospitalTransfer()) weight += 1;

        String newLevel;
        if (weight >= 5) {
            newLevel = "CRITICAL";
        } else if (weight >= 3) {
            newLevel = "HIGH";
        } else if (weight >= 1) {
            newLevel = "MEDIUM";
        } else {
            newLevel = "LOW";
        }
        // 只升不降由近期事件驱动；误触保持原级
        if ("FALSE_TRIGGER".equals(e.getTriageType())) {
            return elder.getRiskLevel() == null ? "LOW" : elder.getRiskLevel();
        }
        return newLevel;
    }

    /* ===================== 查询 ===================== */

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long id) {
        HelpEvent e = mustGet(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("event", e);
        m.put("elder", e.getElder());
        m.put("contacts", contactRepo.findByElderId(e.getElder().getId()));
        m.put("neighbors", neighborRepo.findByElderId(e.getElder().getId()));
        m.put("logs", logRepo.findByEventIdOrderByCreatedAtAsc(id));
        m.put("dispatches", dispatchRepo.findByEventIdOrderByNotifiedAtAsc(id));
        m.put("visits", onsiteRepo.findByEventIdOrderByArrivedAtAsc(id));
        m.put("services", serviceRepo.findByEventIdOrderByServiceAtAsc(id));
        m.put("communications", commRepo.findByEventIdOrderByCommunicatedAtAsc(id));
        m.put("followUps", followUpRepo.findByEventIdOrderByFollowedUpAtAsc(id));
        // 拒医风险随访：本事件的拒医记录（含复访任务）+ 老人未缓解的历史拒医（随求助带入）
        List<Map<String, Object>> refusals = new ArrayList<>();
        for (RefusalRecord r : refusalRepo.findByEventIdOrderByCreatedAtDesc(id)) {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("refusal", r);
            rm.put("tasks", followTaskRepo.findByRefusalIdOrderByCreatedAtAsc(r.getId()));
            refusals.add(rm);
        }
        m.put("refusals", refusals);
        RefusalRecord active = refusalRepo
                .findFirstByElderIdAndStatusOrderByCreatedAtDesc(e.getElder().getId(), "OPEN")
                .orElse(null);
        m.put("activeRefusal", active);
        if (active != null && !active.getEvent().getId().equals(id)) {
            m.put("activeRefusalEventNo", active.getEvent().getEventNo());
        }
        return m;
    }

    @Transactional
    public void addNote(Long eventId, String content) {
        HelpEvent e = mustGet(eventId);
        addLog(e, "NOTE", content, UserContext.currentName());
        broadcast("event-update", eventId);
    }

    @Transactional
    public void markFlag(Long eventId, String flag) {
        HelpEvent e = mustGet(eventId);
        Set<String> flags = flagsOf(e);
        flags.add(flag);
        e.setRiskFlags(String.join(",", flags));
        addLog(e, "SYSTEM", "人工补充风险标记：" + riskLabel(flag), UserContext.currentName());
        broadcast("event-update", eventId);
    }

    private HelpEvent mustGet(Long id) {
        return eventRepo.findById(id).orElseThrow(() -> new ApiException(404, "事件不存在: " + id));
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

    private Set<String> flagsOf(HelpEvent e) {
        Set<String> s = new LinkedHashSet<>();
        if (e.getRiskFlags() != null && !e.getRiskFlags().isBlank()) {
            s.addAll(Arrays.asList(e.getRiskFlags().split(",")));
        }
        return s;
    }

    private String mergeCsv(String csv, String value) {
        Set<String> s = new LinkedHashSet<>();
        if (csv != null && !csv.isBlank()) {
            s.addAll(Arrays.asList(csv.split(",")));
        }
        if (value != null && !value.isBlank()) {
            s.add(value);
        }
        return String.join(",", s);
    }

    private String generateEventNo() {
        String datePart = java.time.LocalDate.now().toString().replace("-", "").substring(2);
        long count = eventRepo.count() + 1;
        return String.format("E%s%03d", datePart, count);
    }

    /** 球面距离（米） */
    static int haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return (int) Math.round(r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    private String nz(String s, String def) {
        return s == null || s.isBlank() ? def : s;
    }

    private String note(String s) {
        return s == null || s.isBlank() ? "" : "；说明：" + s;
    }

    public static String sourceLabel(String s) {
        return switch (s) {
            case "SMART_BUTTON" -> "智能按钮";
            case "PHONE" -> "电话";
            case "MINI_APP" -> "社区小程序";
            default -> s;
        };
    }

    public static String triageLabel(String s) {
        return switch (s) {
            case "LIFE_ASSIST" -> "生活协助";
            case "BODY_DISCOMFORT" -> "身体不适";
            case "FALL_RISK" -> "跌倒风险";
            case "EMOTIONAL_CRISIS" -> "情绪危机";
            case "FALSE_TRIGGER" -> "误触";
            default -> s;
        };
    }

    public static String severityLabel(String s) {
        return switch (s) {
            case "LOW" -> "低";
            case "MEDIUM" -> "中";
            case "HIGH" -> "高";
            case "CRITICAL" -> "紧急";
            default -> s;
        };
    }

    public static String targetLabel(String s) {
        return switch (s) {
            case "GRID_WORKER" -> "网格员";
            case "NEIGHBOR_VOLUNTEER" -> "邻里志愿者";
            case "FAMILY_DOCTOR" -> "家庭医生";
            case "EMERGENCY_120" -> "120急救";
            default -> s;
        };
    }

    public static String dispatchStatusLabel(String s) {
        return switch (s) {
            case "NOTIFIED" -> "已通知";
            case "ACCEPTED" -> "已受理";
            case "ARRIVED" -> "已到场";
            case "REJECTED" -> "无法响应";
            default -> s;
        };
    }

    public static String serviceLabel(String s) {
        if (s == null) return "";
        return switch (s) {
            case "DAILY_VISIT" -> "日常巡访";
            case "MEAL_SERVICE" -> "助餐";
            case "AGING_RETROFIT" -> "适老化改造";
            case "PSYCH_COMPANION" -> "心理陪伴";
            case "TEMP_CARE" -> "临时照护";
            case "HOSPITAL_ESCORT" -> "送医陪诊";
            case "OTHER" -> "其他服务";
            default -> s;
        };
    }

    public static String followResultLabel(String s) {
        return switch (s) {
            case "IMPROVED" -> "好转";
            case "STABLE" -> "平稳";
            case "WORSENED" -> "恶化";
            case "NO_ANSWER" -> "未联系上";
            default -> s;
        };
    }

    public static String riskLabel(String s) {
        return switch (s) {
            case "CANNOT_OPEN_DOOR" -> "无法开门";
            case "LOCATION_OFFSET" -> "定位偏移";
            case "FAMILY_UNREACHABLE" -> "家属长期不接电话";
            case "NEIGHBOR_INFO_UNCLEAR" -> "邻居代报信息不清";
            case "REFUSE_HOSPITAL" -> "老人拒绝就医";
            case "HOME_SAFETY_HAZARD" -> "家中存在安全隐患";
            case "FREQUENT_NIGHT_HELP" -> "频繁夜间求助";
            default -> s;
        };
    }
}
