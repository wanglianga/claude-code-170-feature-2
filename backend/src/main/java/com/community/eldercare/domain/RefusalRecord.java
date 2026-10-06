package com.community.eldercare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 拒绝就医记录（风险随访主档）。
 * 家庭医生判断老人身体异常（如血压异常）建议就医，但老人拒绝时登记：
 * 医生建议、老人拒绝原因、家属是否知情与态度、替代照护安排。
 * 记录存在期间事件不能直接归档，必须完成 24 小时复访闭环；
 * 症状未缓解时，该记录会随老人下一次求助自动带入新事件。
 */
@Getter
@Setter
@Entity
@Table(name = "refusal_record", indexes = {
        @Index(name = "idx_refusal_event", columnList = "event_id"),
        @Index(name = "idx_refusal_elder_status", columnList = "elder_id,status")
})
public class RefusalRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 产生拒医的求助事件 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private HelpEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Elder elder;

    /** 判断异常的家庭医生姓名 */
    @Column(nullable = false)
    private String doctorName;

    /** 异常发现，如“血压 182/110 mmHg，伴头晕” */
    @Column(length = 1000)
    private String abnormalFinding;

    /** 医生建议（如立即就医/急诊观察/调整用药） */
    @Column(nullable = false, length = 2000)
    private String doctorAdvice;

    /** 老人拒绝就医原因（害怕住院/费用顾虑/自觉无碍/无人陪同等） */
    @Column(nullable = false, length = 1000)
    private String refuseReason;

    /** 家属是否知情 */
    private boolean familyInformed;

    /** 家属态度：积极配合/一般/推诿拒绝/未能联系上 */
    private String familyAttitude;

    /** 替代照护安排（居家观察要点、家庭医生每日电话、社区护士上门测压等） */
    @Column(length = 1000)
    private String alternativeCare;

    /** OPEN 随访中（含未缓解持续跟踪） / RESOLVED 症状缓解已解除 */
    @Column(nullable = false)
    private String status = "OPEN";

    /** 当前风险等级 HIGH / CRITICAL（复访加重时提升） */
    @Column(nullable = false)
    private String riskLevel = "HIGH";

    /** 已完成的复访轮次 */
    private int followRounds = 0;

    private String createdByName;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime resolvedAt;
}
