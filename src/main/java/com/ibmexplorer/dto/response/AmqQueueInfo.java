package com.ibmexplorer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AmqQueueInfo {
    private String name;
    private Long queueSize;
    private Integer consumerCount;
    private Integer producerCount;
    private Long enqueueCount;
    private Long dequeueCount;
}
