package com.ibmexplorer.repository;

import com.ibmexplorer.entity.MqConfigurationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MqConfigurationRepository extends JpaRepository<MqConfigurationEntity, Long> {

    List<MqConfigurationEntity> findByEnabledTrue();

    Optional<MqConfigurationEntity> findByConfigName(String configName);

    boolean existsByConfigName(String configName);
}
