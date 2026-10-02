package com.jacobian.orders.persistence;

import jakarta.persistence.Embeddable;

/** One order line. {@code order_items.id} is not mapped: {@code DEFAULT uuidv7()} fills it. */
@Embeddable
public record OrderItem(String code, String description, int quantity) {}
