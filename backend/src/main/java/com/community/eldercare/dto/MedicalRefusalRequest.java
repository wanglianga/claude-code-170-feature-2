package com.community.eldercare.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 拒绝就医登记请求。
 * 家庭医生判断体征异常但老人拒绝就医时，记录医生建议、拒绝原因、家属是否知情与替代照护安排，
 * 系统据此生成 24 小时内复访任务，事件不允许直接关闭。
 */
public class MedicalRefusalRequest {

    /** 异常体征，如血压数值/症状 */
    private String abnormalSign;

    /** 提出建议的家庭医生/医疗人员姓名 */
    @NotBlank
    private String doctorName;

    /** 医生建议 */
    @NotBlank
    private String doctorAdvice;

    /** 老人拒绝原因 */
    @NotBlank
    private String refuseReason;

    /** 家属是否知情 */
    private boolean familyInformed;
    private String familyName;
    /** 家属态度 */
    private String familyAttitude;

    /** 替代照护安排 */
    private String alternativeCare;

    /** 复访方式 PHONE 电话复访 / ONSITE 上门复访 / DOCTOR 医生复诊，默认电话复访 */
    private String revisitMethod = "PHONE";

    /** 复访责任人，留空默认记录人 */
    private String revisitAssignee;

    /** 复访截止时间，留空默认 24 小时内 */
    private LocalDateTime revisitDueAt;

    public String getAbnormalSign() { return abnormalSign; }
    public void setAbnormalSign(String abnormalSign) { this.abnormalSign = abnormalSign; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getDoctorAdvice() { return doctorAdvice; }
    public void setDoctorAdvice(String doctorAdvice) { this.doctorAdvice = doctorAdvice; }
    public String getRefuseReason() { return refuseReason; }
    public void setRefuseReason(String refuseReason) { this.refuseReason = refuseReason; }
    public boolean isFamilyInformed() { return familyInformed; }
    public void setFamilyInformed(boolean familyInformed) { this.familyInformed = familyInformed; }
    public String getFamilyName() { return familyName; }
    public void setFamilyName(String familyName) { this.familyName = familyName; }
    public String getFamilyAttitude() { return familyAttitude; }
    public void setFamilyAttitude(String familyAttitude) { this.familyAttitude = familyAttitude; }
    public String getAlternativeCare() { return alternativeCare; }
    public void setAlternativeCare(String alternativeCare) { this.alternativeCare = alternativeCare; }
    public String getRevisitMethod() { return revisitMethod; }
    public void setRevisitMethod(String revisitMethod) { this.revisitMethod = revisitMethod; }
    public String getRevisitAssignee() { return revisitAssignee; }
    public void setRevisitAssignee(String revisitAssignee) { this.revisitAssignee = revisitAssignee; }
    public LocalDateTime getRevisitDueAt() { return revisitDueAt; }
    public void setRevisitDueAt(LocalDateTime revisitDueAt) { this.revisitDueAt = revisitDueAt; }
}
