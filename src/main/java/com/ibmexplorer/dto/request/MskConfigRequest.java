package com.ibmexplorer.dto.request;

import com.ibmexplorer.entity.MskConfigEntity.AuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MskConfigRequest {

    @NotBlank
    private String configName;

    @NotBlank
    private String bootstrapServers;

    private String awsRegion;

    @NotNull
    private AuthType authType;

    // SASL_SCRAM fields
    private String saslUsername;
    private String saslPassword;

    // IAM fields (optional — env/instance profile used when blank)
    private String accessKey;
    private String secretKey;
    private String sessionToken;  // for temporary credentials (STS / SSO / assumed role)
}
