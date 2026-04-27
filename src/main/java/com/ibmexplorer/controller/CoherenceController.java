package com.ibmexplorer.controller;

import com.ibmexplorer.dto.request.CoherenceServerRequest;
import com.ibmexplorer.dto.response.ApiResponse;
import com.ibmexplorer.dto.response.CoherenceServerResponse;
import com.ibmexplorer.dto.response.CoherenceStatusResponse;
import com.ibmexplorer.entity.AuditLogEntity.AuditAction;
import com.ibmexplorer.entity.CoherenceServerEntity;
import com.ibmexplorer.exception.ConfigNotFoundException;
import com.ibmexplorer.repository.CoherenceServerRepository;
import com.ibmexplorer.service.AuditLogService;
import com.ibmexplorer.service.CoherenceSshService;
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
@RequestMapping("/api/coherence")
@RequiredArgsConstructor
public class CoherenceController {

    private final CoherenceServerRepository serverRepo;
    private final CoherenceSshService sshService;
    private final EncryptionService encryptionService;
    private final AuditLogService auditLogService;

    @GetMapping("/servers")
    public ResponseEntity<ApiResponse<List<CoherenceServerResponse>>> listAll() {
        List<CoherenceServerResponse> servers = serverRepo
            .findByEnabledTrueOrderByEnvironmentAscServerTypeAscDisplayNameAsc()
            .stream().map(this::mapToResponse).toList();
        return ResponseEntity.ok(ApiResponse.success(servers));
    }

    @PostMapping("/servers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CoherenceServerResponse>> create(
            @Valid @RequestBody CoherenceServerRequest req,
            Authentication auth, HttpServletRequest httpReq) {

        CoherenceServerEntity entity = buildEntity(req, auth.getName());
        CoherenceServerEntity saved = serverRepo.save(entity);
        return ResponseEntity.ok(ApiResponse.success(mapToResponse(saved), "Server added"));
    }

    @PutMapping("/servers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CoherenceServerResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody CoherenceServerRequest req,
            Authentication auth) {

        CoherenceServerEntity existing = serverRepo.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));

        existing.setDisplayName(req.getDisplayName());
        existing.setHost(req.getHost());
        existing.setSshPort(req.getSshPort() != null ? req.getSshPort() : 22);
        existing.setUsername(req.getUsername());
        existing.setEnvironment(req.getEnvironment());
        existing.setServerType(req.getServerType());
        existing.setServiceName(req.getServiceName() != null && !req.getServiceName().isBlank()
            ? req.getServiceName() : "coherence");

        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            existing.setEncryptedPassword(encryptionService.encrypt(req.getPassword()));
        }

        CoherenceServerEntity saved = serverRepo.save(existing);
        return ResponseEntity.ok(ApiResponse.success(mapToResponse(saved), "Server updated"));
    }

    @DeleteMapping("/servers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        CoherenceServerEntity server = serverRepo.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        server.setEnabled(false);
        serverRepo.save(server);
        return ResponseEntity.ok(ApiResponse.success(null, "Server removed"));
    }

    @GetMapping("/servers/{id}/status")
    public ResponseEntity<ApiResponse<CoherenceStatusResponse>> checkStatus(@PathVariable Long id) {
        CoherenceServerEntity server = serverRepo.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        CoherenceStatusResponse result = sshService.checkStatus(server);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/servers/{id}/stop")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CoherenceStatusResponse>> stopService(
            @PathVariable Long id, Authentication auth, HttpServletRequest httpReq) {

        CoherenceServerEntity server = serverRepo.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        CoherenceStatusResponse result = sshService.stopService(server);
        auditLogService.log(auth.getName(), AuditAction.COHERENCE_STOP, null,
            server.getHost(), result.getStatus().name(), server.getServiceName(), getClientIp(httpReq));
        return ResponseEntity.ok(ApiResponse.success(result,
            result.getStatus() == CoherenceStatusResponse.ServiceStatus.STOPPED
                ? "Service stopped successfully" : "Stop command sent, check status"));
    }

    @PostMapping("/servers/{id}/restart")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CoherenceStatusResponse>> restartService(
            @PathVariable Long id, Authentication auth, HttpServletRequest httpReq) {

        CoherenceServerEntity server = serverRepo.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        CoherenceStatusResponse result = sshService.restartService(server);
        auditLogService.log(auth.getName(), AuditAction.COHERENCE_RESTART, null,
            server.getHost(), result.getStatus().name(), server.getServiceName(), getClientIp(httpReq));
        return ResponseEntity.ok(ApiResponse.success(result,
            result.getStatus() == CoherenceStatusResponse.ServiceStatus.RUNNING
                ? "Service restarted successfully" : "Restart command sent, check status"));
    }

    @PostMapping("/servers/test-ssh")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> testSsh(
            @Valid @RequestBody CoherenceServerRequest req) {
        CoherenceServerEntity tmp = buildEntity(req, "test");
        try {
            sshService.testConnection(tmp);
            return ResponseEntity.ok(ApiResponse.success("SSH connection successful"));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(false).message("SSH connection failed: " + e.getMessage()).build());
        }
    }

    private CoherenceServerEntity buildEntity(CoherenceServerRequest req, String createdBy) {
        CoherenceServerEntity entity = CoherenceServerEntity.builder()
            .displayName(req.getDisplayName())
            .host(req.getHost())
            .sshPort(req.getSshPort() != null ? req.getSshPort() : 22)
            .username(req.getUsername())
            .environment(req.getEnvironment())
            .serverType(req.getServerType())
            .serviceName(req.getServiceName() != null && !req.getServiceName().isBlank()
                ? req.getServiceName() : "coherence")
            .createdBy(createdBy)
            .build();
        if (req.getPassword() != null && !req.getPassword().isBlank()) {
            entity.setEncryptedPassword(encryptionService.encrypt(req.getPassword()));
        }
        return entity;
    }

    private CoherenceServerResponse mapToResponse(CoherenceServerEntity e) {
        return CoherenceServerResponse.builder()
            .id(e.getId())
            .displayName(e.getDisplayName())
            .host(e.getHost())
            .sshPort(e.getSshPort())
            .username(e.getUsername())
            .hasPassword(e.getEncryptedPassword() != null && !e.getEncryptedPassword().isBlank())
            .environment(e.getEnvironment())
            .serverType(e.getServerType())
            .serviceName(e.getServiceName())
            .enabled(e.getEnabled())
            .createdAt(e.getCreatedAt())
            .createdBy(e.getCreatedBy())
            .build();
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
