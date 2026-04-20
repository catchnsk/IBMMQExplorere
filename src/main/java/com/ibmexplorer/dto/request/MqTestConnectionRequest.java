package com.ibmexplorer.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MqTestConnectionRequest {

    @NotBlank(message = "Host is required")
    private String host;

    @NotNull(message = "Port is required")
    @Min(1) @Max(65535)
    private Integer port;

    @NotBlank(message = "Queue Manager name is required")
    private String queueManagerName;

    @NotBlank(message = "Channel name is required")
    private String channel;

    private String username;
    private String password;
    private String sslCipherSpec;
    private String keystorePath;
    private String keystorePassword;
    private String truststorePath;

    @Builder.Default
    private Boolean sslEnabled = false;
}
