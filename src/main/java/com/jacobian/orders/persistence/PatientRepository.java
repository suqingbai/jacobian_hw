package com.jacobian.orders.persistence;

import org.springframework.data.repository.Repository;

public interface PatientRepository extends Repository<PatientEntity, PatientId> {

  /** Runs under row-level security, so it only sees the bound tenant's patients. */
  boolean existsById(PatientId id);
}
