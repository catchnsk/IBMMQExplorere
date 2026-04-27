package com.ibmexplorer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CoherenceStatusResponse {

    private Long serverId;
    private String host;
    private ServiceStatus status;
    private String details;
    private LocalDateTime checkedAt;

    public enum ServiceStatus {
        RUNNING, STOPPED, UNKNOWN, ERROR
    }
}
