package com.community.eldercare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 拒医风险复访任务。
 * 拒医登记后自动生成，要求 24 小时内完成；
 * 复访方式区分：PHONE 电话复访 / VISIT 上门复访 / DOCTOR_REVIEW 医生复诊。
 * 复访结果驱动后续动作：缓解→解除随访；加重→提升风险等级并重新通知家属与医疗人员、
 * 生成下一轮复访；未缓解/未联系上→保持随访并生成下一轮复访。
 */
@Getter
@Setter
@Entity
@Table(name = "risk_follow_task", indexes = {
        @Index(name = "idx_follow_task_status", columnList = "status"),
        @Index(name = "idx_follow_task_refusal", columnList = "refusal_id")
})
public class RiskFollowTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private RefusalRecord refusal;

    /** 冗余关联，便于按事件/老人查询 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private HelpEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Elder elder;

    /** PHONE 电话复访 / VISIT 上门复访 / DOCTOR_REVIEW 医生复诊 */
    @Column(nullable = false)
    private String taskType;

    /** PENDING 待复访 / DONE 已完成 / CANCELLED 已取消 */
    @Column(nullable = false)
    private String status = "PENDING";

    /** 第几轮复访（从 1 开始） */
    private int roundNo = 1;

    /** 复访截止时间（生成后 24 小时内） */
    @Column(nullable = false)
    private LocalDateTime dueAt;

    /** 复访结果：IMPROVED 症状缓解 / UNCHANGED 未缓解 / WORSENED 症状加重 / NO_ANSWER 未联系上 */
    private String result;

    @Column(length = 1000)
    private String resultNote;

    private String completedByName;
    private LocalDateTime completedAt;

    private LocalDateTime createdAt = LocalDateTime.now();
}
