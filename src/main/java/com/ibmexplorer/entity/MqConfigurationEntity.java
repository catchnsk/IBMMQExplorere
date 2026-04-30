package com.ibmexplorer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "mq_configurations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class MqConfigurationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String configName;

    @Column(nullable = false, length = 255)
    private String host;

    @Column(nullable = false)
    private Integer port;

    @Column(nullable = false, length = 100)
    private String queueManagerName;

    @Column(nullable = false, length = 100)
    private String channel;

    @Column(length = 100)
    private String username;

    @Column(length = 1024)
    private String encryptedPassword;

    @Column(length = 255)
    private String sslCipherSpec;

    @Column(length = 512)
    private String keystorePath;

    @Column(length = 1024)
    private String encryptedKeystorePassword;

    @Column(length = 512)
    private String truststorePath;

    @Column(nullable = false)
    @Builder.Default
    private Boolean sslEnabled = false;

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

    @Column(columnDefinition = "TEXT")
    private String monitoredQueueNames;
}
