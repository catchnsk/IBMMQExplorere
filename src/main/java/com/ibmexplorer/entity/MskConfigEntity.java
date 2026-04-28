package com.ibmexplorer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "msk_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MskConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String configName;

    @Column(nullable = false, length = 1000)
    private String bootstrapServers;

    @Column(length = 50)
    private String awsRegion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthType authType;

    // SASL/SCRAM credentials
    @Column(length = 100)
    private String saslUsername;

    @Column(length = 1024)
    private String encryptedSaslPassword;

    // IAM credentials (optional — falls back to env/instance profile if blank)
    @Column(length = 1024)
    private String encryptedAccessKey;

    @Column(length = 1024)
    private String encryptedSecretKey;

    // Session token for temporary IAM credentials (STS / SSO / assumed role)
    @Column(length = 2048)
    private String encryptedSessionToken;

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

    public enum AuthType {
        NONE,        // PLAINTEXT — local/dev Kafka
        SSL,         // SSL only, no client auth
        SASL_SCRAM,  // SASL_SSL + SCRAM-SHA-512
        IAM          // SASL_SSL + AWS MSK IAM
    }
}
