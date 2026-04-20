package com.ibmexplorer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MqConnectionResponse {

    private Long id;
    private String configName;
    private String host;
    private Integer port;
    private String queueManagerName;
    private String channel;
    private String username;
    private Boolean hasPassword;
    private String sslCipherSpec;
    private String keystorePath;
    private String truststorePath;
    private Boolean sslEnabled;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
}
