package com.jacobian.orders.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Reference data maintained by the patient system; read-only here. All columns are PHI. */
@Entity
@Immutable
@Table(name = "patients")
@IdClass(PatientId.class)
public class PatientEntity {

  @Id private UUID tenantId;
  @Id private String id;
  private String firstName;
  private String lastName;

  protected PatientEntity() {}

  public UUID getTenantId() {
    return tenantId;
  }

  public String getId() {
    return id;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }
}
