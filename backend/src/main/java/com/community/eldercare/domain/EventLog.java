package com.community.eldercare.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 事件时间线日志：求助、研判、转介、上门、服务、沟通、回访、归档全过程留痕。
 */
@Getter
@Setter
@Entity
@Table(name = "event_log")
public class EventLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private HelpEvent event;

    /** HELP 求助 / TRIAGE 研判 / DISPATCH 转介 / ONSITE 上门 / SERVICE 服务 /
     *  FAMILY_COMM 家属沟通 / FOLLOW_UP 回访 / CLOSE 归档 / SYSTEM 系统标记 / NOTE 备注 /
     *  REFUSAL 拒医登记与历史带入 / FOLLOW_TASK 拒医复访任务 */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false, length = 1000)
    private String content;

    private String operatorName;
    private LocalDateTime createdAt = LocalDateTime.now();
}
