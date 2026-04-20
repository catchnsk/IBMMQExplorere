package com.ibmexplorer.controller;

import com.ibmexplorer.dto.request.BrowseFilter;
import com.ibmexplorer.dto.request.MqTestConnectionRequest;
import com.ibmexplorer.dto.response.*;
import com.ibmexplorer.entity.AuditLogEntity;
import com.ibmexplorer.service.AuditLogService;
import com.ibmexplorer.service.MqConnectionService;
import com.ibmexplorer.service.MqMessageBrowseService;
import com.ibmexplorer.service.MqQueueService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mq")
@RequiredArgsConstructor
public class MqExplorerController {

    private final MqConnectionService connectionService;
    private final MqQueueService queueService;
    private final MqMessageBrowseService browseService;
    private final AuditLogService auditLogService;

    @PostMapping("/connect")
    public ResponseEntity<ApiResponse<Void>> connect(
            @RequestParam Long configId,
            Authentication auth, HttpSession session, HttpServletRequest req) {
        connectionService.connect(configId, session.getId(), auth.getName(), getClientIp(req));
        return ResponseEntity.ok(ApiResponse.success(null, "Connected successfully"));
    }

    @PostMapping("/disconnect")
    public ResponseEntity<ApiResponse<Void>> disconnect(
            @RequestParam Long configId,
            Authentication auth, HttpSession session, HttpServletRequest req) {
        connectionService.disconnect(configId, session.getId(), auth.getName(), getClientIp(req));
        return ResponseEntity.ok(ApiResponse.success(null, "Disconnected successfully"));
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status(
            @RequestParam Long configId, HttpSession session) {
        boolean connected = connectionService.isConnected(configId, session.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("connected", connected, "configId", configId)));
    }

    @PostMapping("/test-connection")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> testConnection(
            @Valid @RequestBody MqTestConnectionRequest request,
            Authentication auth, HttpServletRequest req) {
        ApiResponse<String> result = connectionService.testConnection(
            request, auth.getName(), getClientIp(req));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/queues")
    public ResponseEntity<ApiResponse<List<QueueInfoResponse>>> listQueues(
            @RequestParam Long configId,
            @RequestParam(defaultValue = "false") boolean includeSystemQueues,
            Authentication auth, HttpSession session, HttpServletRequest req) {
        List<QueueInfoResponse> queues = queueService.listQueues(
            configId, session.getId(), includeSystemQueues, auth.getName(), getClientIp(req));
        return ResponseEntity.ok(ApiResponse.success(queues));
    }

    @GetMapping("/queues/{queueName}/messages")
    public ResponseEntity<ApiResponse<List<MessageSummaryResponse>>> browseMessages(
            @RequestParam Long configId,
            @PathVariable String queueName,
            @RequestParam(required = false) String correlationId,
            @RequestParam(required = false) String messageId,
            @RequestParam(defaultValue = "100") int limit,
            Authentication auth, HttpSession session, HttpServletRequest req) {

        BrowseFilter filter = BrowseFilter.builder()
            .correlationId(correlationId)
            .messageId(messageId)
            .limit(Math.min(Math.max(limit, 1), 500))
            .build();

        List<MessageSummaryResponse> messages = browseService.browseMessages(
            configId, session.getId(), queueName, filter, auth.getName(), getClientIp(req));
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @GetMapping("/queues/{queueName}/messages/{messageId}")
    public ResponseEntity<ApiResponse<MessageDetailResponse>> getMessage(
            @RequestParam Long configId,
            @PathVariable String queueName,
            @PathVariable String messageId,
            Authentication auth, HttpSession session, HttpServletRequest req) {

        MessageDetailResponse detail = browseService.browseMessageById(
            configId, session.getId(), queueName, messageId, auth.getName(), getClientIp(req));
        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "status", "UP",
            "activeConnections", connectionService.getActiveConnectionCount(),
            "timestamp", LocalDateTime.now()
        )));
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<AuditLogEntity>>> getAuditLog() {
        return ResponseEntity.ok(ApiResponse.success(auditLogService.getRecentLogs()));
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }
}
