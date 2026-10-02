package com.jacobian.orders.persistence;

import jakarta.persistence.Embeddable;

/** Snapshot of the person who submitted an order. */
@Embeddable
public record Party(String id, String name) {}
