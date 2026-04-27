package com.ibmexplorer.service;

import com.ibmexplorer.dto.response.KafkaMessageRecord;
import com.ibmexplorer.dto.response.KafkaTopicInfo;
import com.ibmexplorer.entity.MskConfigEntity;
import com.ibmexplorer.entity.MskConfigEntity.AuthType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MskKafkaService {

    private final EncryptionService encryptionService;

    // Guards System.setProperty for IAM credential injection
    private static final ReentrantLock IAM_LOCK = new ReentrantLock();
    private static final int ADMIN_TIMEOUT_MS = 15_000;
    private static final DateTimeFormatter TS_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    // ── Topic listing ────────────────────────────────────────────────────────

    public List<KafkaTopicInfo> listTopics(MskConfigEntity config, boolean includeInternal) {
        return withCredentials(config, () -> {
            Properties props = buildAdminProps(config);
            try (AdminClient admin = AdminClient.create(props)) {
                ListTopicsOptions opts = new ListTopicsOptions()
                    .listInternal(includeInternal)
                    .timeoutMs(ADMIN_TIMEOUT_MS);
                Set<String> names = admin.listTopics(opts).names().get();

                DescribeTopicsResult desc = admin.describeTopics(new ArrayList<>(names));
                Map<String, TopicDescription> descriptions = desc.allTopicNames().get();

                // Collect offsets for all partitions
                Map<TopicPartition, OffsetSpec> offsetReq = new HashMap<>();
                Map<TopicPartition, OffsetSpec> earliestReq = new HashMap<>();
                for (TopicDescription td : descriptions.values()) {
                    td.partitions().forEach(p -> {
                        TopicPartition tp = new TopicPartition(td.name(), p.partition());
                        offsetReq.put(tp, OffsetSpec.latest());
                        earliestReq.put(tp, OffsetSpec.earliest());
                    });
                }

                Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets =
                    admin.listOffsets(offsetReq).all().get();
                Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> earliestOffsets =
                    admin.listOffsets(earliestReq).all().get();

                return descriptions.values().stream()
                    .sorted(Comparator.comparing(TopicDescription::name))
                    .map(td -> buildTopicInfo(td, latestOffsets, earliestOffsets))
                    .collect(Collectors.toList());
            }
        });
    }

    private KafkaTopicInfo buildTopicInfo(TopicDescription td,
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest,
            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> earliest) {

        long totalLatest = 0, totalEarliest = 0;
        for (var p : td.partitions()) {
            TopicPartition tp = new TopicPartition(td.name(), p.partition());
            totalLatest   += Optional.ofNullable(latest.get(tp))  .map(i -> i.offset()).orElse(0L);
            totalEarliest += Optional.ofNullable(earliest.get(tp)).map(i -> i.offset()).orElse(0L);
        }

        int rf = td.partitions().isEmpty() ? 0 : td.partitions().get(0).replicas().size();

        return KafkaTopicInfo.builder()
            .name(td.name())
            .partitions(td.partitions().size())
            .replicationFactor(rf)
            .totalMessages(Math.max(0, totalLatest - totalEarliest))
            .latestOffset(totalLatest)
            .internal(td.isInternal())
            .build();
    }

    // ── Message browsing ─────────────────────────────────────────────────────

    public List<KafkaMessageRecord> browseMessages(MskConfigEntity config,
                                                    String topic, int limit) {
        return withCredentials(config, () -> {
            Properties props = buildConsumerProps(config);
            try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
                List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
                    .map(p -> new TopicPartition(topic, p.partition()))
                    .collect(Collectors.toList());

                consumer.assign(partitions);

                // Find offsets to seek from (tail - limit/numPartitions)
                Map<TopicPartition, Long> latestOffsets = consumer.endOffsets(partitions);
                Map<TopicPartition, Long> earliestOffsets = consumer.beginningOffsets(partitions);
                int perPartition = Math.max(1, limit / Math.max(1, partitions.size()));

                for (TopicPartition tp : partitions) {
                    long latest   = latestOffsets.getOrDefault(tp, 0L);
                    long earliest = earliestOffsets.getOrDefault(tp, 0L);
                    long start    = Math.max(earliest, latest - perPartition);
                    consumer.seek(tp, start);
                }

                List<KafkaMessageRecord> records = new ArrayList<>();
                long deadline = System.currentTimeMillis() + 8_000;
                while (records.size() < limit && System.currentTimeMillis() < deadline) {
                    ConsumerRecords<String, byte[]> polled =
                        consumer.poll(Duration.ofMillis(500));
                    if (polled.isEmpty()) break;
                    for (ConsumerRecord<String, byte[]> r : polled) {
                        records.add(mapRecord(r));
                        if (records.size() >= limit) break;
                    }
                }

                records.sort(Comparator.comparingLong(KafkaMessageRecord::getOffset));
                return records;
            }
        });
    }

    // ── Test connection ──────────────────────────────────────────────────────

    public void testConnection(MskConfigEntity config) {
        withCredentials(config, () -> {
            Properties props = buildAdminProps(config);
            try (AdminClient admin = AdminClient.create(props)) {
                admin.listTopics(new ListTopicsOptions().timeoutMs(10_000)).names().get();
            }
            return null;
        });
    }

    // ── Properties builders ──────────────────────────────────────────────────

    private Properties buildAdminProps(MskConfigEntity config) {
        Properties p = commonProps(config);
        p.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(ADMIN_TIMEOUT_MS));
        return p;
    }

    private Properties buildConsumerProps(MskConfigEntity config) {
        Properties p = commonProps(config);
        p.put(ConsumerConfig.GROUP_ID_CONFIG, "msk-explorer-" + System.currentTimeMillis());
        p.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        p.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        p.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");
        p.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, "15000");
        return p;
    }

    private Properties commonProps(MskConfigEntity config) {
        Properties p = new Properties();
        p.put("bootstrap.servers", config.getBootstrapServers());
        p.put("client.id", "msk-explorer");

        switch (config.getAuthType()) {
            case NONE -> p.put("security.protocol", "PLAINTEXT");
            case SSL  -> p.put("security.protocol", "SSL");
            case SASL_SCRAM -> {
                p.put("security.protocol", "SASL_SSL");
                p.put("sasl.mechanism", "SCRAM-SHA-512");
                String password = encryptionService.decrypt(config.getEncryptedSaslPassword());
                p.put("sasl.jaas.config",
                    "org.apache.kafka.common.security.scram.ScramLoginModule required "
                    + "username=\"" + config.getSaslUsername() + "\" "
                    + "password=\"" + password + "\";");
            }
            case IAM -> {
                p.put("security.protocol", "SASL_SSL");
                p.put("sasl.mechanism", "AWS_MSK_IAM");
                p.put("sasl.jaas.config",
                    "software.amazon.msk.auth.iam.IAMLoginModule required;");
                p.put("sasl.client.callback.handler.class",
                    "software.amazon.msk.auth.iam.IAMClientCallbackHandler");
            }
        }
        return p;
    }

    // ── Record mapper ────────────────────────────────────────────────────────

    private KafkaMessageRecord mapRecord(ConsumerRecord<String, byte[]> r) {
        String value = null;
        String valueType = "BINARY";
        if (r.value() != null) {
            try {
                value = new String(r.value(), StandardCharsets.UTF_8);
                valueType = looksLikeJson(value) ? "JSON" : "TEXT";
            } catch (Exception e) {
                value = Base64.getEncoder().encodeToString(r.value());
                valueType = "BINARY";
            }
        }

        Map<String, String> headers = new LinkedHashMap<>();
        for (Header h : r.headers()) {
            headers.put(h.key(), h.value() != null
                ? new String(h.value(), StandardCharsets.UTF_8) : null);
        }

        return KafkaMessageRecord.builder()
            .partition(r.partition())
            .offset(r.offset())
            .timestamp(r.timestamp() > 0
                ? TS_FMT.format(Instant.ofEpochMilli(r.timestamp())) : null)
            .key(r.key())
            .value(value)
            .valueType(valueType)
            .valueSize(r.value() != null ? r.value().length : 0)
            .headers(headers.isEmpty() ? null : headers)
            .build();
    }

    private boolean looksLikeJson(String s) {
        if (s == null) return false;
        String t = s.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }

    // ── IAM credential injection ─────────────────────────────────────────────

    private <T> T withCredentials(MskConfigEntity config, ThrowingSupplier<T> supplier) {
        boolean hasExplicitCreds = config.getAuthType() == AuthType.IAM
            && config.getEncryptedAccessKey() != null
            && !config.getEncryptedAccessKey().isBlank();

        if (!hasExplicitCreds) {
            return supplier.getUnchecked();
        }

        IAM_LOCK.lock();
        try {
            String prevAccessKey = System.getProperty("aws.accessKeyId");
            String prevSecretKey = System.getProperty("aws.secretAccessKey");
            System.setProperty("aws.accessKeyId",
                encryptionService.decrypt(config.getEncryptedAccessKey()));
            System.setProperty("aws.secretAccessKey",
                encryptionService.decrypt(config.getEncryptedSecretKey()));
            try {
                return supplier.getUnchecked();
            } finally {
                restoreProperty("aws.accessKeyId", prevAccessKey);
                restoreProperty("aws.secretAccessKey", prevSecretKey);
            }
        } finally {
            IAM_LOCK.unlock();
        }
    }

    private void restoreProperty(String key, String prev) {
        if (prev == null) System.clearProperty(key);
        else System.setProperty(key, prev);
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Exception;

        default T getUnchecked() {
            try { return get(); }
            catch (RuntimeException e) { throw e; }
            catch (Exception e) { throw new RuntimeException(e.getMessage(), e); }
        }
    }
}
