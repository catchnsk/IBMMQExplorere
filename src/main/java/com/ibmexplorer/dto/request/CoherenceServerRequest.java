package com.ibmexplorer.dto.request;

import com.ibmexplorer.entity.CoherenceServerEntity.Environment;
import com.ibmexplorer.entity.CoherenceServerEntity.ServerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CoherenceServerRequest {

    @NotBlank
    private String displayName;

    @NotBlank
    private String host;

    private Integer sshPort;

    @NotBlank
    private String username;

    private String password;

    @NotNull
    private Environment environment;

    @NotNull
    private ServerType serverType;

    private String serviceName;
}
