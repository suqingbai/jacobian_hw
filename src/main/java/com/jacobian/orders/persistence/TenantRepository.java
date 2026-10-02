package com.jacobian.orders.persistence;

import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface TenantRepository extends Repository<TenantEntity, UUID> {

  boolean existsById(UUID id);
}
