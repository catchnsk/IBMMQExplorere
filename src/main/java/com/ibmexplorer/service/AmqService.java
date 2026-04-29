package com.ibmexplorer.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibmexplorer.dto.response.AmqQueueInfo;
import com.ibmexplorer.entity.AmqServerEntity;
import com.ibmexplorer.entity.AmqServerEntity.BrokerType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AmqService {

    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    private static final int TIMEOUT_SEC = 15;
    private static final int MAX_QUEUES = 500;

    // ── Public operations ─────────────────────────────────────────────────────

    public void testConnection(AmqServerEntity server) throws Exception {
        String url = jolokiaBase(server) + "/version";
        String json = httpGet(server, url);
        JolokiaSimpleResponse r = objectMapper.readValue(json, JolokiaSimpleResponse.class);
        if (r.status != 200) {
            throw new RuntimeException("Jolokia responded with status " + r.status
                + (r.error != null ? ": " + r.error : ""));
        }
    }

    public List<AmqQueueInfo> listQueues(AmqServerEntity server) throws Exception {
        String base = jolokiaBase(server);
        boolean artemis = server.getBrokerType() == BrokerType.ARTEMIS;

        // 1. Search for queue MBeans
        String searchPattern = artemis
            ? "org.apache.activemq.artemis:broker=*,component=addresses,address=*,subcomponent=queues,routing-type=*,queue=*"
            : "org.apache.activemq:type=Broker,destinationType=Queue,*";

        String searchBody = objectMapper.writeValueAsString(
            Map.of("type", "search", "mbean", searchPattern));
        String searchJson = httpPost(server, base, searchBody);

        JolokiaSearchResponse search = objectMapper.readValue(searchJson, JolokiaSearchResponse.class);
        if (search.status != 200) {
            throw new RuntimeException("Jolokia search failed: "
                + (search.error != null ? search.error : "status " + search.status));
        }

        List<String> mbeans = search.value != null ? search.value : List.of();
        if (mbeans.isEmpty()) return List.of();

        // Cap to avoid overly large bulk requests
        List<String> capped = mbeans.size() > MAX_QUEUES ? mbeans.subList(0, MAX_QUEUES) : mbeans;

        // 2. Bulk-read queue attributes
        return readQueueAttributes(server, base, capped, artemis);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String jolokiaBase(AmqServerEntity server) {
        String scheme = Boolean.TRUE.equals(server.getUseSsl()) ? "https" : "http";
        int port = server.getManagementPort() != null ? server.getManagementPort() : 8161;
        String path = server.getBrokerType() == BrokerType.ARTEMIS
            ? "/console/jolokia"
            : "/api/jolokia";
        return scheme + "://" + server.getHost() + ":" + port + path;
    }

    private List<AmqQueueInfo> readQueueAttributes(
            AmqServerEntity server, String base, List<String> mbeans, boolean artemis) throws Exception {

        // Build bulk-read request array
        List<String> attrs = artemis
            ? List.of("MessageCount", "ConsumerCount")
            : List.of("QueueSize", "ConsumerCount", "ProducerCount", "EnqueueCount", "DequeueCount");

        List<Map<String, Object>> bulkReq = new ArrayList<>();
        for (String mbean : mbeans) {
            bulkReq.add(Map.of("type", "read", "mbean", mbean, "attribute", attrs));
        }

        String body = objectMapper.writeValueAsString(bulkReq);
        String json = httpPost(server, base, body);

        List<JolokiaReadItem> items = objectMapper.readValue(json,
            objectMapper.getTypeFactory().constructCollectionType(List.class, JolokiaReadItem.class));

        List<AmqQueueInfo> result = new ArrayList<>();
        for (JolokiaReadItem item : items) {
            if (item.status != 200 || item.value == null) continue;

            String name = extractQueueName(item.request != null ? item.request.mbean : "", artemis);
            if (name == null || name.isBlank()) continue;

            AmqQueueInfo.AmqQueueInfoBuilder b = AmqQueueInfo.builder().name(name);
            if (artemis) {
                b.queueSize(longVal(item.value.get("MessageCount")));
                b.consumerCount(intVal(item.value.get("ConsumerCount")));
            } else {
                b.queueSize(longVal(item.value.get("QueueSize")));
                b.consumerCount(intVal(item.value.get("ConsumerCount")));
                b.producerCount(intVal(item.value.get("ProducerCount")));
                b.enqueueCount(longVal(item.value.get("EnqueueCount")));
                b.dequeueCount(longVal(item.value.get("DequeueCount")));
            }
            result.add(b.build());
        }

        result.sort(Comparator.comparing(AmqQueueInfo::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /** Extract queue name from MBean object name string. */
    private String extractQueueName(String mbean, boolean artemis) {
        // Artemis: ...queue=MY.QUEUE
        // Classic: ...destinationName=MY.QUEUE,...
        String key = artemis ? "queue=" : "destinationName=";
        int idx = mbean.indexOf(key);
        if (idx < 0) return null;
        String rest = mbean.substring(idx + key.length());
        int comma = rest.indexOf(',');
        return comma < 0 ? rest.trim() : rest.substring(0, comma).trim();
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private String httpGet(AmqServerEntity server, String url) throws Exception {
        HttpClient client = buildClient(server);
        HttpRequest req = requestBuilder(server, url).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 401 || resp.statusCode() == 403) {
            throw new RuntimeException("Authentication failed — check username/password");
        }
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " from " + url);
        }
        return resp.body();
    }

    private String httpPost(AmqServerEntity server, String url, String jsonBody) throws Exception {
        HttpClient client = buildClient(server);
        HttpRequest req = requestBuilder(server, url)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            .header("Content-Type", "application/json")
            .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 401 || resp.statusCode() == 403) {
            throw new RuntimeException("Authentication failed — check username/password");
        }
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " from Jolokia");
        }
        return resp.body();
    }

    private HttpRequest.Builder requestBuilder(AmqServerEntity server, String url) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(TIMEOUT_SEC));
        if (server.getUsername() != null && !server.getUsername().isBlank()) {
            String password = "";
            if (server.getEncryptedPassword() != null) {
                password = encryptionService.decrypt(server.getEncryptedPassword());
                if (password == null) password = "";
            }
            String creds = Base64.getEncoder().encodeToString(
                (server.getUsername() + ":" + password).getBytes(StandardCharsets.UTF_8));
            b.header("Authorization", "Basic " + creds);
        }
        return b;
    }

    private HttpClient buildClient(AmqServerEntity server) throws Exception {
        HttpClient.Builder b = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(TIMEOUT_SEC));
        if (Boolean.TRUE.equals(server.getUseSsl())) {
            b.sslContext(trustAllSslContext());
        }
        return b.build();
    }

    /** Trust-all SSL context for internal servers with self-signed certs. */
    private static SSLContext trustAllSslContext() throws Exception {
        TrustManager[] tm = new TrustManager[]{
            new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tm, null);
        return ctx;
    }

    private static long longVal(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }

    private static int intVal(Object v) {
        if (v == null) return 0;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }

    // ── Jolokia response models ───────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class JolokiaSimpleResponse {
        public int status;
        public String error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class JolokiaSearchResponse {
        public int status;
        public String error;
        public List<String> value;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class JolokiaReadItem {
        public int status;
        public String error;
        public Map<String, Object> value;
        public JolokiaMbeanRef request;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class JolokiaMbeanRef {
        public String mbean;
    }
}
