package com.community.eldercare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 求助事件聚合根。
 * 一条事件贯穿：求助 → 研判 → 转介/通知 → 上门核实 → 后续服务 → 家属沟通 → 回访 → 归档，
 * 并在归档时联动更新老人照护画像。
 */
@Getter
@Setter
@Entity
@Table(name = "help_event", indexes = {
        @Index(name = "idx_event_elder", columnList = "elder_id"),
        @Index(name = "idx_event_status", columnList = "status"),
        @Index(name = "idx_event_created", columnList = "created_at")
})
public class HelpEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 事件编号，如 E20261005001 */
    @Column(nullable = false, unique = true)
    private String eventNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Elder elder;

    /** NEW 待研判 / TRIAGED 已研判 / DISPATCHED 已转介 / ONSITE 已上门 / SERVICED 已服务 /
     *  COMMUNICATED 已沟通家属 / REFUSAL_TRACK 拒医随访中 / FOLLOWED_UP 待回访归档 / CLOSED 已归档 */
    @Column(nullable = false)
    private String status = "NEW";

    /** 拒医随访闭环关联的事件 id：
     *  拒医事件（relationType=REFUSAL_ROOT）复访症状恶化时生成的新事件指向原拒医事件；
     *  新事件（relationType=ESCALATED_FROM_REFUSAL）携带原拒医记录、家属态度与医生建议，防止被当成普通求助。 */
    private Long relatedEventId;
    /** REFUSAL_ROOT 原拒医事件 / ESCALATED_FROM_REFUSAL 拒医恶化升级事件 */
    private String relationType;

    /* ---------------- 求助信息 ---------------- */

    /** 求助来源 SMART_BUTTON 智能按钮 / PHONE 电话 / MINI_APP 社区小程序 */
    @Column(nullable = false)
    private String source;

    /** 来源补充说明，如电话分机、按钮设备号、小程序openid */
    private String sourceDetail;

    /** 当前语音内容（值班社工听取/转写） */
    @Column(length = 2000)
    private String voiceContent;

    /** 语音文件地址 */
    private String audioUrl;

    /** 求助定位 */
    private Double callLongitude;
    private Double callLatitude;
    private String callAddress;

    /** 是否定位偏移、偏移距离（米） */
    private boolean locationOffset;
    private Integer locationOffsetMeters;

    /** 是否夜间求助（22:00-次日06:00） */
    private boolean nightHelp;

    /* ---------------- 风险标记（可叠加，逗号分隔） ----------------
       CANNOT_OPEN_DOOR 无法开门 / LOCATION_OFFSET 定位偏移 / FAMILY_UNREACHABLE 家属长期不接 /
       NEIGHBOR_INFO_UNCLEAR 邻居代报信息不清 / REFUSE_HOSPITAL 老人拒绝就医 /
       HOME_SAFETY_HAZARD 家中存在安全隐患 / FREQUENT_NIGHT_HELP 频繁夜间求助 */
    @Column(length = 500)
    private String riskFlags = "";

    /* ---------------- 研判 ---------------- */

    /** LIFE_ASSIST 生活协助 / BODY_DISCOMFORT 身体不适 / FALL_RISK 跌倒风险 /
     *  EMOTIONAL_CRISIS 情绪危机 / FALSE_TRIGGER 误触 */
    private String triageType;
    /** 严重程度 LOW / MEDIUM / HIGH / CRITICAL */
    private String severity;
    /** 研判说明 */
    @Column(length = 2000)
    private String triageNote;
    private String triageWorkerName;
    private LocalDateTime triagedAt;

    /* ---------------- 处置结论 ---------------- */

    /** 主要处置/服务类型：日常巡访、助餐、适老化改造、心理陪伴、临时照护、送医等 */
    private String serviceType;
    /** 是否送医（120） */
    private boolean hospitalTransfer;
    @Column(length = 2000)
    private String resolutionNote;

    /** 回访结果 IMPROVED 好转 / STABLE 平稳 / WORSENED 恶化 / NO_ANSWER 未联系上 */
    private String followUpResult;
    @Column(length = 1000)
    private String followUpNote;
    private LocalDateTime followedUpAt;

    /* ---------------- 时效 ---------------- */

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime dispatchedAt;
    private LocalDateTime arrivedAt;
    private LocalDateTime closedAt;

    /** 响应时长（分钟）：上门到达 - 求助发起 */
    private Integer responseMinutes;

    /** 是否已联动更新老人画像 */
    private boolean profileUpdated = false;

    @PreUpdate
    public void preUpdate() {
    }
}
