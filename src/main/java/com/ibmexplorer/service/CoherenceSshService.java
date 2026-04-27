package com.ibmexplorer.service;

import com.ibmexplorer.dto.response.CoherenceStatusResponse;
import com.ibmexplorer.dto.response.CoherenceStatusResponse.ServiceStatus;
import com.ibmexplorer.entity.CoherenceServerEntity;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoherenceSshService {

    private final EncryptionService encryptionService;

    private static final int SSH_TIMEOUT_MS = 15_000;

    public CoherenceStatusResponse checkStatus(CoherenceServerEntity server) {
        String cmd = "systemctl is-active " + server.getServiceName() + " 2>&1";
        try {
            String output = executeCommand(server, cmd).trim();
            ServiceStatus status = switch (output.toLowerCase()) {
                case "active" -> ServiceStatus.RUNNING;
                case "inactive", "failed", "dead" -> ServiceStatus.STOPPED;
                default -> ServiceStatus.UNKNOWN;
            };
            return buildResponse(server, status, output);
        } catch (Exception e) {
            log.warn("SSH status check failed for {}: {}", server.getHost(), e.getMessage());
            return buildResponse(server, ServiceStatus.ERROR, "SSH error: " + e.getMessage());
        }
    }

    public CoherenceStatusResponse stopService(CoherenceServerEntity server) {
        String cmd = "sudo systemctl stop " + server.getServiceName() + " 2>&1; echo \"EXITCODE:$?\"";
        try {
            String output = executeCommand(server, cmd);
            boolean success = output.contains("EXITCODE:0");
            ServiceStatus status = success ? ServiceStatus.STOPPED : ServiceStatus.ERROR;
            return buildResponse(server, status, sanitizeOutput(output));
        } catch (Exception e) {
            log.warn("SSH stop failed for {}: {}", server.getHost(), e.getMessage());
            return buildResponse(server, ServiceStatus.ERROR, "SSH error: " + e.getMessage());
        }
    }

    public CoherenceStatusResponse restartService(CoherenceServerEntity server) {
        String cmd = "sudo systemctl restart " + server.getServiceName() + " 2>&1; echo \"EXITCODE:$?\"";
        try {
            String output = executeCommand(server, cmd);
            boolean success = output.contains("EXITCODE:0");
            ServiceStatus status = success ? ServiceStatus.RUNNING : ServiceStatus.ERROR;
            return buildResponse(server, status, sanitizeOutput(output));
        } catch (Exception e) {
            log.warn("SSH restart failed for {}: {}", server.getHost(), e.getMessage());
            return buildResponse(server, ServiceStatus.ERROR, "SSH error: " + e.getMessage());
        }
    }

    public boolean testConnection(CoherenceServerEntity server) throws Exception {
        executeCommand(server, "echo ok");
        return true;
    }

    private String executeCommand(CoherenceServerEntity server, String command) throws Exception {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        try {
            session = jsch.getSession(server.getUsername(), server.getHost(), server.getSshPort());
            String password = encryptionService.decrypt(server.getEncryptedPassword());
            if (password != null) {
                session.setPassword(password);
            }
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "password,keyboard-interactive");
            session.setTimeout(SSH_TIMEOUT_MS);
            session.connect(SSH_TIMEOUT_MS);

            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            channel.setOutputStream(stdout);
            channel.setErrStream(stderr);
            channel.connect(SSH_TIMEOUT_MS);

            long deadline = System.currentTimeMillis() + SSH_TIMEOUT_MS;
            while (!channel.isClosed() && System.currentTimeMillis() < deadline) {
                Thread.sleep(150);
            }

            return stdout.toString(StandardCharsets.UTF_8)
                 + stderr.toString(StandardCharsets.UTF_8);
        } finally {
            if (channel != null && channel.isConnected()) channel.disconnect();
            if (session != null && session.isConnected()) session.disconnect();
        }
    }

    private CoherenceStatusResponse buildResponse(CoherenceServerEntity server,
                                                   ServiceStatus status, String details) {
        return CoherenceStatusResponse.builder()
            .serverId(server.getId())
            .host(server.getHost())
            .status(status)
            .details(details)
            .checkedAt(LocalDateTime.now())
            .build();
    }

    private String sanitizeOutput(String output) {
        // Remove the EXITCODE marker before returning to the client
        return output.replaceAll("EXITCODE:\\d+", "").trim();
    }
}
