package com.community.eldercare.repo;

import com.community.eldercare.domain.RevisitTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface RevisitTaskRepository extends JpaRepository<RevisitTask, Long> {
    List<RevisitTask> findByEventIdOrderByCreatedAtAsc(Long eventId);

    /** 工作台：待复访任务，按截止时间升序 */
    List<RevisitTask> findByStatusOrderByDueAtAsc(String status);

    /** 定时任务：已到点但仍待复访的任务 */
    List<RevisitTask> findByStatusAndDueAtBefore(String status, LocalDateTime now);

    long countByStatus(String status);
}
