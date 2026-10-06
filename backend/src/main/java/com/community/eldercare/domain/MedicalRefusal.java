package com.community.eldercare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 拒绝就医记录（拒医随访闭环的起点）。
 * 家庭医生判断老人血压等体征异常、但老人拒绝去医院时，平台必须记录：
 * 医生建议、老人拒绝原因、家属是否知情、替代照护安排。
 * 记录拒医后事件不能直接关闭，而要生成 24 小时内复访任务（{@link RevisitTask}）。
 */
@Getter
@Setter
@Entity
@Table(name = "event_medical_refusal", indexes = {
        @Index(name = "idx_refusal_event", columnList = "event_id"),
        @Index(name = "idx_refusal_resolved", columnList = "resolved")
})
public class MedicalRefusal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private HelpEvent event;

    /** 异常体征描述，如“血压 185/110mmHg，头晕” */
    @Column(length = 1000)
    private String abnormalSign;

    /** 提出就医建议的家庭医生/医疗人员姓名 */
    @Column(nullable = false)
    private String doctorName;

    /** 医生建议（建议就诊科室、风险告知等） */
    @Column(nullable = false, length = 2000)
    private String doctorAdvice;

    /** 老人拒绝就医原因，如“怕花钱/怕麻烦子女/自觉无大碍” */
    @Column(nullable = false, length = 1000)
    private String refuseReason;

    /** 家属是否知情 */
    private boolean familyInformed;
    /** 知情家属姓名 */
    private String familyName;
    /** 家属态度：积极配合送医/尊重老人意愿/拒绝送医/无法联系 等 */
    private String familyAttitude;

    /** 替代照护安排（未送医期间的用药、监测、陪护等） */
    @Column(length = 2000)
    private String alternativeCare;

    /** 记录人（值班社工/医生） */
    private String recordedByName;
    private LocalDateTime recordedAt = LocalDateTime.now();

    /** 随访是否已了结：复访症状缓解转正常归档，或恶化升级到新事件 */
    private boolean resolved = false;
    /** RELIEVED 症状缓解 / WORSENED 症状恶化升级 / UNRELIEVED 未缓解持续随访 */
    private String resolveOutcome;
    /** 恶化升级时生成的新事件 id */
    private Long escalatedEventId;
    private LocalDateTime resolvedAt;
}
