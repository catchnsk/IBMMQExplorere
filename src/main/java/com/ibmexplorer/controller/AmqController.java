package com.ibmexplorer.controller;

import com.ibmexplorer.dto.request.AmqServerRequest;
import com.ibmexplorer.dto.response.AmqQueueInfo;
import com.ibmexplorer.dto.response.AmqServerResponse;
import com.ibmexplorer.dto.response.AmqStatusResponse;
import com.ibmexplorer.dto.response.ApiResponse;
import com.ibmexplorer.entity.AmqServerEntity;
import com.ibmexplorer.entity.AmqServerEntity.BrokerType;
import com.ibmexplorer.exception.ConfigNotFoundException;
import com.ibmexplorer.repository.AmqServerRepository;
import com.ibmexplorer.service.AmqService;
import com.ibmexplorer.service.AmqSshService;
import com.ibmexplorer.service.EncryptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/amq")
@RequiredArgsConstructor
public class AmqController {

    private final AmqServerRepository serverRepo;
    private final AmqService amqService;
    private final AmqSshService sshService;
    private final EncryptionService encryptionService;

    // ── Config CRUD ───────────────────────────────────────────────────────────

    @GetMapping("/servers")
    public ResponseEntity<ApiResponse<List<AmqServerResponse>>> listAll() {
        List<AmqServerResponse> servers = serverRepo
            .findByEnabledTrueOrderByEnvironmentAscGroupCategoryAscDisplayNameAsc()
            .stream().map(this::mapToResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(servers));
    }

    @PostMapping("/servers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AmqServerResponse>> create(
            @Valid @RequestBody AmqServerRequest req, Authentication auth) {
        AmqServerEntity entity = buildEntity(req, auth.getName());
        return ResponseEntity.ok(ApiResponse.success(mapToResponse(serverRepo.save(entity)), "Server added"));
    }

    @PutMapping("/servers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AmqServerResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody AmqServerRequest req,
            Authentication auth) {

        AmqServerEntity e = serverRepo.findById(id).orElseThrow(() -> new ConfigNotFoundException(id));

        e.setDisplayName(req.getDisplayName());
        e.setHost(req.getHost());
        e.setManagementPort(req.getManagementPort() != null ? req.getManagementPort() : 8161);
        e.setUsername(req.getUsername());
        e.setSshPort(req.getSshPort() != null ? req.getSshPort() : 22);
        e.setSshUsername(req.getSshUsername());
        e.setInstanceUser(req.getInstanceUser());
        e.setInstanceName(req.getInstanceName());
        e.setEnvironment(req.getEnvironment());
        e.setGroupCategory(req.getGroupCategory());
        e.setBrokerType(req.getBrokerType() != null ? req.getBrokerType() : BrokerType.CLASSIC);
        e.setUseSsl(Boolean.TRUE.equals(req.getUseSsl()));

        if (hasValue(req.getPassword()))
            e.setEncryptedPassword(encryptionService.encrypt(req.getPassword()));
        if (hasValue(req.getSshPassword()))
            e.setEncryptedSshPassword(encryptionService.encrypt(req.getSshPassword()));

        return ResponseEntity.ok(ApiResponse.success(mapToResponse(serverRepo.save(e)), "Server updated"));
    }

    @DeleteMapping("/servers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        AmqServerEntity e = serverRepo.findById(id).orElseThrow(() -> new ConfigNotFoundException(id));
        e.setEnabled(false);
        serverRepo.save(e);
        return ResponseEntity.ok(ApiResponse.success(null, "Server removed"));
    }

    // ── Queue operations ──────────────────────────────────────────────────────

    @GetMapping("/servers/{id}/queues")
    public ResponseEntity<ApiResponse<List<AmqQueueInfo>>> listQueues(@PathVariable Long id) {
        AmqServerEntity server = serverRepo.findById(id).orElseThrow(() -> new ConfigNotFoundException(id));
        try {
            return ResponseEntity.ok(ApiResponse.success(amqService.listQueues(server)));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.<List<AmqQueueInfo>>builder()
                .success(false).message("Failed to list queues: " + rootMessage(e)).build());
        }
    }

    // ── SSH service operations ────────────────────────────────────────────────

    @GetMapping("/servers/{id}/status")
    public ResponseEntity<ApiResponse<AmqStatusResponse>> checkStatus(@PathVariable Long id) {
        AmqServerEntity server = serverRepo.findById(id).orElseThrow(() -> new ConfigNotFoundException(id));
        return ResponseEntity.ok(ApiResponse.success(sshService.checkStatus(server)));
    }

    @PostMapping("/servers/{id}/stop")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AmqStatusResponse>> stopService(
            @PathVariable Long id, Authentication auth) {
        AmqServerEntity server = serverRepo.findById(id).orElseThrow(() -> new ConfigNotFoundException(id));
        AmqStatusResponse result = sshService.stopService(server);
        return ResponseEntity.ok(ApiResponse.success(result,
            result.getStatus() == AmqStatusResponse.ServiceStatus.STOPPED
                ? "Service stopped" : "stop command executed — check status"));
    }

    @PostMapping("/servers/{id}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AmqStatusResponse>> startService(
            @PathVariable Long id, Authentication auth) {
        AmqServerEntity server = serverRepo.findById(id).orElseThrow(() -> new ConfigNotFoundException(id));
        AmqStatusResponse result = sshService.startService(server);
        return ResponseEntity.ok(ApiResponse.success(result,
            result.getStatus() == AmqStatusResponse.ServiceStatus.RUNNING
                ? "Service started" : "start command executed — check status"));
    }

    // ── Connection tests ──────────────────────────────────────────────────────

    @PostMapping("/servers/test-connection")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> testConnection(@Valid @RequestBody AmqServerRequest req) {
        AmqServerEntity tmp = buildEntity(req, "test");
        try {
            amqService.testConnection(tmp);
            return ResponseEntity.ok(ApiResponse.success("Jolokia connection successful"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(false).message("Connection failed: " + rootMessage(e)).build());
        }
    }

    @PostMapping("/servers/test-ssh")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> testSsh(@Valid @RequestBody AmqServerRequest req) {
        AmqServerEntity tmp = buildEntity(req, "test");
        try {
            sshService.testSsh(tmp);
            return ResponseEntity.ok(ApiResponse.success("SSH connection successful"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(false).message("SSH failed: " + rootMessage(e)).build());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AmqServerEntity buildEntity(AmqServerRequest req, String createdBy) {
        AmqServerEntity e = AmqServerEntity.builder()
            .displayName(req.getDisplayName())
            .host(req.getHost())
            .managementPort(req.getManagementPort() != null ? req.getManagementPort() : 8161)
            .username(req.getUsername())
            .sshPort(req.getSshPort() != null ? req.getSshPort() : 22)
            .sshUsername(req.getSshUsername())
            .instanceUser(req.getInstanceUser())
            .instanceName(req.getInstanceName())
            .environment(req.getEnvironment())
            .groupCategory(req.getGroupCategory())
            .brokerType(req.getBrokerType() != null ? req.getBrokerType() : BrokerType.CLASSIC)
            .useSsl(Boolean.TRUE.equals(req.getUseSsl()))
            .createdBy(createdBy)
            .build();
        if (hasValue(req.getPassword()))
            e.setEncryptedPassword(encryptionService.encrypt(req.getPassword()));
        if (hasValue(req.getSshPassword()))
            e.setEncryptedSshPassword(encryptionService.encrypt(req.getSshPassword()));
        return e;
    }

    private AmqServerResponse mapToResponse(AmqServerEntity e) {
        return AmqServerResponse.builder()
            .id(e.getId())
            .displayName(e.getDisplayName())
            .host(e.getHost())
            .managementPort(e.getManagementPort())
            .username(e.getUsername())
            .hasPassword(hasValue(e.getEncryptedPassword()))
            .sshPort(e.getSshPort())
            .sshUsername(e.getSshUsername())
            .hasSshPassword(hasValue(e.getEncryptedSshPassword()))
            .instanceUser(e.getInstanceUser())
            .instanceName(e.getInstanceName())
            .binDir(AmqSshService.binDir(e))
            .environment(e.getEnvironment())
            .groupCategory(e.getGroupCategory())
            .brokerType(e.getBrokerType())
            .useSsl(Boolean.TRUE.equals(e.getUseSsl()))
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
