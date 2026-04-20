package com.ibmexplorer.repository;

import com.ibmexplorer.entity.AuditLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    Page<AuditLogEntity> findByUsernameOrderByTimestampDesc(String username, Pageable pageable);

    List<AuditLogEntity> findTop50ByOrderByTimestampDesc();

    List<AuditLogEntity> findByTimestampAfterOrderByTimestampDesc(LocalDateTime after);
}
