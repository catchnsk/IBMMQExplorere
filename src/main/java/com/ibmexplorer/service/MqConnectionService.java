package com.ibmexplorer.service;

import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.MQConstants;
import com.ibmexplorer.dto.request.MqTestConnectionRequest;
import com.ibmexplorer.dto.response.ApiResponse;
import com.ibmexplorer.entity.AuditLogEntity.AuditAction;
import com.ibmexplorer.entity.MqConfigurationEntity;
import com.ibmexplorer.exception.ConfigNotFoundException;
import com.ibmexplorer.exception.MqAuthorizationException;
import com.ibmexplorer.exception.MqConnectionException;
import com.ibmexplorer.repository.MqConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqConnectionService {

    private final MqConfigurationRepository configRepo;
    private final EncryptionService encryptionService;
    private final AuditLogService auditLogService;

    private final ConcurrentHashMap<String, MQQueueManager> activeConnections = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastUsed = new ConcurrentHashMap<>();

    @Value("${app.mq.session-idle-timeout-minutes:30}")
    private int idleTimeoutMinutes;

    public void connect(Long configId, String sessionId, String username, String clientIp) {
        MqConfigurationEntity config = configRepo.findById(configId)
            .orElseThrow(() -> new ConfigNotFoundException(configId));

        String key = buildKey(sessionId, configId);

        // Disconnect existing connection for this config if any
        disconnectKey(key);

        try {
            MQQueueManager qm = buildQueueManager(config);
            activeConnections.put(key, qm);
            lastUsed.put(key, Instant.now());
            log.info("Connected to queue manager '{}' for session {}", config.getQueueManagerName(), sessionId);
            auditLogService.log(username, AuditAction.CONNECT, config.getQueueManagerName(),
                config.getConfigName(), "SUCCESS", null, clientIp);
        } catch (MQException e) {
            String message = mapMqErrorToMessage(e.getReason());
            auditLogService.log(username, AuditAction.CONNECT, config.getQueueManagerName(),
                config.getConfigName(), "FAILURE", message, clientIp);
            if (e.getReason() == 2035) {
                throw new MqAuthorizationException(message);
            }
            throw new MqConnectionException(e.getReason(), message, e);
        }
    }

    public void disconnect(Long configId, String sessionId, String username, String clientIp) {
        String key = buildKey(sessionId, configId);
        String qmName = null;

        MqConfigurationEntity config = configRepo.findById(configId).orElse(null);
        if (config != null) qmName = config.getQueueManagerName();

        disconnectKey(key);
        auditLogService.log(username, AuditAction.DISCONNECT, qmName, null, "SUCCESS", null, clientIp);
    }

    public MQQueueManager getConnection(Long configId, String sessionId) {
        String key = buildKey(sessionId, configId);
        MQQueueManager qm = activeConnections.get(key);
        if (qm == null) {
            throw new MqConnectionException(0, "Not connected. Please connect to a queue manager first.");
        }
        lastUsed.put(key, Instant.now());
        return qm;
    }

    public boolean isConnected(Long configId, String sessionId) {
        return activeConnections.containsKey(buildKey(sessionId, configId));
    }

    public int getActiveConnectionCount() {
        return activeConnections.size();
    }

    public ApiResponse<String> testConnection(MqTestConnectionRequest request, String username, String clientIp) {
        MQQueueManager qm = null;
        try {
            qm = buildQueueManagerFromRequest(request);
            auditLogService.log(username, AuditAction.TEST_CONNECTION, request.getQueueManagerName(),
                request.getHost() + ":" + request.getPort(), "SUCCESS", null, clientIp);
            return ApiResponse.success("Connection successful to queue manager: " + request.getQueueManagerName());
        } catch (MQException e) {
            String message = mapMqErrorToMessage(e.getReason());
            auditLogService.log(username, AuditAction.TEST_CONNECTION, request.getQueueManagerName(),
                request.getHost() + ":" + request.getPort(), "FAILURE", message, clientIp);
            return ApiResponse.<String>builder()
                .success(false)
                .message(message)
                .mqErrorCode(e.getReason())
                .build();
        } finally {
            if (qm != null) {
                try { qm.disconnect(); } catch (MQException ignored) {}
            }
        }
    }

    public void disconnectSession(String sessionId) {
        activeConnections.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(sessionId + ":")) {
                try { entry.getValue().disconnect(); } catch (MQException ignored) {}
                lastUsed.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    @Scheduled(fixedDelay = 300_000)
    public void evictIdleConnections() {
        Instant cutoff = Instant.now().minus(idleTimeoutMinutes, ChronoUnit.MINUTES);
        lastUsed.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(cutoff)) {
                MQQueueManager qm = activeConnections.remove(entry.getKey());
                if (qm != null) {
                    try { qm.disconnect(); } catch (MQException ignored) {}
                    log.info("Evicted idle MQ connection: {}", entry.getKey());
                }
                return true;
            }
            return false;
        });
    }

    private MQQueueManager buildQueueManager(MqConfigurationEntity config) throws MQException {
        Hashtable<String, Object> props = buildConnectionProps(
            config.getHost(), config.getPort(), config.getChannel(),
            config.getUsername(),
            config.getEncryptedPassword() != null ? encryptionService.decrypt(config.getEncryptedPassword()) : null,
            config.getSslCipherSpec()
        );
        return new MQQueueManager(config.getQueueManagerName(), props);
    }

    private MQQueueManager buildQueueManagerFromRequest(MqTestConnectionRequest req) throws MQException {
        Hashtable<String, Object> props = buildConnectionProps(
            req.getHost(), req.getPort(), req.getChannel(),
            req.getUsername(), req.getPassword(), req.getSslCipherSpec()
        );
        return new MQQueueManager(req.getQueueManagerName(), props);
    }

    private Hashtable<String, Object> buildConnectionProps(String host, int port, String channel,
                                                            String username, String password,
                                                            String sslCipherSpec) {
        Hashtable<String, Object> props = new Hashtable<>();
        props.put(MQConstants.HOST_NAME_PROPERTY, host);
        props.put(MQConstants.PORT_PROPERTY, port);
        props.put(MQConstants.CHANNEL_PROPERTY, channel);
        props.put(MQConstants.TRANSPORT_PROPERTY, MQConstants.TRANSPORT_MQSERIES_CLIENT);

        if (username != null && !username.isBlank()) {
            props.put(MQConstants.USER_ID_PROPERTY, username);
        }
        if (password != null && !password.isBlank()) {
            props.put(MQConstants.PASSWORD_PROPERTY, password);
        }
        if (sslCipherSpec != null && !sslCipherSpec.isBlank()) {
            props.put(MQConstants.SSL_CIPHER_SUITE_PROPERTY, sslCipherSpec);
        }
        return props;
    }

    private void disconnectKey(String key) {
        MQQueueManager existing = activeConnections.remove(key);
        lastUsed.remove(key);
        if (existing != null) {
            try { existing.disconnect(); } catch (MQException ignored) {}
        }
    }

    private String buildKey(String sessionId, Long configId) {
        return sessionId + ":" + configId;
    }

    private String mapMqErrorToMessage(int reason) {
        return switch (reason) {
            case 2035 -> "Not authorized (MQRC 2035). Check MQ username, password, and channel authority records.";
            case 2059 -> "Queue Manager unavailable (MQRC 2059). The queue manager may be stopped or the SVRCONN channel is inactive.";
            case 2538 -> "Host not reachable (MQRC 2538). Check hostname, port, and network connectivity.";
            case 2393 -> "SSL/TLS initialization error (MQRC 2393). Check cipher spec and certificate stores.";
            case 2195 -> "Unexpected error (MQRC 2195). Check MQ error logs on the server.";
            case 2063 -> "Security error (MQRC 2063). Check MQSC security configuration.";
            case 2009 -> "Connection broken (MQRC 2009). The MQ connection was lost.";
            case 2016 -> "GET inhibited on queue (MQRC 2016).";
            case 2033 -> "No message available (MQRC 2033).";
            case 2085 -> "Unknown object name (MQRC 2085). The queue does not exist.";
            default   -> "MQ Error (MQRC " + reason + "). See IBM MQ documentation for details.";
        };
    }
}
