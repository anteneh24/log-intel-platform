package com.platform.persistence.repo;

import com.platform.persistence.entity.AgentExecutionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AgentExecutionRepository extends JpaRepository<AgentExecutionEntity, UUID> {}
