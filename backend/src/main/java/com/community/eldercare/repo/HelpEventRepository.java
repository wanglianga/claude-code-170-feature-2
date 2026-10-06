package com.community.eldercare.repo;

import com.community.eldercare.domain.HelpEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HelpEventRepository extends JpaRepository<HelpEvent, Long> {

    List<HelpEvent> findByElderIdOrderByCreatedAtDesc(Long elderId);

    List<HelpEvent> findByStatusNotOrderByCreatedAtDesc(String status);

    List<HelpEvent> findByStatusOrderByCreatedAtDesc(String status);

    long countByStatus(String status);

    @Query("select e from HelpEvent e where e.elder.id = :elderId and e.nightHelp = true and e.createdAt >= :since order by e.createdAt desc")
    List<HelpEvent> findNightHelpsSince(@Param("elderId") Long elderId, @Param("since") LocalDateTime since);

    @Query("select e from HelpEvent e where e.createdAt >= :from and e.createdAt < :to order by e.createdAt desc")
    List<HelpEvent> findByTimeRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<HelpEvent> findAllByOrderByCreatedAtDesc();

    /** 由拒医随访升级生成的新事件（relatedEventId 指向原拒医事件） */
    List<HelpEvent> findByRelatedEventId(Long relatedEventId);
}
