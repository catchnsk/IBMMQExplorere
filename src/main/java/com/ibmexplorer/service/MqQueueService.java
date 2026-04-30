package com.ibmexplorer.service;

import com.ibm.mq.MQException;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.ibmexplorer.dto.response.QueueInfoResponse;
import com.ibmexplorer.entity.AuditLogEntity.AuditAction;
import com.ibmexplorer.repository.MqConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqQueueService {

    private final MqConnectionService connectionService;
    private final AuditLogService auditLogService;
    private final MqConfigurationRepository configRepo;

    public List<QueueInfoResponse> listQueues(Long configId, String sessionId,
                                               boolean includeSystemQueues,
                                               String username, String clientIp) {
        MQQueueManager qm = connectionService.getConnection(configId, sessionId);
        PCFMessageAgent agent = null;
        try {
            agent = new PCFMessageAgent(qm);

            PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q);
            request.addParameter(CMQC.MQCA_Q_NAME, "*");
            request.addParameter(CMQC.MQIA_Q_TYPE, CMQC.MQQT_LOCAL);
            request.addParameter(CMQCFC.MQIACF_Q_ATTRS, new int[]{
                CMQC.MQIA_CURRENT_Q_DEPTH,
                CMQC.MQIA_MAX_Q_DEPTH,
                CMQC.MQIA_OPEN_INPUT_COUNT,
                CMQC.MQIA_OPEN_OUTPUT_COUNT,
                CMQC.MQCA_Q_DESC,
                CMQC.MQIA_Q_TYPE,
                CMQC.MQIA_INHIBIT_GET,
                CMQC.MQIA_INHIBIT_PUT
            });

            PCFMessage[] responses = agent.send(request);
            List<QueueInfoResponse> queues = new ArrayList<>();

            for (PCFMessage response : responses) {
                if (response.getCompCode() != CMQC.MQCC_OK) continue;
                QueueInfoResponse info = mapToQueueInfo(response);
                if (info == null) continue;
                if (!includeSystemQueues &&
                    (info.getName().startsWith("SYSTEM.") || info.getName().startsWith("AMQ."))) {
                    continue;
                }
                queues.add(info);
            }

            queues.sort(Comparator.comparing(QueueInfoResponse::getName));
            auditLogService.log(username, AuditAction.VIEW_QUEUES, null,
                "Listed " + queues.size() + " queues", "SUCCESS", null, clientIp);
            return queues;

        } catch (PCFException e) {
            log.error("PCF error listing queues: reason={}", e.getReason(), e);
            if (e.getReason() == 2035) {
                List<String> monitored = getMonitoredQueueNames(configId);
                if (!monitored.isEmpty()) {
                    return fallbackDirect(qm, monitored, includeSystemQueues, configId, username, clientIp);
                }
                throw new com.ibmexplorer.exception.MqAuthorizationException(pcfAuthMessage());
            }
            throw new com.ibmexplorer.exception.MqConnectionException(e.getReason(),
                "PCF command failed (MQRC " + e.getReason() + ").");
        } catch (Exception e) {
            log.error("Unexpected error listing queues", e);
            if (e instanceof com.ibmexplorer.exception.MqConnectionException mce) throw mce;
            if (e instanceof com.ibmexplorer.exception.MqAuthorizationException mae) throw mae;
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof MQException mqe && mqe.getReason() == 2035) {
                    List<String> monitored = getMonitoredQueueNames(configId);
                    if (!monitored.isEmpty()) {
                        return fallbackDirect(qm, monitored, includeSystemQueues, configId, username, clientIp);
                    }
                    throw new com.ibmexplorer.exception.MqAuthorizationException(pcfAuthMessage());
                }
                cause = cause.getCause();
            }
            throw new RuntimeException("Failed to list queues: " + e.getMessage(), e);
        } finally {
            if (agent != null) {
                try { agent.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    private List<QueueInfoResponse> fallbackDirect(MQQueueManager qm, List<String> queueNames,
                                                    boolean includeSystemQueues,
                                                    Long configId, String username, String clientIp) {
        log.info("PCF not authorized — falling back to direct MQINQ for {} configured queues", queueNames.size());
        List<QueueInfoResponse> queues = listQueuesDirect(qm, queueNames, includeSystemQueues);
        queues.sort(Comparator.comparing(QueueInfoResponse::getName));
        auditLogService.log(username, AuditAction.VIEW_QUEUES, null,
            "Listed " + queues.size() + " queues (direct mode)", "SUCCESS", null, clientIp);
        return queues;
    }

    private List<QueueInfoResponse> listQueuesDirect(MQQueueManager qm, List<String> queueNames,
                                                      boolean includeSystemQueues) {
        List<QueueInfoResponse> result = new ArrayList<>();
        for (String rawName : queueNames) {
            String name = rawName.trim();
            if (name.isBlank()) continue;
            if (!includeSystemQueues && (name.startsWith("SYSTEM.") || name.startsWith("AMQ."))) continue;

            MQQueue queue = null;
            try {
                queue = qm.accessQueue(name, CMQC.MQOO_INQUIRE);

                // Selectors: integer attrs first (MQIA_*), then char attrs (MQCA_*)
                int[] selectors = {
                    CMQC.MQIA_CURRENT_Q_DEPTH,   // intAttrs[0]
                    CMQC.MQIA_MAX_Q_DEPTH,        // intAttrs[1]
                    CMQC.MQIA_OPEN_INPUT_COUNT,   // intAttrs[2]
                    CMQC.MQIA_OPEN_OUTPUT_COUNT,  // intAttrs[3]
                    CMQC.MQIA_Q_TYPE,             // intAttrs[4]
                    CMQC.MQIA_INHIBIT_GET,        // intAttrs[5]
                    CMQC.MQIA_INHIBIT_PUT,        // intAttrs[6]
                    CMQC.MQCA_Q_DESC              // charAttrs[0]
                };
                int[] intAttrs  = new int[7];
                char[] charAttrs = new char[64]; // MQCA_Q_DESC is 64 chars
                queue.inquire(selectors, intAttrs, charAttrs);

                result.add(QueueInfoResponse.builder()
                    .name(name)
                    .type(mapQueueType(intAttrs[4]))
                    .currentDepth(intAttrs[0])
                    .maxDepth(intAttrs[1])
                    .openInputCount(intAttrs[2])
                    .openOutputCount(intAttrs[3])
                    .description(new String(charAttrs).trim())
                    .getInhibited(intAttrs[5] == CMQC.MQQA_GET_INHIBITED)
                    .putInhibited(intAttrs[6] == CMQC.MQQA_PUT_INHIBITED)
                    .build());

            } catch (MQException e) {
                log.warn("Cannot inquire queue '{}': reason={}", name, e.getReason());
            } finally {
                if (queue != null) {
                    try { queue.close(); } catch (Exception ignored) {}
                }
            }
        }
        return result;
    }

    private List<String> getMonitoredQueueNames(Long configId) {
        return configRepo.findById(configId)
            .map(c -> c.getMonitoredQueueNames())
            .filter(s -> s != null && !s.isBlank())
            .map(s -> Arrays.asList(s.split("[,\n]+")))
            .orElse(List.of())
            .stream()
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .collect(Collectors.toList());
    }

    private static String pcfAuthMessage() {
        return "The connected user does not have authority to issue PCF commands (MQRC 2035). "
            + "An MQ administrator must grant the following on the queue manager:\n"
            + "  setmqaut -m <QM> -t qmgr   -p <user> +connect +inq\n"
            + "  setmqaut -m <QM> -t queue  -n SYSTEM.ADMIN.COMMAND.QUEUE -p <user> +put\n"
            + "  setmqaut -m <QM> -t queue  -n SYSTEM.DEFAULT.MODEL.QUEUE -p <user> +get +browse\n"
            + "Alternatively, add the user to the 'mqm' group (full admin access).\n"
            + "Or configure Monitored Queue Names in the connection settings to use direct read-only access.";
    }

    private QueueInfoResponse mapToQueueInfo(PCFMessage response) {
        try {
            String name = response.getStringParameterValue(CMQC.MQCA_Q_NAME).trim();
            if (name.isBlank()) return null;

            int inhibitGet = safeGetInt(response, CMQC.MQIA_INHIBIT_GET, CMQC.MQQA_GET_ALLOWED);
            int inhibitPut = safeGetInt(response, CMQC.MQIA_INHIBIT_PUT, CMQC.MQQA_PUT_ALLOWED);

            return QueueInfoResponse.builder()
                .name(name)
                .type(mapQueueType(safeGetInt(response, CMQC.MQIA_Q_TYPE, CMQC.MQQT_LOCAL)))
                .currentDepth(safeGetInt(response, CMQC.MQIA_CURRENT_Q_DEPTH, 0))
                .maxDepth(safeGetInt(response, CMQC.MQIA_MAX_Q_DEPTH, 0))
                .openInputCount(safeGetInt(response, CMQC.MQIA_OPEN_INPUT_COUNT, 0))
                .openOutputCount(safeGetInt(response, CMQC.MQIA_OPEN_OUTPUT_COUNT, 0))
                .description(safeGetString(response, CMQC.MQCA_Q_DESC))
                .getInhibited(inhibitGet == CMQC.MQQA_GET_INHIBITED)
                .putInhibited(inhibitPut == CMQC.MQQA_PUT_INHIBITED)
                .build();
        } catch (Exception e) {
            log.warn("Failed to map queue info from PCF response", e);
            return null;
        }
    }

    private int safeGetInt(PCFMessage response, int parameter, int defaultValue) {
        try {
            return response.getIntParameterValue(parameter);
        } catch (PCFException e) {
            return defaultValue;
        }
    }

    private String safeGetString(PCFMessage response, int parameter) {
        try {
            String value = response.getStringParameterValue(parameter);
            return value != null ? value.trim() : "";
        } catch (PCFException e) {
            return "";
        }
    }

    private String mapQueueType(int type) {
        return switch (type) {
            case CMQC.MQQT_LOCAL  -> "LOCAL";
            case CMQC.MQQT_ALIAS  -> "ALIAS";
            case CMQC.MQQT_REMOTE -> "REMOTE";
            case CMQC.MQQT_MODEL  -> "MODEL";
            default               -> "UNKNOWN";
        };
    }
}
