package com.community.eldercare.controller;

import com.community.eldercare.common.R;
import com.community.eldercare.domain.*;
import com.community.eldercare.dto.*;
import com.community.eldercare.notify.SseService;
import com.community.eldercare.repo.HelpEventRepository;
import com.community.eldercare.service.EventService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final HelpEventRepository eventRepo;
    private final SseService sseService;
    private final com.community.eldercare.security.TokenService tokenService;

    public EventController(EventService eventService, HelpEventRepository eventRepo,
                           SseService sseService,
                           com.community.eldercare.security.TokenService tokenService) {
        this.eventService = eventService;
        this.eventRepo = eventRepo;
        this.sseService = sseService;
        this.tokenService = tokenService;
    }

    /** 值班工作台 SSE：新求助、状态变更实时推送。EventSource 无法加自定义头，token 通过 query 传递 */
    @GetMapping(path = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) String token,
                             @RequestHeader(value = "Authorization", required = false) String authorization) {
        String t = token;
        if (t == null && authorization != null && authorization.startsWith("Bearer ")) {
            t = authorization.substring(7);
        }
        if (tokenService.resolve(t).isEmpty()) {
            throw new com.community.eldercare.common.ApiException(401, "SSE 未授权");
        }
        return sseService.subscribe();
    }

    /** 发起求助（智能按钮/电话/小程序） */
    @PostMapping
    public R<HelpEvent> create(@Valid @RequestBody HelpRequest req) {
        return R.ok(eventService.createHelp(req));
    }

    /** 事件列表：?status=NEW / all=true / elderId= */
    @GetMapping
    public R<List<HelpEvent>> list(@RequestParam(required = false) String status,
                                   @RequestParam(required = false) Boolean all,
                                   @RequestParam(required = false) Long elderId) {
        if (elderId != null) {
            return R.ok(eventRepo.findByElderIdOrderByCreatedAtDesc(elderId));
        }
        if ("CLOSED".equals(status)) {
            return R.ok(eventRepo.findByStatusOrderByCreatedAtDesc("CLOSED"));
        }
        if (status != null && !status.isBlank()) {
            return R.ok(eventRepo.findByStatusOrderByCreatedAtDesc(status));
        }
        if (Boolean.TRUE.equals(all)) {
            return R.ok(eventRepo.findAllByOrderByCreatedAtDesc());
        }
        return R.ok(eventRepo.findByStatusNotOrderByCreatedAtDesc("CLOSED"));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(eventService.detail(id));
    }

    /** 研判 */
    @PostMapping("/{id}/triage")
    public R<HelpEvent> triage(@PathVariable Long id, @Valid @RequestBody TriageRequest req) {
        return R.ok(eventService.triage(id, req));
    }

    /** 转介/通知 */
    @PostMapping("/{id}/dispatches")
    public R<EventDispatch> dispatch(@PathVariable Long id, @Valid @RequestBody DispatchRequest req) {
        return R.ok(eventService.dispatch(id, req));
    }

    /** 更新转介响应状态：NOTIFIED/ACCEPTED/ARRIVED/REJECTED */
    @PutMapping("/dispatches/{dispatchId}/status")
    public R<EventDispatch> dispatchStatus(@PathVariable Long dispatchId, @RequestParam String status) {
        return R.ok(eventService.updateDispatchStatus(dispatchId, status));
    }

    /** 上门核实 */
    @PostMapping("/{id}/onsite")
    public R<OnsiteVisit> onsite(@PathVariable Long id, @Valid @RequestBody OnsiteVisitRequest req) {
        return R.ok(eventService.onsite(id, req));
    }

    /** 后续服务 */
    @PostMapping("/{id}/services")
    public R<com.community.eldercare.domain.EventService> service(@PathVariable Long id, @Valid @RequestBody EventServiceRequest req) {
        return R.ok(eventService.addService(id, req));
    }

    /** 家属沟通 */
    @PostMapping("/{id}/family-communications")
    public R<FamilyCommunication> familyComm(@PathVariable Long id, @Valid @RequestBody FamilyCommRequest req) {
        return R.ok(eventService.familyComm(id, req));
    }

    /** 回访并归档（联动更新画像） */
    @PostMapping("/{id}/follow-up")
    public R<EventFollowUp> followUp(@PathVariable Long id, @Valid @RequestBody FollowUpRequest req) {
        return R.ok(eventService.followUpAndClose(id, req));
    }

    /** 登记老人拒绝就医（记录医生建议/拒绝原因/家属知情/替代照护），自动生成 24 小时内复访任务 */
    @PostMapping("/{id}/medical-refusals")
    public R<Map<String, Object>> medicalRefusal(@PathVariable Long id,
                                                 @Valid @RequestBody MedicalRefusalRequest req) {
        return R.ok(eventService.recordMedicalRefusal(id, req));
    }

    /** 工作台复访任务列表（待复访/已逾期） */
    @GetMapping("/revisits/pending")
    public R<List<Map<String, Object>>> pendingRevisits() {
        return R.ok(eventService.pendingRevisitTasks());
    }

    /** 完成拒医复访：RELIEVED 解除随访；WORSENED/UNRELIEVED 提升风险、重新通知并生成升级事件 */
    @PostMapping("/revisits/{taskId}/complete")
    public R<Map<String, Object>> completeRevisit(@PathVariable Long taskId,
                                                  @Valid @RequestBody RevisitRequest req) {
        return R.ok(eventService.completeRevisit(taskId, req));
    }

    /** 补充事件备注 */
    @PostMapping("/{id}/notes")
    public R<Void> note(@PathVariable Long id, @RequestBody Map<String, String> body) {
        eventService.addNote(id, body.getOrDefault("content", ""));
        return R.ok();
    }

    /** 人工补充风险标记 */
    @PostMapping("/{id}/flags")
    public R<Void> flag(@PathVariable Long id, @RequestParam String flag) {
        eventService.markFlag(id, flag);
        return R.ok();
    }
}
