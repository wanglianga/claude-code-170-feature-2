package com.community.eldercare.notify;

import com.community.eldercare.service.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 拒医复访时效监控：每分钟扫描到期未复访任务，标记逾期并向值班工作台推送提醒。
 */
@Component
public class RevisitScheduler {

    private static final Logger log = LoggerFactory.getLogger(RevisitScheduler.class);

    private final EventService eventService;

    public RevisitScheduler(EventService eventService) {
        this.eventService = eventService;
    }

    @Scheduled(fixedDelayString = "${app.refusal-scan-ms:60000}")
    public void scanOverdue() {
        try {
            int n = eventService.markOverdueRevisits();
            if (n > 0) {
                log.warn("标记 {} 个拒医 24 小时复访任务逾期", n);
            }
        } catch (Exception e) {
            log.error("拒医复访逾期扫描失败", e);
        }
    }
}
