package org.openphc.cce.scheduler.domain.repository;

import org.openphc.cce.scheduler.domain.model.TransitionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TransitionLogRepository extends JpaRepository<TransitionLog, UUID> {
}
