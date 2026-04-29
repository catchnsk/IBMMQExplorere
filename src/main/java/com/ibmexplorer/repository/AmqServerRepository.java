package com.ibmexplorer.repository;

import com.ibmexplorer.entity.AmqServerEntity;
import com.ibmexplorer.entity.AmqServerEntity.Environment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AmqServerRepository extends JpaRepository<AmqServerEntity, Long> {

    List<AmqServerEntity> findByEnabledTrueOrderByEnvironmentAscGroupCategoryAscDisplayNameAsc();

    List<AmqServerEntity> findByEnvironmentAndEnabledTrueOrderByGroupCategoryAscDisplayNameAsc(Environment environment);
}
