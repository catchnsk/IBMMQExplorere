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
public class MqConnectionRequest {

    @NotBlank(message = "Configuration name is required")
    @Size(max = 100, message = "Configuration name must not exceed 100 characters")
    private String configName;

    @NotBlank(message = "Host is required")
    @Size(max = 255, message = "Host must not exceed 255 characters")
    private String host;

    @NotNull(message = "Port is required")
    @Min(value = 1, message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    private Integer port;

    @NotBlank(message = "Queue Manager name is required")
    @Size(max = 100, message = "Queue Manager name must not exceed 100 characters")
    private String queueManagerName;

    @NotBlank(message = "Channel name is required")
    @Size(max = 100, message = "Channel name must not exceed 100 characters")
    private String channel;

    @Size(max = 100)
    private String username;

    private String password;

    private String keystorePassword;

    @Size(max = 255)
    private String sslCipherSpec;

    @Size(max = 512)
    private String keystorePath;

    @Size(max = 512)
    private String truststorePath;

    @Builder.Default
    private Boolean sslEnabled = false;
}
