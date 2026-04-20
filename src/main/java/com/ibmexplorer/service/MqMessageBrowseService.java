package com.ibmexplorer.service;

import com.ibm.mq.MQException;
import com.ibm.mq.MQGetMessageOptions;
import com.ibm.mq.MQMessage;
import com.ibm.mq.MQQueue;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.MQConstants;
import com.ibmexplorer.dto.request.BrowseFilter;
import com.ibmexplorer.dto.response.MessageDetailResponse;
import com.ibmexplorer.dto.response.MessageSummaryResponse;
import com.ibmexplorer.entity.AuditLogEntity.AuditAction;
import com.ibmexplorer.exception.MqConnectionException;
import com.ibmexplorer.exception.MqQueueNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MqMessageBrowseService {

    private final MqConnectionService connectionService;
    private final MqMessageParserService parserService;
    private final AuditLogService auditLogService;

    @Value("${app.mq.browse-max-messages:500}")
    private int maxMessages;

    public List<MessageSummaryResponse> browseMessages(Long configId, String sessionId,
                                                        String queueName, BrowseFilter filter,
                                                        String username, String clientIp) {
        MQQueueManager qm = connectionService.getConnection(configId, sessionId);
        MQQueue queue = null;
        try {
            int openOptions = MQConstants.MQOO_BROWSE | MQConstants.MQOO_FAIL_IF_QUIESCING;
            queue = qm.accessQueue(queueName, openOptions);

            List<MessageSummaryResponse> messages = new ArrayList<>();
            MQGetMessageOptions gmo = new MQGetMessageOptions();
            gmo.options = MQConstants.MQGMO_BROWSE_FIRST
                        | MQConstants.MQGMO_NO_WAIT
                        | MQConstants.MQGMO_FAIL_IF_QUIESCING;

            int limit = Math.min(filter.getLimit(), maxMessages);
            boolean first = true;

            while (messages.size() < limit) {
                MQMessage message = new MQMessage();

                // Apply ID filters if requested
                if (filter.getCorrelationId() != null && !filter.getCorrelationId().isBlank()) {
                    message.correlationId = HexFormat.of().parseHex(
                        padHex(filter.getCorrelationId(), 48));
                    gmo.matchOptions = MQConstants.MQMO_MATCH_CORREL_ID;
                }
                if (filter.getMessageId() != null && !filter.getMessageId().isBlank()) {
                    message.messageId = HexFormat.of().parseHex(
                        padHex(filter.getMessageId(), 48));
                    gmo.matchOptions = (gmo.matchOptions | MQConstants.MQMO_MATCH_MSG_ID);
                }

                try {
                    queue.get(message, gmo);
                    messages.add(mapToSummary(message, messages.size()));

                    if (first) {
                        gmo.options = (gmo.options & ~MQConstants.MQGMO_BROWSE_FIRST)
                                    | MQConstants.MQGMO_BROWSE_NEXT;
                        first = false;
                    }
                } catch (MQException e) {
                    if (e.getReason() == MQConstants.MQRC_NO_MSG_AVAILABLE) {
                        break; // Normal end of queue
                    }
                    if (e.getReason() == MQConstants.MQRC_UNKNOWN_OBJECT_NAME) {
                        throw new MqQueueNotFoundException(queueName);
                    }
                    throw new MqConnectionException(e.getReason(),
                        "Error browsing queue '" + queueName + "': MQRC " + e.getReason());
                }
            }

            auditLogService.log(username, AuditAction.BROWSE_MESSAGES, null, queueName,
                "SUCCESS", "Browsed " + messages.size() + " messages", clientIp);
            return messages;

        } catch (MQException e) {
            if (e.getReason() == MQConstants.MQRC_UNKNOWN_OBJECT_NAME) {
                throw new MqQueueNotFoundException(queueName);
            }
            throw new MqConnectionException(e.getReason(),
                "Cannot open queue '" + queueName + "': MQRC " + e.getReason());
        } finally {
            if (queue != null) {
                try { queue.close(); } catch (MQException ignored) {}
            }
        }
    }

    public MessageDetailResponse browseMessageById(Long configId, String sessionId,
                                                    String queueName, String messageId,
                                                    String username, String clientIp) {
        MQQueueManager qm = connectionService.getConnection(configId, sessionId);
        MQQueue queue = null;
        try {
            int openOptions = MQConstants.MQOO_BROWSE | MQConstants.MQOO_FAIL_IF_QUIESCING;
            queue = qm.accessQueue(queueName, openOptions);

            MQMessage message = new MQMessage();
            message.messageId = HexFormat.of().parseHex(padHex(messageId, 48));

            MQGetMessageOptions gmo = new MQGetMessageOptions();
            gmo.options = MQConstants.MQGMO_BROWSE_FIRST | MQConstants.MQGMO_NO_WAIT;
            gmo.matchOptions = MQConstants.MQMO_MATCH_MSG_ID;

            try {
                queue.get(message, gmo);
            } catch (MQException e) {
                if (e.getReason() == MQConstants.MQRC_NO_MSG_AVAILABLE) {
                    throw new MqConnectionException(0, "Message not found: " + messageId);
                }
                throw e;
            }

            int length = message.getDataLength();
            byte[] body = new byte[length];
            message.readFully(body);

            auditLogService.log(username, AuditAction.VIEW_MESSAGE, null,
                queueName + "/" + messageId, "SUCCESS", null, clientIp);
            return parserService.parseMessage(message, body);

        } catch (MQException e) {
            throw new MqConnectionException(e.getReason(),
                "Error reading message from queue '" + queueName + "': MQRC " + e.getReason());
        } catch (IOException e) {
            throw new RuntimeException("Error reading message body", e);
        } finally {
            if (queue != null) {
                try { queue.close(); } catch (MQException ignored) {}
            }
        }
    }

    private MessageSummaryResponse mapToSummary(MQMessage msg, int index) {
        try {
            LocalDateTime putTime = null;
            if (msg.putDateTime != null) {
                putTime = msg.putDateTime.getTime().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();
            }
            return MessageSummaryResponse.builder()
                .index(index)
                .messageId(HexFormat.of().formatHex(msg.messageId))
                .correlationId(HexFormat.of().formatHex(msg.correlationId))
                .putTimestamp(putTime)
                .putApplicationName(msg.putApplicationName != null ? msg.putApplicationName.trim() : "")
                .messageType(msg.messageType)
                .expiry(msg.expiry)
                .priority(msg.priority)
                .persistence(msg.persistence)
                .encoding(msg.encoding)
                .format(msg.format != null ? msg.format.trim() : "")
                .dataLength(msg.getDataLength())
                .build();
        } catch (Exception e) {
            log.warn("Error mapping message summary at index {}", index, e);
            return MessageSummaryResponse.builder().index(index).build();
        }
    }

    private String padHex(String hex, int targetLength) {
        // Remove any spaces or 0x prefix
        hex = hex.replaceAll("\\s+", "").replaceAll("^0x", "");
        if (hex.length() < targetLength) {
            return hex + "0".repeat(targetLength - hex.length());
        }
        return hex.substring(0, targetLength);
    }
}
