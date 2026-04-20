package com.ibmexplorer.service;

import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.ibmexplorer.dto.response.QueueInfoResponse;
import com.ibmexplorer.entity.AuditLogEntity.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqQueueService {

    private final MqConnectionService connectionService;
    private final AuditLogService auditLogService;

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
            throw new com.ibmexplorer.exception.MqConnectionException(e.getReason(),
                "Failed to list queues via PCF. Check that the SYSTEM.ADMIN.SVRCONN channel is available. MQRC: " + e.getReason());
        } catch (Exception e) {
            log.error("Unexpected error listing queues", e);
            if (e instanceof com.ibmexplorer.exception.MqConnectionException mce) throw mce;
            throw new RuntimeException("Failed to list queues: " + e.getMessage(), e);
        } finally {
            if (agent != null) {
                try { agent.disconnect(); } catch (Exception ignored) {}
            }
        }
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
