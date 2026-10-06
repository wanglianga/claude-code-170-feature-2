package com.community.eldercare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 拒医后 24 小时内复访任务。
 * 记录拒医（{@link MedicalRefusal}）后事件不能直接关闭，必须生成复访任务并按时复访。
 * 复访方式区分：PHONE 电话复访 / ONSITE 上门复访 / DOCTOR 医生复诊。
 */
@Getter
@Setter
@Entity
@Table(name = "event_revisit_task", indexes = {
        @Index(name = "idx_revisit_event", columnList = "event_id"),
        @Index(name = "idx_revisit_status", columnList = "status"),
        @Index(name = "idx_revisit_due", columnList = "dueAt")
})
public class RevisitTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private HelpEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private MedicalRefusal refusal;

    /** PHONE 电话复访 / ONSITE 上门复访 / DOCTOR 医生复诊 */
    @Column(nullable = false)
    private String method;

    /** PENDING 待复访 / COMPLETED 已复访 / OVERDUE 已逾期（由定时任务标记） */
    @Column(nullable = false)
    private String status = "PENDING";

    /** 复访责任人 */
    private String assigneeName;

    /** 复访截止时间（记录拒医后 24 小时内） */
    @Column(nullable = false)
    private LocalDateTime dueAt;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime completedAt;
    private String completedByName;

    /** 复访结果：RELIEVED 症状缓解 / UNRELIEVED 症状未缓解 / WORSENED 症状加重 */
    private String result;

    @Column(length = 2000)
    private String note;

    /** 症状恶化/未缓解而生成的升级新事件 id */
    private Long escalatedEventId;

    /** 是否重新通知了家属（症状加重时） */
    private boolean familyNotified;
    /** 是否重新通知了医疗人员（症状加重时） */
    private boolean medicalNotified;
}
