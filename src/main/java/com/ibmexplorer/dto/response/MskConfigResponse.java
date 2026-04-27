package com.ibmexplorer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibmexplorer.entity.MskConfigEntity.AuthType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MskConfigResponse {

    private Long id;
    private String configName;
    private String bootstrapServers;
    private String awsRegion;
    private AuthType authType;
    private String saslUsername;
    private boolean hasSaslPassword;
    private boolean hasIamCredentials;
    private boolean enabled;
    private LocalDateTime createdAt;
    private String createdBy;
}
