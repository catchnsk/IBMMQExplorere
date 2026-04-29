package com.ibmexplorer.service;

import com.ibmexplorer.dto.response.AmqStatusResponse;
import com.ibmexplorer.dto.response.AmqStatusResponse.ServiceStatus;
import com.ibmexplorer.entity.AmqServerEntity;
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
public class AmqSshService {

    private final EncryptionService encryptionService;

    private static final int SSH_TIMEOUT_MS = 20_000;
    private static final String BASE_PATH   = "/apps/amq/instances";
    private static final String START_CMD   = "sudo /opt/chef/script/chef-cw";

    // ── Public operations ─────────────────────────────────────────────────────

    public AmqStatusResponse checkStatus(AmqServerEntity server) {
        String cmd = cdBin(server) + " ./artemis-service status 2>&1; echo \"EXITCODE:$?\"";
        try {
            String raw = execute(server, cmd);
            boolean exited0 = raw.contains("EXITCODE:0");
            String output = sanitize(raw);
            return build(server, resolveStatus(exited0, output), output);
        } catch (Exception e) {
            log.warn("AMQ status check failed for {}: {}", server.getHost(), e.getMessage());
            return build(server, ServiceStatus.ERROR, "SSH error: " + e.getMessage());
        }
    }

    public AmqStatusResponse stopService(AmqServerEntity server) {
        String cmd = cdBin(server) + " ./artemis-service stop 2>&1; echo \"EXITCODE:$?\"";
        try {
            String raw = execute(server, cmd);
            boolean ok = raw.contains("EXITCODE:0");
            return build(server, ok ? ServiceStatus.STOPPED : ServiceStatus.ERROR, sanitize(raw));
        } catch (Exception e) {
            log.warn("AMQ stop failed for {}: {}", server.getHost(), e.getMessage());
            return build(server, ServiceStatus.ERROR, "SSH error: " + e.getMessage());
        }
    }

    public AmqStatusResponse startService(AmqServerEntity server) {
        String cmd = START_CMD + " 2>&1; echo \"EXITCODE:$?\"";
        try {
            String raw = execute(server, cmd);
            boolean ok = raw.contains("EXITCODE:0");
            return build(server, ok ? ServiceStatus.RUNNING : ServiceStatus.ERROR, sanitize(raw));
        } catch (Exception e) {
            log.warn("AMQ start failed for {}: {}", server.getHost(), e.getMessage());
            return build(server, ServiceStatus.ERROR, "SSH error: " + e.getMessage());
        }
    }

    public boolean testSsh(AmqServerEntity server) throws Exception {
        execute(server, "echo ok");
        return true;
    }

    // ── Path helpers ──────────────────────────────────────────────────────────

    /** /apps/amq/instances/{instanceUser}/{instanceName}/bin */
    public static String binDir(AmqServerEntity server) {
        String user = notBlank(server.getInstanceUser(),
            notBlank(server.getSshUsername(), server.getUsername()));
        String name = notBlank(server.getInstanceName(), "amq");
        return BASE_PATH + "/" + user + "/" + name + "/bin";
    }

    private static String cdBin(AmqServerEntity server) {
        return "cd " + binDir(server) + " &&";
    }

    // ── Status helpers ────────────────────────────────────────────────────────

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

    private AmqStatusResponse build(AmqServerEntity server, ServiceStatus status, String details) {
        return AmqStatusResponse.builder()
            .serverId(server.getId())
            .host(server.getHost())
            .status(status)
            .details(details)
            .checkedAt(LocalDateTime.now())
            .build();
    }

    // ── SSH execution ─────────────────────────────────────────────────────────

    private String execute(AmqServerEntity server, String command) throws Exception {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        try {
            String sshUser = notBlank(server.getSshUsername(), server.getUsername());
            int sshPort = server.getSshPort() != null ? server.getSshPort() : 22;

            session = jsch.getSession(sshUser, server.getHost(), sshPort);

            String password = resolvePassword(server);
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

    private String resolvePassword(AmqServerEntity server) {
        // Prefer dedicated SSH password, fall back to console password
        String encrypted = notBlank(server.getEncryptedSshPassword(), server.getEncryptedPassword());
        return encrypted != null ? encryptionService.decrypt(encrypted) : null;
    }

    private static String notBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
