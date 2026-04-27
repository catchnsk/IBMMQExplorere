package com.ibmexplorer.repository;

import com.ibmexplorer.entity.CoherenceServerEntity;
import com.ibmexplorer.entity.CoherenceServerEntity.Environment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CoherenceServerRepository extends JpaRepository<CoherenceServerEntity, Long> {

    List<CoherenceServerEntity> findByEnabledTrueOrderByEnvironmentAscServerTypeAscDisplayNameAsc();

    List<CoherenceServerEntity> findByEnvironmentAndEnabledTrueOrderByServerTypeAscDisplayNameAsc(Environment environment);
}
