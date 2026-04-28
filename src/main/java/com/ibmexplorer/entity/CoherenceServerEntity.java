package com.ibmexplorer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "coherence_servers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CoherenceServerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    @Builder.Default
    private Integer sshPort = 22;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(length = 1024)
    private String encryptedPassword;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Environment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private ServerType serverType;

    @Column(length = 255)
    @Builder.Default
    private String scriptBasePath = "/apps/bwag/applications/coherence";

    @Column(length = 50)
    @Builder.Default
    private String scriptInstance = "999";

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
        DEV, QA, QA03, PERF
    }

    public enum ServerType {
        CORE_CACHE, DB_SERVER
    }
}
