package com.ibmexplorer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueueInfoResponse {

    private String name;
    private String type;
    private Integer currentDepth;
    private Integer maxDepth;
    private Integer openInputCount;
    private Integer openOutputCount;
    private String description;
    private Boolean getInhibited;
    private Boolean putInhibited;
}
