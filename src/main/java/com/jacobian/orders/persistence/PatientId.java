package com.jacobian.orders.persistence;

import java.io.Serializable;
import java.util.UUID;

/** Primary key of {@link PatientEntity}: a patient id is unique only within its tenant. */
public record PatientId(UUID tenantId, String id) implements Serializable {}
