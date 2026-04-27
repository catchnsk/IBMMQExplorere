package com.ibmexplorer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KafkaMessageRecord {

    private int partition;
    private long offset;
    private String timestamp;
    private String key;
    private String value;
    private String valueType;   // TEXT, JSON, BINARY
    private int valueSize;
    private Map<String, String> headers;
}
