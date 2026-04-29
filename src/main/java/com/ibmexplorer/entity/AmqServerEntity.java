package com.ibmexplorer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "amq_servers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class AmqServerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 255)
    private String host;

    /** ActiveMQ web-console / Jolokia port (default 8161) */
    @Column(nullable = false)
    @Builder.Default
    private Integer managementPort = 8161;

    /** Console HTTP basic-auth username */
    @Column(length = 100)
    private String username;

    @Column(length = 1024)
    private String encryptedPassword;

    // ── SSH credentials ───────────────────────────────────────────────────────

    @Column
    @Builder.Default
    private Integer sshPort = 22;

    /** SSH login user; falls back to username if blank */
    @Column(length = 100)
    private String sshUsername;

    @Column(length = 1024)
    private String encryptedSshPassword;

    // ── Script path ───────────────────────────────────────────────────────────

    /**
     * The OS user that owns the AMQ instance — used in the path:
     * /apps/amq/instances/{instanceUser}/{instanceName}/bin
     * Falls back to sshUsername if blank.
     */
    @Column(length = 100)
    private String instanceUser;

    /** AMQ instance directory name under /apps/amq/instances/{user}/ */
    @Column(length = 100)
    private String instanceName;

    // ── Other fields ──────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Environment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private GroupCategory groupCategory;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private BrokerType brokerType = BrokerType.CLASSIC;

    /** Use HTTPS for management endpoint */
    @Column(nullable = false)
    @Builder.Default
    private Boolean useSsl = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Column(length = 100)
    private String createdBy;

    public enum Environment {
        QA, QA03, PERF
    }

    public enum GroupCategory {
        GROUP_A, GROUP_B
    }

    public enum BrokerType {
        CLASSIC, ARTEMIS
    }
}
