package com.ibmexplorer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KafkaTopicInfo {

    private String name;
    private int partitions;
    private int replicationFactor;
    private long totalMessages;   // sum of (latest - earliest) across all partitions
    private long latestOffset;    // sum of latest offsets
    private boolean internal;
}
