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
public class MessageSummaryResponse {

    private int index;
    private String messageId;
    private String correlationId;
    private LocalDateTime putTimestamp;
    private String putApplicationName;
    private int messageType;
    private int expiry;
    private int priority;
    private int persistence;
    private int encoding;
    private String format;
    private int dataLength;
}
