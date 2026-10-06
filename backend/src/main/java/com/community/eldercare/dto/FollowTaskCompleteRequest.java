package com.community.eldercare.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 复访任务完成请求。
 * result：IMPROVED 症状缓解 / UNCHANGED 未缓解 / WORSENED 症状加重 / NO_ANSWER 未联系上。
 * 未缓解、加重或未联系上时会自动生成下一轮 24 小时复访任务，
 * 可通过 nextTaskType 指定下一轮方式（PHONE/VISIT/DOCTOR_REVIEW）。
 */
public class FollowTaskCompleteRequest {

    @NotBlank(message = "请填写复访结果")
    private String result;

    private String note;

    /** 下一轮复访方式（不填则按结果自动选择：加重→上门复访，其余→电话复访） */
    private String nextTaskType;

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getNextTaskType() { return nextTaskType; }
    public void setNextTaskType(String nextTaskType) { this.nextTaskType = nextTaskType; }
}
