package com.ibmexplorer.controller;

import com.ibmexplorer.dto.request.MskConfigRequest;
import com.ibmexplorer.dto.response.*;
import com.ibmexplorer.entity.MskConfigEntity;
import com.ibmexplorer.exception.ConfigNotFoundException;
import com.ibmexplorer.repository.MskConfigRepository;
import com.ibmexplorer.service.EncryptionService;
import com.ibmexplorer.service.MskKafkaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/msk")
@RequiredArgsConstructor
public class MskController {

    private final MskConfigRepository configRepo;
    private final MskKafkaService kafkaService;
    private final EncryptionService encryptionService;

    // ── Config CRUD ──────────────────────────────────────────────────────────

    @GetMapping("/configs")
    public ResponseEntity<ApiResponse<List<MskConfigResponse>>> listConfigs() {
        List<MskConfigResponse> list = configRepo.findByEnabledTrueOrderByConfigNameAsc()
            .stream().map(this::mapToResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping("/configs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MskConfigResponse>> createConfig(
            @Valid @RequestBody MskConfigRequest req, Authentication auth) {
        MskConfigEntity entity = buildEntity(req, auth.getName());
        MskConfigEntity saved = configRepo.save(entity);
        return ResponseEntity.ok(ApiResponse.success(mapToResponse(saved), "Configuration saved"));
    }

    @PutMapping("/configs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MskConfigResponse>> updateConfig(
            @PathVariable Long id,
            @Valid @RequestBody MskConfigRequest req,
            Authentication auth) {

        MskConfigEntity existing = configRepo.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));

        existing.setConfigName(req.getConfigName());
        existing.setBootstrapServers(req.getBootstrapServers());
        existing.setAwsRegion(req.getAwsRegion());
        existing.setAuthType(req.getAuthType());
        existing.setSaslUsername(req.getSaslUsername());

        if (hasValue(req.getSaslPassword()))
            existing.setEncryptedSaslPassword(encryptionService.encrypt(req.getSaslPassword()));
        if (hasValue(req.getAccessKey()))
            existing.setEncryptedAccessKey(encryptionService.encrypt(req.getAccessKey()));
        if (hasValue(req.getSecretKey()))
            existing.setEncryptedSecretKey(encryptionService.encrypt(req.getSecretKey()));
        if (hasValue(req.getSessionToken()))
            existing.setEncryptedSessionToken(encryptionService.encrypt(req.getSessionToken()));
        else if (req.getSessionToken() != null && req.getSessionToken().isEmpty())
            existing.setEncryptedSessionToken(null);  // explicit clear when empty string sent

        MskConfigEntity saved = configRepo.save(existing);
        return ResponseEntity.ok(ApiResponse.success(mapToResponse(saved), "Configuration updated"));
    }

    @DeleteMapping("/configs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(@PathVariable Long id) {
        MskConfigEntity config = configRepo.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        config.setEnabled(false);
        configRepo.save(config);
        return ResponseEntity.ok(ApiResponse.success(null, "Configuration removed"));
    }

    @PostMapping("/configs/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> testConnection(
            @Valid @RequestBody MskConfigRequest req, Authentication auth) {
        MskConfigEntity tmp = buildEntity(req, auth.getName());
        try {
            kafkaService.testConnection(tmp);
            return ResponseEntity.ok(ApiResponse.success("Connection successful"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(false).message("Connection failed: " + rootMessage(e)).build());
        }
    }

    // ── Kafka operations ─────────────────────────────────────────────────────

    @GetMapping("/configs/{id}/topics")
    public ResponseEntity<ApiResponse<List<KafkaTopicInfo>>> listTopics(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean includeInternal) {
        MskConfigEntity config = configRepo.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        try {
            List<KafkaTopicInfo> topics = kafkaService.listTopics(config, includeInternal);
            return ResponseEntity.ok(ApiResponse.success(topics));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.<List<KafkaTopicInfo>>builder()
                .success(false).message("Failed to list topics: " + rootMessage(e)).build());
        }
    }

    @GetMapping("/configs/{id}/topics/{topic}/messages")
    public ResponseEntity<ApiResponse<List<KafkaMessageRecord>>> browseMessages(
            @PathVariable Long id,
            @PathVariable String topic,
            @RequestParam(defaultValue = "50") int limit) {
        MskConfigEntity config = configRepo.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        int cappedLimit = Math.min(limit, 500);
        try {
            List<KafkaMessageRecord> records =
                kafkaService.browseMessages(config, topic, cappedLimit);
            return ResponseEntity.ok(ApiResponse.success(records));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.<List<KafkaMessageRecord>>builder()
                .success(false).message("Failed to browse messages: " + rootMessage(e)).build());
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private MskConfigEntity buildEntity(MskConfigRequest req, String createdBy) {
        MskConfigEntity e = MskConfigEntity.builder()
            .configName(req.getConfigName())
            .bootstrapServers(req.getBootstrapServers())
            .awsRegion(req.getAwsRegion())
            .authType(req.getAuthType())
            .saslUsername(req.getSaslUsername())
            .createdBy(createdBy)
            .build();
        if (hasValue(req.getSaslPassword()))
            e.setEncryptedSaslPassword(encryptionService.encrypt(req.getSaslPassword()));
        if (hasValue(req.getAccessKey()))
            e.setEncryptedAccessKey(encryptionService.encrypt(req.getAccessKey()));
        if (hasValue(req.getSecretKey()))
            e.setEncryptedSecretKey(encryptionService.encrypt(req.getSecretKey()));
        if (hasValue(req.getSessionToken()))
            e.setEncryptedSessionToken(encryptionService.encrypt(req.getSessionToken()));
        return e;
    }

    private MskConfigResponse mapToResponse(MskConfigEntity e) {
        return MskConfigResponse.builder()
            .id(e.getId())
            .configName(e.getConfigName())
            .bootstrapServers(e.getBootstrapServers())
            .awsRegion(e.getAwsRegion())
            .authType(e.getAuthType())
            .saslUsername(e.getSaslUsername())
            .hasSaslPassword(hasValue(e.getEncryptedSaslPassword()))
            .hasIamCredentials(hasValue(e.getEncryptedAccessKey()))
            .hasSessionToken(hasValue(e.getEncryptedSessionToken()))
            .enabled(e.getEnabled())
            .createdAt(e.getCreatedAt())
            .createdBy(e.getCreatedBy())
            .build();
    }

    private boolean hasValue(String s) { return s != null && !s.isBlank(); }

    private String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : t.getMessage();
    }
}
