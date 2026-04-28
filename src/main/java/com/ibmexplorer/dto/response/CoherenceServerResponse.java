package com.ibmexplorer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibmexplorer.entity.CoherenceServerEntity.Environment;
import com.ibmexplorer.entity.CoherenceServerEntity.ServerType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CoherenceServerResponse {

    private Long id;
    private String displayName;
    private String host;
    private Integer sshPort;
    private String username;
    private boolean hasPassword;
    private Environment environment;
    private ServerType serverType;
    private String scriptBasePath;
    private String scriptInstance;
    private String scriptDir;   // computed: {scriptBasePath}/{scriptInstance}/bin
    private boolean enabled;
    private LocalDateTime createdAt;
    private String createdBy;
}
