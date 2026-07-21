package com.hz.crm.activity.repository;

import com.hz.crm.activity.domain.WorkflowInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstanceEntity, Long> {
}
