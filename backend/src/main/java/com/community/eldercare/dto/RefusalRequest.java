package com.community.eldercare.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 拒绝就医登记请求：医生建议、老人拒绝原因、家属是否知情与态度、替代照护安排。
 * 登记后自动生成 24 小时内复访任务，事件在随访解除前不能归档。
 */
public class RefusalRequest {

    @NotBlank(message = "请填写判断异常的家庭医生")
    private String doctorName;

    /** 异常发现，如“血压 182/110 mmHg” */
    private String abnormalFinding;

    @NotBlank(message = "请填写医生建议")
    private String doctorAdvice;

    @NotBlank(message = "请填写老人拒绝原因")
    private String refuseReason;

    /** 家属是否知情 */
    private boolean familyInformed;

    /** 家属态度：积极配合/一般/推诿拒绝/未能联系上 */
    private String familyAttitude;

    /** 替代照护安排 */
    private String alternativeCare;

    /** 首轮复访方式：PHONE 电话复访 / VISIT 上门复访 / DOCTOR_REVIEW 医生复诊，默认 PHONE */
    private String followTaskType = "PHONE";

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getAbnormalFinding() { return abnormalFinding; }
    public void setAbnormalFinding(String abnormalFinding) { this.abnormalFinding = abnormalFinding; }
    public String getDoctorAdvice() { return doctorAdvice; }
    public void setDoctorAdvice(String doctorAdvice) { this.doctorAdvice = doctorAdvice; }
    public String getRefuseReason() { return refuseReason; }
    public void setRefuseReason(String refuseReason) { this.refuseReason = refuseReason; }
    public boolean isFamilyInformed() { return familyInformed; }
    public void setFamilyInformed(boolean familyInformed) { this.familyInformed = familyInformed; }
    public String getFamilyAttitude() { return familyAttitude; }
    public void setFamilyAttitude(String familyAttitude) { this.familyAttitude = familyAttitude; }
    public String getAlternativeCare() { return alternativeCare; }
    public void setAlternativeCare(String alternativeCare) { this.alternativeCare = alternativeCare; }
    public String getFollowTaskType() { return followTaskType; }
    public void setFollowTaskType(String followTaskType) { this.followTaskType = followTaskType; }
}
