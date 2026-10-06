package com.community.eldercare.controller;

import com.community.eldercare.common.R;
import com.community.eldercare.domain.RefusalRecord;
import com.community.eldercare.domain.RiskFollowTask;
import com.community.eldercare.dto.FollowTaskCompleteRequest;
import com.community.eldercare.dto.RefusalRequest;
import com.community.eldercare.service.RefusalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 拒绝就医风险随访：拒医登记、24 小时复访任务、复访结果处置。
 */
@RestController
@RequestMapping("/api")
public class RefusalController {

    private final RefusalService refusalService;

    public RefusalController(RefusalService refusalService) {
        this.refusalService = refusalService;
    }

    /** 登记拒绝就医（自动生成 24 小时复访任务，事件随访解除前不能归档） */
    @PostMapping("/events/{id}/refusals")
    public R<RefusalRecord> create(@PathVariable Long id, @Valid @RequestBody RefusalRequest req) {
        return R.ok(refusalService.createRefusal(id, req));
    }

    /** 事件的拒医记录及各自复访任务 */
    @GetMapping("/events/{id}/refusals")
    public R<List<Map<String, Object>>> eventRefusals(@PathVariable Long id) {
        return R.ok(refusalService.eventRefusals(id));
    }

    /** 为随访中的拒医记录追加复访方式（PHONE/VISIT/DOCTOR_REVIEW） */
    @PostMapping("/refusals/{id}/tasks")
    public R<RiskFollowTask> addTask(@PathVariable Long id, @RequestParam String taskType) {
        return R.ok(refusalService.addTask(id, taskType));
    }

    /** 复访任务列表（默认待复访，按截止时间升序；?status=PENDING/DONE&elderId=） */
    @GetMapping("/refusal-follow-tasks")
    public R<List<RiskFollowTask>> tasks(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) Long elderId) {
        return R.ok(refusalService.listTasks(status, elderId));
    }

    /** 完成复访：缓解解除随访；加重提升风险等级并重新通知家属与医疗人员；未缓解转入持续随访 */
    @PostMapping("/refusal-follow-tasks/{id}/complete")
    public R<RiskFollowTask> complete(@PathVariable Long id, @Valid @RequestBody FollowTaskCompleteRequest req) {
        return R.ok(refusalService.completeTask(id, req));
    }
}
