package com.ibmexplorer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageDetailResponse {

    // MQMD fields
    private String messageId;
    private String correlationId;
    private String format;
    private int encoding;
    private int codedCharacterSetId;
    private int messageType;
    private int expiry;
    private int priority;
    private int persistence;
    private String replyToQueue;
    private String replyToQueueManager;
    private String putApplicationName;
    private LocalDateTime putDateTime;
    private String userId;

    // Body views
    private int rawBodySize;
    private String contentType;
    private String textView;
    private String jsonView;
    private String xmlView;
    private String hexView;
}
