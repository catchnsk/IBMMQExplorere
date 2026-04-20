package com.ibmexplorer.controller;

import com.ibmexplorer.dto.request.MqConnectionRequest;
import com.ibmexplorer.dto.response.ApiResponse;
import com.ibmexplorer.dto.response.MqConnectionResponse;
import com.ibmexplorer.entity.AuditLogEntity.AuditAction;
import com.ibmexplorer.entity.MqConfigurationEntity;
import com.ibmexplorer.exception.ConfigNotFoundException;
import com.ibmexplorer.repository.MqConfigurationRepository;
import com.ibmexplorer.service.AuditLogService;
import com.ibmexplorer.service.EncryptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mq/config")
@RequiredArgsConstructor
public class MqConfigController {

    private final MqConfigurationRepository configRepo;
    private final EncryptionService encryptionService;
    private final AuditLogService auditLogService;

    @PostMapping("/save")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MqConnectionResponse>> save(
            @Valid @RequestBody MqConnectionRequest request,
            Authentication auth, HttpServletRequest httpReq) {

        MqConfigurationEntity entity = MqConfigurationEntity.builder()
            .configName(request.getConfigName())
            .host(request.getHost())
            .port(request.getPort())
            .queueManagerName(request.getQueueManagerName())
            .channel(request.getChannel())
            .username(request.getUsername())
            .sslCipherSpec(request.getSslCipherSpec())
            .keystorePath(request.getKeystorePath())
            .truststorePath(request.getTruststorePath())
            .sslEnabled(request.getSslEnabled() != null ? request.getSslEnabled() : false)
            .createdBy(auth.getName())
            .build();

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            entity.setEncryptedPassword(encryptionService.encrypt(request.getPassword()));
        }
        if (request.getKeystorePassword() != null && !request.getKeystorePassword().isBlank()) {
            entity.setEncryptedKeystorePassword(encryptionService.encrypt(request.getKeystorePassword()));
        }

        // Preserve existing encrypted password if not provided in update
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            configRepo.findByConfigName(request.getConfigName())
                .ifPresent(existing -> entity.setEncryptedPassword(existing.getEncryptedPassword()));
        }

        MqConfigurationEntity saved = configRepo.save(entity);
        auditLogService.log(auth.getName(), AuditAction.SAVE_CONFIG, null,
            saved.getConfigName(), "SUCCESS", null, getClientIp(httpReq));

        return ResponseEntity.ok(ApiResponse.success(mapToResponse(saved), "Configuration saved successfully"));
    }

    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<MqConnectionResponse>>> getAll() {
        List<MqConnectionResponse> configs = configRepo.findAll().stream()
            .map(this::mapToResponse)
            .toList();
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MqConnectionResponse>> getById(@PathVariable Long id) {
        MqConfigurationEntity config = configRepo.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        return ResponseEntity.ok(ApiResponse.success(mapToResponse(config)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id, Authentication auth, HttpServletRequest httpReq) {
        MqConfigurationEntity config = configRepo.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        configRepo.deleteById(id);
        auditLogService.log(auth.getName(), AuditAction.DELETE_CONFIG, null,
            config.getConfigName(), "SUCCESS", null, getClientIp(httpReq));
        return ResponseEntity.ok(ApiResponse.success(null, "Configuration deleted"));
    }

    private MqConnectionResponse mapToResponse(MqConfigurationEntity e) {
        return MqConnectionResponse.builder()
            .id(e.getId())
            .configName(e.getConfigName())
            .host(e.getHost())
            .port(e.getPort())
            .queueManagerName(e.getQueueManagerName())
            .channel(e.getChannel())
            .username(e.getUsername())
            .hasPassword(e.getEncryptedPassword() != null && !e.getEncryptedPassword().isBlank())
            .sslCipherSpec(e.getSslCipherSpec())
            .keystorePath(e.getKeystorePath())
            .truststorePath(e.getTruststorePath())
            .sslEnabled(e.getSslEnabled())
            .enabled(e.getEnabled())
            .createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt())
            .createdBy(e.getCreatedBy())
            .build();
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
