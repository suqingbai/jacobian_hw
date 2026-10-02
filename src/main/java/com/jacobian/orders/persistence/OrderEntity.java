package com.jacobian.orders.persistence;

import com.jacobian.orders.domain.OrderType;
import com.jacobian.orders.domain.Priority;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.ListIndexBase;

/**
 * An accepted order (table {@code order_service.orders}). New orders are inserted by {@link
 * OrderWriter} with {@code INSERT ... ON CONFLICT DO NOTHING}; this mapping serves reads.
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY) // DEFAULT uuidv7()
  private UUID id;

  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  private String externalOrderId;
  private byte[] requestHash;
  private String patientId;
  private OrderType orderType;
  private Priority priority;
  private String status;

  @Embedded
  @AttributeOverride(name = "id", column = @Column(name = "submitted_by_user_id"))
  @AttributeOverride(name = "name", column = @Column(name = "submitted_by_display_name"))
  private Party submittedBy;

  private String notes;
  private Instant submittedAt;

  @Column(insertable = false, updatable = false)
  private Instant createdAt;

  @Column(insertable = false, updatable = false)
  private Instant updatedAt;

  @Version private Long version;

  @ElementCollection
  @CollectionTable(
      name = "order_items",
      joinColumns = {
        @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id"),
        @JoinColumn(name = "order_id", referencedColumnName = "id")
      })
  @OrderColumn(name = "line_no")
  @ListIndexBase(1)
  @BatchSize(size = OrderPageQuery.MAX_LIMIT)
  private List<OrderItem> items = new ArrayList<>();

  protected OrderEntity() {}

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getExternalOrderId() {
    return externalOrderId;
  }

  public byte[] getRequestHash() {
    return requestHash.clone();
  }

  public String getPatientId() {
    return patientId;
  }

  public OrderType getOrderType() {
    return orderType;
  }

  public Priority getPriority() {
    return priority;
  }

  public String getStatus() {
    return status;
  }

  public Party getSubmittedBy() {
    return submittedBy;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Long getVersion() {
    return version;
  }

  public List<OrderItem> getItems() {
    return List.copyOf(items);
  }
}
