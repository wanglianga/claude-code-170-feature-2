package com.community.eldercare.repo;

import com.community.eldercare.domain.RiskFollowTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskFollowTaskRepository extends JpaRepository<RiskFollowTask, Long> {

    List<RiskFollowTask> findByRefusalIdOrderByCreatedAtAsc(Long refusalId);

    List<RiskFollowTask> findByEventIdOrderByCreatedAtAsc(Long eventId);

    List<RiskFollowTask> findByStatusOrderByDueAtAsc(String status);

    List<RiskFollowTask> findByElderIdOrderByCreatedAtDesc(Long elderId);

    List<RiskFollowTask> findByRefusalIdAndStatus(Long refusalId, String status);
}
