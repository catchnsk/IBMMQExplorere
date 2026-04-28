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

    // ── Public operations ─────────────────────────────────────────────────────

    public CoherenceStatusResponse checkStatus(CoherenceServerEntity server) {
        String script = scriptPath(server, "status.sh");
        String cmd = "bash " + script + " 2>&1; echo \"EXITCODE:$?\"";
        try {
            String raw = executeCommand(server, cmd);
            boolean exited0 = raw.contains("EXITCODE:0");
            String output = sanitize(raw);
            ServiceStatus status = resolveStatus(exited0, output);
            return buildResponse(server, status, output);
        } catch (Exception e) {
            log.warn("SSH status check failed for {}: {}", server.getHost(), e.getMessage());
            return buildResponse(server, ServiceStatus.ERROR, "SSH error: " + e.getMessage());
        }
    }

    public CoherenceStatusResponse stopService(CoherenceServerEntity server) {
        String script = scriptPath(server, "stop.sh");
        String cmd = "bash " + script + " 2>&1; echo \"EXITCODE:$?\"";
        try {
            String raw = executeCommand(server, cmd);
            boolean success = raw.contains("EXITCODE:0");
            return buildResponse(server,
                success ? ServiceStatus.STOPPED : ServiceStatus.ERROR, sanitize(raw));
        } catch (Exception e) {
            log.warn("SSH stop failed for {}: {}", server.getHost(), e.getMessage());
            return buildResponse(server, ServiceStatus.ERROR, "SSH error: " + e.getMessage());
        }
    }

    public CoherenceStatusResponse startService(CoherenceServerEntity server) {
        String script = scriptPath(server, "start.sh");
        String cmd = "bash " + script + " 2>&1; echo \"EXITCODE:$?\"";
        try {
            String raw = executeCommand(server, cmd);
            boolean success = raw.contains("EXITCODE:0");
            return buildResponse(server,
                success ? ServiceStatus.RUNNING : ServiceStatus.ERROR, sanitize(raw));
        } catch (Exception e) {
            log.warn("SSH start failed for {}: {}", server.getHost(), e.getMessage());
            return buildResponse(server, ServiceStatus.ERROR, "SSH error: " + e.getMessage());
        }
    }

    public boolean testConnection(CoherenceServerEntity server) throws Exception {
        executeCommand(server, "echo ok");
        return true;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static final String DEFAULT_BASE = "/apps/bwag/applications/coherence";
    private static final String DEFAULT_INSTANCE = "999";

    /** Full path to a script: {basePath}/{instance}/bin/{script} */
    public static String scriptDir(CoherenceServerEntity server) {
        String base = (server.getScriptBasePath() != null && !server.getScriptBasePath().isBlank())
            ? server.getScriptBasePath() : DEFAULT_BASE;
        String instance = (server.getScriptInstance() != null && !server.getScriptInstance().isBlank())
            ? server.getScriptInstance() : DEFAULT_INSTANCE;
        return base.replaceAll("/+$", "") + "/" + instance + "/bin";
    }

    private String scriptPath(CoherenceServerEntity server, String scriptFile) {
        return scriptDir(server) + "/" + scriptFile;
    }

    private ServiceStatus resolveStatus(boolean exitedZero, String output) {
        String lower = output.toLowerCase();
        if (lower.contains("running") || lower.contains("started")) return ServiceStatus.RUNNING;
        if (lower.contains("stopped") || lower.contains("not running")
                || lower.contains("inactive") || lower.contains("dead")) return ServiceStatus.STOPPED;
        return exitedZero ? ServiceStatus.RUNNING : ServiceStatus.STOPPED;
    }

    private String sanitize(String raw) {
        return raw.replaceAll("EXITCODE:\\d+", "").trim();
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

    // ── SSH execution ─────────────────────────────────────────────────────────

    private String executeCommand(CoherenceServerEntity server, String command) throws Exception {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        try {
            session = jsch.getSession(server.getUsername(), server.getHost(), server.getSshPort());
            String password = encryptionService.decrypt(server.getEncryptedPassword());
            if (password != null) session.setPassword(password);
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
}
