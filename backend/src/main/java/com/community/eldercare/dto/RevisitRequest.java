package com.community.eldercare.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 拒医复访结果登记。
 * RELIEVED 症状缓解 → 解除随访、事件可正常回访归档；
 * WORSENED 症状加重 / UNRELIEVED 症状未缓解 → 提升风险等级、重新通知家属与医疗人员，
 * 并把原拒医记录、家属态度、医生建议带入新事件。
 */
public class RevisitRequest {

    /** RELIEVED / UNRELIEVED / WORSENED */
    @NotBlank
    private String result;

    /** 实际复访方式，可覆盖任务创建时的方式：PHONE / ONSITE / DOCTOR */
    private String method;

    /** 复访说明（现场症状、处置等） */
    private String note;

    /** 症状加重/未缓解时，重新通知家属使用的方式（电话/短信/微信/当面），默认电话 */
    private String notifyChannel = "电话";

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getNotifyChannel() { return notifyChannel; }
    public void setNotifyChannel(String notifyChannel) { this.notifyChannel = notifyChannel; }
}
