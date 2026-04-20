package com.ibmexplorer.service;

import com.ibmexplorer.entity.AuditLogEntity;
import com.ibmexplorer.entity.AuditLogEntity.AuditAction;
import com.ibmexplorer.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void log(String username, AuditAction action, String queueManagerName,
                    String targetResource, String outcome, String details, String clientIp) {
        try {
            AuditLogEntity entry = AuditLogEntity.builder()
                .username(username != null ? username : "system")
                .action(action)
                .queueManagerName(queueManagerName)
                .targetResource(targetResource)
                .outcome(outcome)
                .details(sanitize(details))
                .timestamp(LocalDateTime.now())
                .clientIp(clientIp)
                .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to write audit log entry: action={}, user={}", action, username, e);
        }
    }

    public List<AuditLogEntity> getRecentLogs() {
        return auditLogRepository.findTop50ByOrderByTimestampDesc();
    }

    private String sanitize(String details) {
        if (details == null) return null;
        return details.replaceAll("(?i)(password|secret|key|pwd)\\s*[=:]\\s*\\S+", "$1=***");
    }
}
