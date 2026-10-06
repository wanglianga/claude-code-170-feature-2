package com.community.eldercare.repo;

import com.community.eldercare.domain.MedicalRefusal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicalRefusalRepository extends JpaRepository<MedicalRefusal, Long> {
    List<MedicalRefusal> findByEventIdOrderByRecordedAtAsc(Long eventId);

    /** 未了结的拒医记录（事件因此不能关闭） */
    List<MedicalRefusal> findByResolvedFalse();
}
