package com.ibmexplorer.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ibmexplorer.entity.AmqServerEntity.BrokerType;
import com.ibmexplorer.entity.AmqServerEntity.Environment;
import com.ibmexplorer.entity.AmqServerEntity.GroupCategory;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AmqServerResponse {

    private Long id;
    private String displayName;
    private String host;
    private Integer managementPort;

    // Console credentials
    private String username;
    private boolean hasPassword;

    // SSH
    private Integer sshPort;
    private String sshUsername;
    private boolean hasSshPassword;

    // Script path
    private String instanceUser;
    private String instanceName;
    private String binDir;   // computed: /apps/amq/instances/{instanceUser}/{instanceName}/bin

    // Other
    private Environment environment;
    private GroupCategory groupCategory;
    private BrokerType brokerType;
    private boolean useSsl;
    private boolean enabled;
    private LocalDateTime createdAt;
    private String createdBy;
}
