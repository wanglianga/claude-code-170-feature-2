package com.community.eldercare.repo;

import com.community.eldercare.domain.RefusalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefusalRecordRepository extends JpaRepository<RefusalRecord, Long> {

    List<RefusalRecord> findByEventIdOrderByCreatedAtDesc(Long eventId);

    boolean existsByEventIdAndStatus(Long eventId, String status);

    /** 老人最近一次未解除的拒医记录（用于新求助事件自动带入历史） */
    Optional<RefusalRecord> findFirstByElderIdAndStatusOrderByCreatedAtDesc(Long elderId, String status);

    List<RefusalRecord> findByElderIdOrderByCreatedAtDesc(Long elderId);
}
