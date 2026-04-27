package com.ibmexplorer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
    @Index(name = "idx_audit_username", columnList = "username"),
    @Index(name = "idx_audit_action", columnList = "action")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @Column(length = 100)
    private String queueManagerName;

    @Column(length = 200)
    private String targetResource;

    @Column(length = 20)
    private String outcome;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(length = 50)
    private String clientIp;

    public enum AuditAction {
        CONNECT, DISCONNECT, VIEW_QUEUES, BROWSE_MESSAGES,
        VIEW_MESSAGE, SAVE_CONFIG, DELETE_CONFIG, TEST_CONNECTION,
        COHERENCE_STOP, COHERENCE_RESTART
    }
}
