package com.ibmexplorer.dto.request;

import com.ibmexplorer.entity.AmqServerEntity.BrokerType;
import com.ibmexplorer.entity.AmqServerEntity.Environment;
import com.ibmexplorer.entity.AmqServerEntity.GroupCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AmqServerRequest {

    @NotBlank
    private String displayName;

    @NotBlank
    private String host;

    private Integer managementPort;

    /** Console HTTP basic-auth username */
    private String username;
    private String password;

    // ── SSH ───────────────────────────────────────────────────────────────────

    private Integer sshPort;
    private String sshUsername;
    private String sshPassword;

    // ── Script path ───────────────────────────────────────────────────────────

    /** OS owner of the AMQ instance (used in path: /apps/amq/instances/{instanceUser}/...) */
    private String instanceUser;

    /** AMQ instance name (used in path: .../instances/{instanceUser}/{instanceName}/bin) */
    private String instanceName;

    // ── Other ─────────────────────────────────────────────────────────────────

    @NotNull
    private Environment environment;

    @NotNull
    private GroupCategory groupCategory;

    private BrokerType brokerType;
    private Boolean useSsl;
}
