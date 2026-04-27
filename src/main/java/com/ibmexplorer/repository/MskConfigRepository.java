package com.ibmexplorer.repository;

import com.ibmexplorer.entity.MskConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MskConfigRepository extends JpaRepository<MskConfigEntity, Long> {

    List<MskConfigEntity> findByEnabledTrueOrderByConfigNameAsc();

    Optional<MskConfigEntity> findByConfigName(String configName);
}
